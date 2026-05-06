package io.b2mash.b2b.b2bstrawman.assistant.invocation;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Integration tests for {@link AiInvocationExpirySweeper}. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiInvocationExpirySweeperIntegrationTest {

  private static final String ORG_ID = "org_sweeper_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private AiInvocationExpirySweeper sweeper;
  @Autowired private AiSpecialistInvocationRepository repository;
  @Autowired private JdbcTemplate jdbc;

  private String tenantSchema;
  private UUID ownerMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Sweeper Test Org", null);
    var ownerStr =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_sweeper_owner",
            "sweeper_owner@test.com",
            "Sweeper Owner",
            "owner");
    ownerMemberId = UUID.fromString(ownerStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  private void runInTenant(Runnable body) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_ASSISTANT_USE", "TEAM_OVERSIGHT"))
        .run(body);
  }

  @Test
  void expiresPendingApprovalOlderThanThreshold() {
    runInTenant(
        () -> {
          // Create a PENDING_APPROVAL invocation
          var inv =
              new AiSpecialistInvocation(
                  "BILLING",
                  InvocationSource.AUTOMATION,
                  ownerMemberId,
                  null,
                  "billing_run",
                  UUID.randomUUID(),
                  "v1.0");
          inv = repository.save(inv);
          inv.markPendingApproval();
          repository.save(inv);

          // Backdate to 15 days ago (past 14-day threshold)
          jdbc.update(
              "UPDATE "
                  + tenantSchema
                  + ".ai_specialist_invocations SET created_at = NOW() - INTERVAL '15 days' WHERE id = ?",
              inv.getId());

          // Run the sweeper
          sweeper.sweep();

          // Verify expired
          var reloaded = repository.findById(inv.getId()).orElseThrow();
          assertThat(reloaded.getStatus()).isEqualTo(InvocationStatus.EXPIRED);
        });
  }

  @Test
  void doesNotExpireRecentPendingApproval() {
    runInTenant(
        () -> {
          // Create a recent PENDING_APPROVAL invocation
          var inv =
              new AiSpecialistInvocation(
                  "BILLING",
                  InvocationSource.AUTOMATION,
                  ownerMemberId,
                  null,
                  "billing_run",
                  UUID.randomUUID(),
                  "v1.0");
          inv = repository.save(inv);
          inv.markPendingApproval();
          repository.save(inv);

          // Don't backdate — it's fresh

          // Run the sweeper
          sweeper.sweep();

          // Should still be PENDING_APPROVAL
          var reloaded = repository.findById(inv.getId()).orElseThrow();
          assertThat(reloaded.getStatus()).isEqualTo(InvocationStatus.PENDING_APPROVAL);
        });
  }

  @Test
  void nullsJsonbOnTerminalRowsOlderThanRetention() {
    runInTenant(
        () -> {
          // Create a FAILED invocation with proposed output
          var inv =
              new AiSpecialistInvocation(
                  "BILLING",
                  InvocationSource.AUTOMATION,
                  ownerMemberId,
                  null,
                  "billing_run",
                  UUID.randomUUID(),
                  "v1.0");
          inv = repository.save(inv);
          inv.markFailed("some error");
          repository.save(inv);

          // Backdate to 366 days ago (past 365-day retention threshold)
          jdbc.update(
              "UPDATE "
                  + tenantSchema
                  + ".ai_specialist_invocations SET created_at = NOW() - INTERVAL '366 days' WHERE id = ?",
              inv.getId());

          // Set some proposed_output JSONB so there's something to null
          jdbc.update(
              "UPDATE "
                  + tenantSchema
                  + ".ai_specialist_invocations SET proposed_output = '{\"kind\":\"BillingPolishPayload\",\"invoiceId\":\"00000000-0000-0000-0000-000000000001\",\"edits\":[]}' WHERE id = ?",
              inv.getId());

          // Run the sweeper
          sweeper.sweep();

          // Verify status preserved but outputs nulled
          var reloaded = repository.findById(inv.getId()).orElseThrow();
          assertThat(reloaded.getStatus()).isEqualTo(InvocationStatus.FAILED);
          assertThat(reloaded.getProposedOutput()).isNull();
          assertThat(reloaded.getAppliedOutput()).isNull();
        });
  }

  @Test
  void preservesStatusAsAuditShadow() {
    runInTenant(
        () -> {
          // Create an EXPIRED invocation (already expired)
          var inv =
              new AiSpecialistInvocation(
                  "INBOX",
                  InvocationSource.AUTOMATION,
                  ownerMemberId,
                  null,
                  "inbox",
                  UUID.randomUUID(),
                  "v1.0");
          inv = repository.save(inv);
          inv.markPendingApproval();
          repository.save(inv);
          // Need to reload to get correct version
          inv = repository.findById(inv.getId()).orElseThrow();
          inv.markExpired();
          repository.save(inv);

          // Backdate past retention
          jdbc.update(
              "UPDATE "
                  + tenantSchema
                  + ".ai_specialist_invocations SET created_at = NOW() - INTERVAL '400 days' WHERE id = ?",
              inv.getId());

          sweeper.sweep();

          var reloaded = repository.findById(inv.getId()).orElseThrow();
          // Status preserved
          assertThat(reloaded.getStatus()).isEqualTo(InvocationStatus.EXPIRED);
        });
  }
}
