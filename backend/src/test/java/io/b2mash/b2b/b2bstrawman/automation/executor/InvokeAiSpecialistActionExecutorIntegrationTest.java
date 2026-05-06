package io.b2mash.b2b.b2bstrawman.automation.executor;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.AiSpecialistInvocationRepository;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.AiSpecialistInvocationService;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.InvocationStatus;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.applier.OutputApplier;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.InboxSummaryPayload;
import io.b2mash.b2b.b2bstrawman.assistant.specialist.NonInteractiveSpecialistRunner;
import io.b2mash.b2b.b2bstrawman.automation.VariableResolver;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionFailure;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionSuccess;
import io.b2mash.b2b.b2bstrawman.automation.config.InvokeAiSpecialistActionConfig;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for {@link InvokeAiSpecialistActionExecutor}. Uses a mock
 * NonInteractiveSpecialistRunner since actual LLM calls are not available in tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  InvokeAiSpecialistActionExecutorIntegrationTest.FakeInboxApplierConfig.class
})
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvokeAiSpecialistActionExecutorIntegrationTest {

  private static final String ORG_ID = "org_ai_exec_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private InvokeAiSpecialistActionExecutor executor;
  @Autowired private AiSpecialistInvocationRepository invocationRepository;
  @Autowired private AiSpecialistInvocationService invocationService;
  @Autowired private VariableResolver variableResolver;
  @MockitoBean private NonInteractiveSpecialistRunner runner;
  @Autowired private FakeInboxApplier fakeInboxApplier;

  private String tenantSchema;
  private UUID ownerMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "AI Exec Test Org", null);
    var ownerStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_ai_exec_owner", "ai_exec_owner@test.com", "AI Owner", "owner");
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
  void reviewHappyPath_queuesPendingApproval() {
    var payload =
        new InboxSummaryPayload(
            UUID.randomUUID(),
            "2026-01-01T00:00:00Z",
            "2026-01-02T00:00:00Z",
            "Test summary",
            null);
    Mockito.when(
            runner.run(
                Mockito.anyString(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()))
        .thenReturn(payload);
    Mockito.when(runner.promptVersion(Mockito.anyString())).thenReturn("v1.0");

    runInTenant(
        () -> {
          var config =
              new InvokeAiSpecialistActionConfig(
                  "inbox-za",
                  Map.of("entityType", "inbox", "entityId", UUID.randomUUID().toString()),
                  null,
                  null,
                  "REVIEW",
                  60);
          var context =
              Map.of(
                  "actor", Map.<String, Object>of("id", ownerMemberId.toString()),
                  "event", Map.<String, Object>of("entityId", UUID.randomUUID().toString()));
          var executionId = UUID.randomUUID();

          var result = executor.execute(config, context, executionId);

          assertThat(result.isSuccess()).isTrue();
          var success = (ActionSuccess) result;
          assertThat(success.resultData()).containsKey("invocationId");

          var invId = UUID.fromString((String) success.resultData().get("invocationId"));
          var inv = invocationRepository.findById(invId).orElseThrow();
          assertThat(inv.getStatus()).isEqualTo(InvocationStatus.PENDING_APPROVAL);
          assertThat(inv.getProposedOutput()).isNotNull();
        });
  }

  @Test
  void directModeNonInbox_returnsFailed() {
    runInTenant(
        () -> {
          var config =
              new InvokeAiSpecialistActionConfig(
                  "billing-za",
                  Map.of("entityType", "billing", "entityId", UUID.randomUUID().toString()),
                  null,
                  null,
                  "DIRECT",
                  60);
          var context = Map.of("actor", Map.<String, Object>of("id", ownerMemberId.toString()));
          var executionId = UUID.randomUUID();

          var result = executor.execute(config, context, executionId);

          assertThat(result.isSuccess()).isFalse();
          var failure = (ActionFailure) result;
          assertThat(failure.errorMessage()).contains("DIRECT mode is only permitted for INBOX");
        });
  }

  @Test
  void unknownSpecialist_returnsFailed() {
    runInTenant(
        () -> {
          var config =
              new InvokeAiSpecialistActionConfig("NONEXISTENT", Map.of(), null, null, "REVIEW", 60);
          var context = Map.of("actor", Map.<String, Object>of("id", ownerMemberId.toString()));
          var executionId = UUID.randomUUID();

          var result = executor.execute(config, context, executionId);

          assertThat(result.isSuccess()).isFalse();
          var failure = (ActionFailure) result;
          assertThat(failure.errorMessage()).contains("Failed to invoke AI specialist");
        });
  }

  @Test
  void variableResolutionInContextRef() {
    var entityId = UUID.randomUUID();
    var payload =
        new InboxSummaryPayload(
            UUID.randomUUID(), "2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z", "Summary", null);
    Mockito.when(
            runner.run(
                Mockito.anyString(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()))
        .thenReturn(payload);
    Mockito.when(runner.promptVersion(Mockito.anyString())).thenReturn("v1.0");

    runInTenant(
        () -> {
          var config =
              new InvokeAiSpecialistActionConfig(
                  "inbox-za",
                  Map.of("entityType", "inbox", "entityId", "{{event.entityId}}"),
                  null,
                  null,
                  "REVIEW",
                  60);
          var context =
              Map.of(
                  "actor", Map.<String, Object>of("id", ownerMemberId.toString()),
                  "event", Map.<String, Object>of("entityId", entityId.toString()));
          var executionId = UUID.randomUUID();

          var result = executor.execute(config, context, executionId);

          assertThat(result.isSuccess()).isTrue();
          var success = (ActionSuccess) result;
          var invId = UUID.fromString((String) success.resultData().get("invocationId"));
          var inv = invocationRepository.findById(invId).orElseThrow();
          assertThat(inv.getContextEntityId()).isEqualTo(entityId);
        });
  }

  @Test
  void failureHandling_marksInvocationFailed() {
    Mockito.when(
            runner.run(
                Mockito.anyString(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()))
        .thenThrow(new IllegalStateException("LLM error"));
    Mockito.when(runner.promptVersion(Mockito.anyString())).thenReturn("v1.0");

    runInTenant(
        () -> {
          var config =
              new InvokeAiSpecialistActionConfig(
                  "inbox-za",
                  Map.of("entityType", "inbox", "entityId", UUID.randomUUID().toString()),
                  null,
                  null,
                  "REVIEW",
                  60);
          var context = Map.of("actor", Map.<String, Object>of("id", ownerMemberId.toString()));
          var executionId = UUID.randomUUID();

          var result = executor.execute(config, context, executionId);

          assertThat(result.isSuccess()).isFalse();
          var failure = (ActionFailure) result;
          assertThat(failure.errorMessage()).contains("LLM error");
        });
  }

  @Test
  void directModeInbox_autoApplies() {
    var payload =
        new InboxSummaryPayload(
            UUID.randomUUID(),
            "2026-01-01T00:00:00Z",
            "2026-01-02T00:00:00Z",
            "Direct summary",
            null);
    Mockito.when(
            runner.run(
                Mockito.anyString(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()))
        .thenReturn(payload);
    Mockito.when(runner.promptVersion(Mockito.anyString())).thenReturn("v1.0");
    fakeInboxApplier.reset();

    runInTenant(
        () -> {
          var config =
              new InvokeAiSpecialistActionConfig(
                  "inbox-za",
                  Map.of("entityType", "inbox", "entityId", UUID.randomUUID().toString()),
                  null,
                  null,
                  "DIRECT",
                  60);
          var context = Map.of("actor", Map.<String, Object>of("id", ownerMemberId.toString()));
          var executionId = UUID.randomUUID();

          var result = executor.execute(config, context, executionId);

          assertThat(result.isSuccess()).isTrue();
          var success = (ActionSuccess) result;
          var invId = UUID.fromString((String) success.resultData().get("invocationId"));
          var inv = invocationRepository.findById(invId).orElseThrow();
          assertThat(inv.getStatus()).isEqualTo(InvocationStatus.AUTO_APPLIED);
        });
  }

  @TestConfiguration
  static class FakeInboxApplierConfig {
    @Bean("inboxSummaryApplier")
    FakeInboxApplier fakeInboxApplier() {
      return new FakeInboxApplier();
    }
  }

  static class FakeInboxApplier implements OutputApplier<InboxSummaryPayload> {
    private final AtomicInteger applyCount = new AtomicInteger();

    void reset() {
      applyCount.set(0);
    }

    int applyCount() {
      return applyCount.get();
    }

    @Override
    public Class<InboxSummaryPayload> payloadType() {
      return InboxSummaryPayload.class;
    }

    @Override
    public void apply(InboxSummaryPayload payload, UUID actorId) {
      applyCount.incrementAndGet();
    }
  }
}
