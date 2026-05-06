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

/** Integration tests for {@link AiInvocationReaper}. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiInvocationReaperIntegrationTest {

  private static final String ORG_ID = "org_reaper_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private AiInvocationReaper reaper;
  @Autowired private AiSpecialistInvocationRepository repository;
  @Autowired private JdbcTemplate jdbc;

  private String tenantSchema;
  private UUID ownerMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Reaper Test Org", null);
    var ownerStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_reaper_owner", "reaper_owner@test.com", "Reaper Owner", "owner");
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
  void reapsStaleRunningInvocations() {
    runInTenant(
        () -> {
          // Create a RUNNING invocation and backdate it
          var inv =
              new AiSpecialistInvocation(
                  "INBOX",
                  InvocationSource.AUTOMATION,
                  ownerMemberId,
                  null,
                  "project",
                  UUID.randomUUID(),
                  "v1.0");
          inv = repository.save(inv);
          assertThat(inv.getStatus()).isEqualTo(InvocationStatus.RUNNING);

          // Backdate the created_at to make it stale (3 minutes ago)
          jdbc.update(
              "UPDATE "
                  + tenantSchema
                  + ".ai_specialist_invocations SET created_at = NOW() - INTERVAL '3 minutes' WHERE id = ?",
              inv.getId());

          // Run the reaper
          reaper.reapStaleInvocations();

          // Verify the invocation was reaped
          var reloaded = repository.findById(inv.getId()).orElseThrow();
          assertThat(reloaded.getStatus()).isEqualTo(InvocationStatus.FAILED);
          assertThat(reloaded.getErrorMessage()).isEqualTo("REAPED_AFTER_RESTART");
        });
  }

  @Test
  void doesNotAffectNonRunningInvocations() {
    runInTenant(
        () -> {
          // Create a PENDING_APPROVAL invocation and backdate it
          var inv =
              new AiSpecialistInvocation(
                  "INBOX",
                  InvocationSource.AUTOMATION,
                  ownerMemberId,
                  null,
                  "project",
                  UUID.randomUUID(),
                  "v1.0");
          inv = repository.save(inv);
          inv.markPendingApproval();
          repository.save(inv);

          // Backdate
          jdbc.update(
              "UPDATE "
                  + tenantSchema
                  + ".ai_specialist_invocations SET created_at = NOW() - INTERVAL '3 minutes' WHERE id = ?",
              inv.getId());

          // Run the reaper
          reaper.reapStaleInvocations();

          // Should still be PENDING_APPROVAL
          var reloaded = repository.findById(inv.getId()).orElseThrow();
          assertThat(reloaded.getStatus()).isEqualTo(InvocationStatus.PENDING_APPROVAL);
        });
  }

  @Test
  void doesNotReapRecentRunningInvocations() {
    runInTenant(
        () -> {
          // Create a fresh RUNNING invocation (just created, not stale)
          var inv =
              new AiSpecialistInvocation(
                  "INBOX",
                  InvocationSource.AUTOMATION,
                  ownerMemberId,
                  null,
                  "project",
                  UUID.randomUUID(),
                  "v1.0");
          inv = repository.save(inv);

          // Run the reaper
          reaper.reapStaleInvocations();

          // Should still be RUNNING (not stale yet)
          var reloaded = repository.findById(inv.getId()).orElseThrow();
          assertThat(reloaded.getStatus()).isEqualTo(InvocationStatus.RUNNING);
        });
  }
}
