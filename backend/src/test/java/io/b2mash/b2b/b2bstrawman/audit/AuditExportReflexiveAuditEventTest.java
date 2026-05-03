package io.b2mash.b2b.b2bstrawman.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.AbstractIntegrationTest;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestConstructor;

/**
 * Verifies the reflexive {@code audit.export.generated} event emitted by the streaming CSV export
 * (Epic 503A task 503.4). Each export run must emit exactly one event whose details capture the
 * effective filter and the streamed row count.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuditExportReflexiveAuditEventTest extends AbstractIntegrationTest {

  private static final String ORG_ID = "org_audit_export_reflexive_test";

  private final AuditService auditService;
  private final TenantProvisioningService provisioningSvc;

  AuditExportReflexiveAuditEventTest(
      AuditService auditService, TenantProvisioningService provisioningSvc) {
    this.auditService = auditService;
    this.provisioningSvc = provisioningSvc;
  }

  private String schemaName;

  @BeforeAll
  void provisionAndSeed() throws Exception {
    schemaName =
        provisioningSvc
            .provisionTenant(ORG_ID, "Audit Export Reflexive Test Org", null)
            .schemaName();
    var liveMemberIdStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_ref_owner", "ref_owner@test.com", "Ref Owner", "owner");
    var liveMemberId = UUID.fromString(liveMemberIdStr);

    // Seed a small, deterministic set of source events so the export has a known row count.
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              for (int i = 0; i < 3; i++) {
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
              }
            });
  }

  private void performExport(String... params) throws Exception {
    var req =
        get("/api/audit-events/export.csv").with(TestJwtFactory.ownerJwt(ORG_ID, "user_ref_owner"));
    for (int i = 0; i + 1 < params.length; i += 2) {
      req = req.param(params[i], params[i + 1]);
    }
    var asyncResult = mockMvc.perform(req).andExpect(request().asyncStarted()).andReturn();
    mockMvc.perform(asyncDispatch(asyncResult)).andExpect(status().isOk());
  }

  @Test
  void exportEmitsExactlyOneReflexiveEventWithCorrectDetails() throws Exception {
    int beforeCount = countExportEvents();

    performExport("entityType", "task");

    var events = findExportEvents();
    assertThat(events).hasSize(beforeCount + 1);

    // Newest first — the most recent event is the one this test just produced.
    var latest = events.get(0);
    var details = latest.getDetails();
    assertThat(details).isNotNull();
    assertThat(details.get("format")).isEqualTo("CSV");
    assertThat(details.get("rowCount")).isNotNull();

    // rowCount lands as a JSONB number — Jackson deserialises to Number; coerce defensively.
    long rowCount = ((Number) details.get("rowCount")).longValue();
    assertThat(rowCount).isGreaterThanOrEqualTo(3);

    @SuppressWarnings("unchecked")
    Map<String, Object> filterMap = (Map<String, Object>) details.get("filter");
    assertThat(filterMap).isNotNull();
    assertThat(filterMap).containsEntry("entityType", "task");

    // Synthetic entityId must be a UUID per the emitter contract.
    assertThat(latest.getEntityType()).isEqualTo("audit_export");
    assertThat(latest.getEntityId()).isNotNull();
  }

  @Test
  void rerunningExportEmitsAdditionalEvent() throws Exception {
    int beforeCount = countExportEvents();

    performExport();
    performExport();

    assertThat(countExportEvents()).isEqualTo(beforeCount + 2);
  }

  private int countExportEvents() {
    return findExportEvents().size();
  }

  private List<AuditEvent> findExportEvents() {
    return ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .call(
            () ->
                auditService
                    .findEvents(
                        new AuditEventFilter(
                            "audit_export", null, null, "audit.export.generated", null, null, null),
                        PageRequest.of(0, 100))
                    .getContent());
  }
}
