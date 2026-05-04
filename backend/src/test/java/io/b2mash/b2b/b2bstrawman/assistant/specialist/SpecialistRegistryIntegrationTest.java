package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.assistant.provider.ChatRequest;
import io.b2mash.b2b.b2bstrawman.assistant.provider.LlmChatProvider;
import io.b2mash.b2b.b2bstrawman.assistant.provider.LlmChatProviderRegistry;
import io.b2mash.b2b.b2bstrawman.assistant.provider.ModelInfo;
import io.b2mash.b2b.b2bstrawman.assistant.provider.StreamEvent;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationService;
import io.b2mash.b2b.b2bstrawman.integration.secret.SecretStore;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration coverage for Phase 70 Epic 511A — specialist registry, /chat specialistId branch,
 * AI_ASSISTANT_USE gate, session service, prompt-linter wiring.
 *
 * <p>This test deliberately uses no plan-tier checks — there are no plan-tier subscriptions in the
 * product (PR #1286 was reverted for reintroducing PlanTier; that regression must not return).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpecialistRegistryIntegrationTest {

  private static final String ORG_ID = "org_specialist_511a_test";

  @MockitoBean private LlmChatProviderRegistry llmChatProviderRegistry;
  private LlmChatProvider mockProvider;

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private IntegrationService integrationService;
  @Autowired private SecretStore secretStore;
  @Autowired private SpecialistRegistry specialistRegistry;
  @Autowired private SpecialistSessionService specialistSessionService;
  @Autowired private SystemPromptBuilder systemPromptBuilder;

  private UUID memberIdOwner;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Specialist 511A Test Org", null);
    var ownerStr =
        TestMemberHelper.syncMember(
            mockMvc,
            ORG_ID,
            "user_specialist_owner",
            "specialist_owner@test.com",
            "Specialist Owner",
            "owner");
    memberIdOwner = UUID.fromString(ownerStr);
    TestMemberHelper.syncMember(
        mockMvc,
        ORG_ID,
        "user_specialist_member",
        "specialist_member@test.com",
        "Specialist Member",
        "member");
    var schema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, schema)
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
  }

  @BeforeEach
  void setUpMocks() {
    mockProvider = Mockito.mock(LlmChatProvider.class);
    when(llmChatProviderRegistry.get(anyString())).thenReturn(mockProvider);
    when(mockProvider.availableModels())
        .thenReturn(List.of(new ModelInfo("claude-sonnet-4-6", "Claude Sonnet 4.6", true)));
    when(mockProvider.validateKey(anyString(), anyString())).thenReturn(true);
  }

  /** 511A.8 test 1: registry wiring — three specialists registered on context start. */
  @Test
  void registryRegistersThreeSpecialists() {
    assertThat(specialistRegistry.findById("billing-za")).isPresent();
    assertThat(specialistRegistry.findById("intake-za")).isPresent();
    assertThat(specialistRegistry.findById("inbox-za")).isPresent();
    assertThat(specialistRegistry.all()).hasSize(3);
  }

  /** 511A.8 test 2: /chat with specialistId routes through specialist's prompt. */
  @Test
  void chatWithSpecialistIdInjectsSpecialistPrompt() throws Exception {
    var captor = ArgumentCaptor.forClass(ChatRequest.class);
    doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<StreamEvent> consumer = invocation.getArgument(1);
              consumer.accept(new StreamEvent.TextDelta("ok"));
              consumer.accept(new StreamEvent.Usage(1, 1));
              consumer.accept(new StreamEvent.Done());
              return null;
            })
        .when(mockProvider)
        .chat(captor.capture(), any());

    var mvcResult =
        mockMvc
            .perform(
                post("/api/assistant/chat")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_specialist_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"message":"hi","history":[],"currentPage":"/billing","specialistId":"billing-za"}
                        """))
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.request()
                    .asyncStarted())
            .andReturn();

    mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk());

    var captured = captor.getValue();
    assertThat(captured.systemPrompt())
        .as("specialist body should appear in the system prompt")
        .contains("Specialist Instructions");
    assertThat(captured.systemPrompt())
        .as("specialist branch must NOT use the generalist DocTeams suffix")
        .doesNotContain("You are the DocTeams assistant");
    // billing-za declares its tool subset (Epic 512A); ensure tools are wired through.
    assertThat(captured.tools()).isNotEmpty();
  }

  /** 511A.8 test 3: AI_ASSISTANT_USE gate — non-capable caller → 403. */
  @Test
  void specialistListRequiresAiAssistantUseCapability() throws Exception {
    mockMvc
        .perform(
            get("/api/assistant/specialists")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_specialist_member")))
        .andExpect(status().isForbidden());
  }

  /** Owner gets the AI_ASSISTANT_USE capability via the "owner = ALL_NAMES" rule. */
  @Test
  void specialistListAccessibleToOwner() throws Exception {
    mockMvc
        .perform(
            get("/api/assistant/specialists")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_specialist_owner")))
        .andExpect(status().isOk());
  }

  /** 511A.8 test 4: SpecialistSessionService.start returns correct SessionHandle shape. */
  @Test
  void sessionServiceReturnsSessionHandle() throws Exception {
    var schema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    SessionHandle[] holder = new SessionHandle[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of("AI_ASSISTANT_USE"))
        .run(
            () ->
                holder[0] =
                    specialistSessionService.start(
                        "billing-za", new ContextRef(null, null, "/billing"), null));
    var handle = holder[0];
    assertThat(handle).isNotNull();
    assertThat(handle.specialistId()).isEqualTo("billing-za");
    assertThat(handle.sessionId()).isNotNull();
    assertThat(handle.systemPromptHash()).isNotBlank();
    assertThat(handle.displayName()).isEqualTo("Billing Specialist");
    assertThat(handle.toolIds()).isNotEmpty();
  }

  /** 511A.8 test 5: prompt builder loaded all three stubs. */
  @Test
  void systemPromptBuilderLoadedAllStubs() {
    assertThat(systemPromptBuilder.isLoaded("billing-za")).isTrue();
    assertThat(systemPromptBuilder.isLoaded("intake-za")).isTrue();
    assertThat(systemPromptBuilder.isLoaded("inbox-za")).isTrue();
    assertThat(systemPromptBuilder.promptVersion("billing-za")).isEqualTo("1.0.0");
  }
}
