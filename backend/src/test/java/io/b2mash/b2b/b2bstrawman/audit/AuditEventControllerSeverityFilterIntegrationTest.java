package io.b2mash.b2b.b2bstrawman.audit;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.in;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.AbstractIntegrationTest;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;

/**
 * HTTP integration tests for the {@code ?severities=} multi-value filter on {@code GET
 * /api/audit-events} (502.7). Pre-flight registry filtering is implemented at the service layer in
 * 502A; this slice merely wires the query param through and surfaces the registry-resolved {@code
 * severity} field on each row.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuditEventControllerSeverityFilterIntegrationTest extends AbstractIntegrationTest {

  private static final String ORG_ID = "org_audit_severity_ctrl_test";

  private final AuditService auditService;
  private final TenantProvisioningService provisioningSvc;

  AuditEventControllerSeverityFilterIntegrationTest(
      AuditService auditService, TenantProvisioningService provisioningSvc) {
    this.auditService = auditService;
    this.provisioningSvc = provisioningSvc;
  }

  private String schemaName;
  private UUID liveMemberId;

  @BeforeAll
  void provisionTenantAndSeedEvents() throws Exception {
    schemaName =
        provisioningSvc
            .provisionTenant(ORG_ID, "Audit Severity Controller Test Org", null)
            .schemaName();
    var liveMemberIdStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_asc_owner", "asc_owner@test.com", "ASC Owner", "owner");
    liveMemberId = UUID.fromString(liveMemberIdStr);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_asc_member", "asc_member@test.com", "ASC Member", "member");

    // Seed three events spanning three severities so the filter has something to discriminate:
    //   matter.closure.override_used   → CRITICAL / COMPLIANCE
    //   security.login.failure         → WARNING  / SECURITY
    //   task.created (unregistered)    → INFO     / STANDARD (default fallback)
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
                      "security.login.failure",
                      "security",
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
                      liveMemberId,
                      "USER",
                      "API",
                      null,
                      null,
                      null));
            });
  }

  @Test
  void severityFilterRestrictsToCriticalAndWarning() throws Exception {
    mockMvc
        .perform(
            get("/api/audit-events")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_asc_owner"))
                .param("severities", "CRITICAL,WARNING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(2))))
        // Every returned row must carry a severity inside the requested set — the INFO-severity
        // task.created row must be filtered out.
        .andExpect(jsonPath("$.content[*].severity", everyItem(in(List.of("CRITICAL", "WARNING")))))
        .andExpect(jsonPath("$.content[*].severity", hasItem("CRITICAL")))
        .andExpect(jsonPath("$.content[*].severity", hasItem("WARNING")));
  }

  @Test
  void emptySeverityParamReturnsAllRows() throws Exception {
    // Spring binds `?severities=` (empty) to either an empty Set or null — both must mean
    // "no severity filter" and therefore the INFO-severity row must come back too.
    mockMvc
        .perform(
            get("/api/audit-events")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_asc_owner"))
                .param("severities", ""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[*].severity", hasItem("INFO")))
        .andExpect(jsonPath("$.content[*].severity", hasItem("CRITICAL")))
        .andExpect(jsonPath("$.content[*].severity", hasItem("WARNING")));
  }

  @Test
  void severityFilterForbiddenForNonOwner() throws Exception {
    mockMvc
        .perform(
            get("/api/audit-events")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_asc_member"))
                .param("severities", "CRITICAL"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
  }
}
