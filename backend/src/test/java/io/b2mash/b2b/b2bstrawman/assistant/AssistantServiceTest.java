package io.b2mash.b2b.b2bstrawman.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.assistant.provider.ChatRequest;
import io.b2mash.b2b.b2bstrawman.assistant.provider.LlmChatProvider;
import io.b2mash.b2b.b2bstrawman.assistant.provider.LlmChatProviderRegistry;
import io.b2mash.b2b.b2bstrawman.assistant.provider.StreamEvent;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationService;
import io.b2mash.b2b.b2bstrawman.integration.secret.SecretStore;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AssistantServiceTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_assistant_service_test";
  private static final String ORG_ID_STARTER = "org_assistant_starter_test";
  private static final String ORG_ID_NO_AI = "org_assistant_no_ai_test";
  private static final String ORG_ID_NO_KEY = "org_assistant_no_key_test";

  /** Mock the entire registry to avoid the duplicate provider ID issue. */
  @MockitoBean private LlmChatProviderRegistry llmChatProviderRegistry;

  /** Mock provider that the registry will return. */
  private LlmChatProvider mockProvider;

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private IntegrationService integrationService;
  @Autowired private SecretStore secretStore;
  @Autowired private AssistantService assistantService;

  private String tenantSchema;
  private UUID memberIdOwner;
  private String tenantSchemaStarter;
  private UUID memberIdStarter;
  private String tenantSchemaNoAi;
  private UUID memberIdNoAi;
  private String tenantSchemaNoKey;
  private UUID memberIdNoKey;

  @BeforeAll
  void setUp() throws Exception {
    // === Fully configured PRO tenant with AI ===
    provisioningService.provisionTenant(ORG_ID, "AI Service Test Org", null);
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    var memberIdStr =
        syncMember(ORG_ID, "user_ast_owner", "ast_owner@test.com", "AST Owner", "owner");
    memberIdOwner = UUID.fromString(memberIdStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of())
        .run(
            () -> {
              var actor = new ActorContext(memberIdOwner, "owner");
              orgSettingsService.updateSettingsWithBranding(
                  "USD", null, null, null, true, null, null, actor);
              integrationService.upsertIntegration(
                  IntegrationDomain.AI, "anthropic", "{\"model\": \"claude-sonnet-4-6\"}");
              secretStore.store("ai:anthropic:api_key", "sk-test-key-12345");
            });

    // === STARTER tier tenant ===
    provisioningService.provisionTenant(ORG_ID_STARTER, "Starter Tier Org", null);
    var memberIdStarterStr =
        syncMember(
            ORG_ID_STARTER, "user_ast_starter", "ast_starter@test.com", "AST Starter", "owner");
    memberIdStarter = UUID.fromString(memberIdStarterStr);
    tenantSchemaStarter =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_STARTER).orElseThrow().getSchemaName();

    // === PRO tenant with AI NOT enabled ===
    provisioningService.provisionTenant(ORG_ID_NO_AI, "No AI Org", null);
    planSyncService.syncPlan(ORG_ID_NO_AI, "pro-plan");
    var memberIdNoAiStr =
        syncMember(ORG_ID_NO_AI, "user_ast_noai", "ast_noai@test.com", "AST NoAI", "owner");
    memberIdNoAi = UUID.fromString(memberIdNoAiStr);
    tenantSchemaNoAi =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_NO_AI).orElseThrow().getSchemaName();

    // === PRO tenant with AI enabled but NO API key ===
    provisioningService.provisionTenant(ORG_ID_NO_KEY, "No Key Org", null);
    planSyncService.syncPlan(ORG_ID_NO_KEY, "pro-plan");
    var memberIdNoKeyStr =
        syncMember(ORG_ID_NO_KEY, "user_ast_nokey", "ast_nokey@test.com", "AST NoKey", "owner");
    memberIdNoKey = UUID.fromString(memberIdNoKeyStr);
    tenantSchemaNoKey =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_NO_KEY).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaNoKey)
        .where(RequestScopes.ORG_ID, ORG_ID_NO_KEY)
        .where(RequestScopes.MEMBER_ID, memberIdNoKey)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of())
        .run(
            () -> {
              var actor = new ActorContext(memberIdNoKey, "owner");
              orgSettingsService.updateSettingsWithBranding(
                  "USD", null, null, null, true, null, null, actor);
              integrationService.upsertIntegration(
                  IntegrationDomain.AI, "anthropic", "{\"model\": \"claude-sonnet-4-6\"}");
              // Do NOT store API key
            });
  }

  @BeforeEach
  void setUpMocks() {
    mockProvider = Mockito.mock(LlmChatProvider.class);
    when(llmChatProviderRegistry.get(anyString())).thenReturn(mockProvider);
  }

  @Test
  void textOnlyResponseEmitsTextDeltaAndDone() {
    doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<StreamEvent> consumer = invocation.getArgument(1);
              consumer.accept(new StreamEvent.TextDelta("Hello"));
              consumer.accept(new StreamEvent.Usage(50, 25));
              consumer.accept(new StreamEvent.Done());
              return null;
            })
        .when(mockProvider)
        .chat(any(ChatRequest.class), any());

    var events = runChatInScope(tenantSchema, ORG_ID, memberIdOwner);

    assertThat(events).anyMatch(e -> e.contains("text_delta") && e.contains("Hello"));
    assertThat(events).anyMatch(e -> e.contains("done"));
  }

  @Test
  void readToolInvocationExecutesInlineAndContinues() {
    AtomicInteger callCount = new AtomicInteger(0);
    doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<StreamEvent> consumer = invocation.getArgument(1);
              int call = callCount.incrementAndGet();
              if (call == 1) {
                consumer.accept(new StreamEvent.ToolUse("call_1", "list_projects", Map.of()));
                consumer.accept(new StreamEvent.Usage(100, 50));
                consumer.accept(new StreamEvent.Done());
              } else {
                consumer.accept(new StreamEvent.TextDelta("Here are your projects"));
                consumer.accept(new StreamEvent.Usage(80, 40));
                consumer.accept(new StreamEvent.Done());
              }
              return null;
            })
        .when(mockProvider)
        .chat(any(ChatRequest.class), any());

    var events = runChatInScope(tenantSchema, ORG_ID, memberIdOwner);

    assertThat(events).anyMatch(e -> e.contains("tool_use") && e.contains("list_projects"));
    assertThat(events).anyMatch(e -> e.contains("tool_result"));
    assertThat(events)
        .anyMatch(e -> e.contains("text_delta") && e.contains("Here are your projects"));
    assertThat(events).anyMatch(e -> e.contains("done"));
  }

  @Test
  void systemPromptIncludesGuide() {
    var capturedRequest = new AtomicReference<ChatRequest>();
    doAnswer(
            invocation -> {
              capturedRequest.set(invocation.getArgument(0));
              @SuppressWarnings("unchecked")
              Consumer<StreamEvent> consumer = invocation.getArgument(1);
              consumer.accept(new StreamEvent.TextDelta("OK"));
              consumer.accept(new StreamEvent.Done());
              return null;
            })
        .when(mockProvider)
        .chat(any(ChatRequest.class), any());

    runChatInScope(tenantSchema, ORG_ID, memberIdOwner);

    assertThat(capturedRequest.get()).isNotNull();
    assertThat(capturedRequest.get().systemPrompt()).contains("DocTeams");
    assertThat(capturedRequest.get().systemPrompt()).contains("Navigation");
  }

  @Test
  void systemPromptIncludesTenantContext() {
    var capturedRequest = new AtomicReference<ChatRequest>();
    doAnswer(
            invocation -> {
              capturedRequest.set(invocation.getArgument(0));
              @SuppressWarnings("unchecked")
              Consumer<StreamEvent> consumer = invocation.getArgument(1);
              consumer.accept(new StreamEvent.TextDelta("OK"));
              consumer.accept(new StreamEvent.Done());
              return null;
            })
        .when(mockProvider)
        .chat(any(ChatRequest.class), any());

    runChatInScope(tenantSchema, ORG_ID, memberIdOwner);

    assertThat(capturedRequest.get()).isNotNull();
    var prompt = capturedRequest.get().systemPrompt();
    assertThat(prompt).contains("AI Service Test Org");
    assertThat(prompt).contains("owner");
    assertThat(prompt).contains("Current page");
  }

  @Test
  void errorWhenAiNotEnabled() {
    var events = runChatInScope(tenantSchemaNoAi, ORG_ID_NO_AI, memberIdNoAi);

    assertThat(events)
        .anyMatch(e -> e.contains("error") && e.contains("AI assistant is not enabled"));
  }

  // Test removed: errorWhenStarterTier — Tier model was removed in Epic 419A.
  // Subscription lifecycle (not Tier) now governs feature access (Epic 420).

  @Test
  void errorWhenNoApiKey() {
    var events = runChatInScope(tenantSchemaNoKey, ORG_ID_NO_KEY, memberIdNoKey);

    assertThat(events).anyMatch(e -> e.contains("error") && e.contains("No API key configured"));
  }

  @Test
  void tokenUsageAccumulatedAcrossMultiTurns() {
    AtomicInteger callCount = new AtomicInteger(0);
    doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<StreamEvent> consumer = invocation.getArgument(1);
              int call = callCount.incrementAndGet();
              if (call == 1) {
                consumer.accept(new StreamEvent.ToolUse("call_tok_1", "list_projects", Map.of()));
                consumer.accept(new StreamEvent.Usage(100, 50));
                consumer.accept(new StreamEvent.Done());
              } else {
                consumer.accept(new StreamEvent.TextDelta("Result"));
                consumer.accept(new StreamEvent.Usage(80, 40));
                consumer.accept(new StreamEvent.Done());
              }
              return null;
            })
        .when(mockProvider)
        .chat(any(ChatRequest.class), any());

    var events = runChatInScope(tenantSchema, ORG_ID, memberIdOwner);

    // The done event should have accumulated tokens: 100+80=180 input, 50+40=90 output
    assertThat(events).anyMatch(e -> e.contains("done") && e.contains("180") && e.contains("90"));
  }

  // === Helpers ===

  private List<String> runChatInScope(String schema, String orgId, UUID memberId) {
    var context = new ChatContext("Hello", List.of(), "/org/test/dashboard");
    List<String> capturedEvents = new ArrayList<>();
    var emitter = new SseEmitter(30_000L);

    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of())
        .run(
            () -> {
              var spyEmitter = Mockito.spy(emitter);
              try {
                Mockito.doAnswer(
                        inv -> {
                          SseEmitter.SseEventBuilder builder = inv.getArgument(0);
                          // Call build() to get the actual data content
                          var dataItems = builder.build();
                          var sb = new StringBuilder();
                          for (var item : dataItems) {
                            sb.append(item.getData().toString()).append(" ");
                          }
                          capturedEvents.add(sb.toString());
                          return null;
                        })
                    .when(spyEmitter)
                    .send(any(SseEmitter.SseEventBuilder.class));
              } catch (IOException e) {
                throw new RuntimeException(e);
              }

              assistantService.chat(context, spyEmitter);
            });

    return capturedEvents;
  }

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s", "clerkUserId": "%s",
                          "email": "%s", "name": "%s",
                          "avatarUrl": null, "orgRole": "%s"
                        }
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
