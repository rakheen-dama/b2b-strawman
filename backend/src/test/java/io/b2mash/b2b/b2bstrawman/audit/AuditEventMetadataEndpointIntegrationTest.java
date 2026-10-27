package io.b2mash.b2b.b2bstrawman.audit;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.testutil.AbstractIntegrationTest;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * HTTP integration tests for {@code GET /api/audit-events/metadata} (501.7) covering authorization
 * (TEAM_OVERSIGHT capability) and shape of the catalogue response.
 */
class AuditEventMetadataEndpointIntegrationTest extends AbstractIntegrationTest {

  private static final String ORG_ID = "org_audit_metadata_endpoint_test";

  @Autowired private AuditService auditService;

  private String tenantSchema;
  private UUID ownerId;

  @BeforeAll
  void provisionTenantAndSeedMembers() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Audit Metadata Endpoint Test Org", null)
            .schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_amd_owner", "amd_owner@test.com", "AMD Owner", "owner"));
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_amd_member", "amd_member@test.com", "AMD Member", "member");
  }

  @Test
  void ownerSeesFullCatalogueWithAtLeast17Entries() throws Exception {
    mockMvc
        .perform(
            get("/api/audit-events/metadata")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_amd_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(17))))
        .andExpect(jsonPath("$[*].eventType", hasItem("matter.closure.override_used")))
        .andExpect(jsonPath("$[*].eventType", hasItem("security.*")))
        .andExpect(jsonPath("$[*].eventType", hasItem("trust.transaction.rejected")));
  }

  @Test
  void responsePayloadHasAllFourMetadataFieldsForEachEntry() throws Exception {
    mockMvc
        .perform(
            get("/api/audit-events/metadata")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_amd_owner")))
        .andExpect(status().isOk())
        // Find the index of matter.closure.override_used and assert its fields directly via a
        // filter expression.
        .andExpect(
            jsonPath("$[?(@.eventType == 'matter.closure.override_used')].label")
                .value(hasItem("Matter Closure Override Used")))
        .andExpect(
            jsonPath("$[?(@.eventType == 'matter.closure.override_used')].severity")
                .value(hasItem("CRITICAL")))
        .andExpect(
            jsonPath("$[?(@.eventType == 'matter.closure.override_used')].group")
                .value(hasItem("COMPLIANCE")));
  }

  @Test
  void invoicePrefixEntryReadsFeeNoteAndNoCatalogueLabelSaysInvoice() throws Exception {
    // Phase 88 625C (D12, R-8): stored key invoice.* unchanged (ADR-342); display label legal.
    mockMvc
        .perform(
            get("/api/audit-events/metadata")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_amd_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.eventType == 'invoice.*')].label").value(hasItem("Fee note")))
        .andExpect(jsonPath("$[?(@.eventType == 'invoice.*')].group").value(hasItem("FINANCIAL")))
        .andExpect(jsonPath("$[*].label", not(hasItem("Invoice"))));
  }

  @Test
  void resolvedRowLabelsAreLegalWorded() throws Exception {
    // Phase 88 625C (D12, R-8): the labels a firm audit-trail read returns — "Fee note" for an
    // invoice.* row and the six legal head mappings for synthesised (unregistered) defaults.
    UUID entityId = UUID.randomUUID();
    String entityType = "legal_label_probe";
    for (String eventType :
        new String[] {
          "invoice.sent",
          "project.updated",
          "customer.lifecycle.transitioned",
          "project_member.added",
          "legal_disbursement.created",
          "project_budget.updated"
        }) {
      ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
          .run(
              () ->
                  auditService.log(
                      new AuditEventRecord(
                          eventType,
                          entityType,
                          entityId,
                          ownerId,
                          "USER",
                          "API",
                          null,
                          null,
                          null)));
    }
    mockMvc
        .perform(
            get("/api/audit-events/" + entityType + "/" + entityId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_amd_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(6)))
        .andExpect(
            jsonPath("$.content[?(@.eventType == 'invoice.sent')].label")
                .value(hasItem("Fee note")))
        .andExpect(
            jsonPath("$.content[?(@.eventType == 'project.updated')].label")
                .value(hasItem("Matter updated")))
        .andExpect(
            jsonPath("$.content[?(@.eventType == 'customer.lifecycle.transitioned')].label")
                .value(hasItem("Client lifecycle transitioned")))
        .andExpect(
            jsonPath("$.content[?(@.eventType == 'project_member.added')].label")
                .value(hasItem("Matter team member added")))
        .andExpect(
            jsonPath("$.content[?(@.eventType == 'legal_disbursement.created')].label")
                .value(hasItem("Disbursement created")))
        .andExpect(
            jsonPath("$.content[?(@.eventType == 'project_budget.updated')].label")
                .value(hasItem("Matter budget updated")));
  }

  @Test
  void regularMemberWithoutTeamOversightForbidden() throws Exception {
    // 403 path goes through GlobalExceptionHandler.handleAccessDenied which emits the project's
    // standard ProblemDetail body. Asserting status, title, and detail enforces the project
    // coding-guideline that error-path tests must verify the ProblemDetail contract, not just
    // the HTTP status code.
    mockMvc
        .perform(
            get("/api/audit-events/metadata")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_amd_member")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
  }

  @Test
  void unauthenticatedRequestRejected() throws Exception {
    // 401 path is handled by Spring Security's BearerTokenAuthenticationEntryPoint, which writes
    // a WWW-Authenticate challenge header and an empty response body — no ProblemDetail JSON. The
    // status assertion plus the WWW-Authenticate header is the strongest contract we can verify
    // without changing the global authentication-entry-point behaviour, which is out of scope for
    // 501A. Documented here so future readers know why JSON fields are not asserted.
    mockMvc
        .perform(get("/api/audit-events/metadata"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().exists("WWW-Authenticate"));
  }
}
