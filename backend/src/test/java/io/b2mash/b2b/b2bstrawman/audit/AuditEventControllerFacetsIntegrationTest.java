package io.b2mash.b2b.b2bstrawman.audit;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.AbstractIntegrationTest;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;

/**
 * HTTP integration tests for the three facet endpoints (502.6) plus their TEAM_OVERSIGHT capability
 * gate and default-range behaviour. Mirrors the structure of {@link AuditEventControllerTest} and
 * seeds events via {@link AuditService#log(AuditEventRecord)} so that registry-resolved fields
 * (severity / group / label) can be asserted deterministically without driving the project/task
 * APIs.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuditEventControllerFacetsIntegrationTest extends AbstractIntegrationTest {

  private static final String ORG_ID = "org_audit_facets_ctrl_test";

  private final AuditService auditService;
  private final TenantProvisioningService provisioningSvc;

  AuditEventControllerFacetsIntegrationTest(
      AuditService auditService, TenantProvisioningService provisioningSvc) {
    this.auditService = auditService;
    this.provisioningSvc = provisioningSvc;
  }

  private String schemaName;
  private UUID liveMemberId;
  private UUID ghostActorId;

  @BeforeAll
  void provisionTenantAndSeedEvents() throws Exception {
    schemaName =
        provisioningSvc
            .provisionTenant(ORG_ID, "Audit Facets Controller Test Org", null)
            .schemaName();
    var liveMemberIdStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_afc_owner", "afc_owner@test.com", "AFC Owner Member", "owner");
    liveMemberId = UUID.fromString(liveMemberIdStr);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_afc_member", "afc_member@test.com", "AFC Plain Member", "member");

    ghostActorId = UUID.randomUUID();

    // Seed a CRITICAL/COMPLIANCE event by the live member, plus an event by an actorId that does
    // NOT match any member row (the LEFT JOIN miss surfaces as the §12.3.4 "Former member" label).
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              auditService.log(
                  new AuditEventRecord(
                      "matter.closure.override_used",
                      "matter",
                      UUID.randomUUID(),
                      liveMemberId,
                      "USER",
                      "API",
                      null,
                      null,
                      null));
              auditService.log(
                  new AuditEventRecord(
                      "task.created",
                      "task",
                      UUID.randomUUID(),
                      ghostActorId,
                      "USER",
                      "API",
                      null,
                      null,
                      null));
            });
  }

  @Test
  void actorFacetsEndpointReturnsLiveMemberAndFormerMemberFallback() throws Exception {
    mockMvc
        .perform(
            get("/api/audit-events/facets/actors")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_afc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
        .andExpect(jsonPath("$[*].actorDisplayName", hasItem("AFC Owner Member")))
        .andExpect(
            jsonPath("$[*].actorDisplayName", hasItem("Former member (" + ghostActorId + ")")));
  }

  @Test
  void eventTypeFacetsEndpointReturnsRegistryEnrichedRows() throws Exception {
    mockMvc
        .perform(
            get("/api/audit-events/facets/event-types")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_afc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[*].eventType", hasItem("matter.closure.override_used")))
        .andExpect(
            jsonPath("$[?(@.eventType == 'matter.closure.override_used')].severity")
                .value(hasItem("CRITICAL")))
        .andExpect(
            jsonPath("$[?(@.eventType == 'matter.closure.override_used')].group")
                .value(hasItem("COMPLIANCE")))
        .andExpect(
            jsonPath("$[?(@.eventType == 'matter.closure.override_used')].label")
                .value(hasItem("Matter Closure Override Used")));
  }

  @Test
  void entityTypeFacetsEndpointReturnsTitleCasedLabels() throws Exception {
    mockMvc
        .perform(
            get("/api/audit-events/facets/entity-types")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_afc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[*].entityType", hasItem("matter")))
        .andExpect(jsonPath("$[*].entityType", hasItem("task")))
        .andExpect(jsonPath("$[?(@.entityType == 'matter')].label").value(hasItem("Matter")))
        .andExpect(jsonPath("$[?(@.entityType == 'task')].label").value(hasItem("Task")));
  }

  @Test
  void defaultRangeAppliedWhenFromAndToOmitted() throws Exception {
    // No `from` / `to` query params — the controller must default to (now - 30d, now) and still
    // return a populated body, not a 400. The seeded events were emitted moments ago, so they
    // belong inside the default window.
    mockMvc
        .perform(
            get("/api/audit-events/facets/event-types")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_afc_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
  }

  @Test
  void actorFacetsForbiddenForNonOwner() throws Exception {
    mockMvc
        .perform(
            get("/api/audit-events/facets/actors")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_afc_member")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
  }

  @Test
  void eventTypeFacetsForbiddenForNonOwner() throws Exception {
    mockMvc
        .perform(
            get("/api/audit-events/facets/event-types")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_afc_member")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
  }

  @Test
  void entityTypeFacetsForbiddenForNonOwner() throws Exception {
    mockMvc
        .perform(
            get("/api/audit-events/facets/entity-types")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_afc_member")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
  }
}
