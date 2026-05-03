package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.assistant.provider.ChatRequest;
import io.b2mash.b2b.b2bstrawman.assistant.provider.LlmChatProvider;
import io.b2mash.b2b.b2bstrawman.assistant.provider.LlmChatProviderRegistry;
import io.b2mash.b2b.b2bstrawman.assistant.provider.ModelInfo;
import io.b2mash.b2b.b2bstrawman.assistant.provider.StreamEvent;
import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.billing.PlanTier;
import io.b2mash.b2b.b2bstrawman.billing.PlanTierResolver;
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
import java.util.Map;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 70 / Epic 511B integration tests for the {@code /api/assistant/chat} extension.
 *
 * <p>Asserts that when {@code specialistId} is supplied, the {@link
 * io.b2mash.b2b.b2bstrawman.assistant.AssistantService} prepends the specialist's system prompt
 * (verified by hunting for SA-tariff tokens like {@code "ZAR"} and {@code "LSSA tariff"} in the
 * captured {@link ChatRequest#systemPrompt()}) and narrows the tool list to the intersection of the
 * specialist's declared {@code toolIds} and the caller's capability-allowed tools. Hand-off back to
 * the generalist (omitting {@code specialistId} on a follow-up call) is asserted by comparing
 * prompt and tool counts across the two calls.
 *
 * <p>The Anthropic LLM is mocked via {@code @MockitoBean LlmChatProviderRegistry} (the established
 * pattern; see {@code AssistantControllerTest}). No WireMock — Mockito gives us {@link
 * ArgumentCaptor} access to the {@link ChatRequest} which is exactly what we need to verify.
 *
 * <p>The intersection narrowing is asserted concretely by registering two test-only {@link
 * AssistantTool} beans: one named {@code "GetInvoice"} (matches a BILLING {@code toolId}) and one
 * named {@code "ZzzNonSpecialist"} (does not). After enrichment the captured tool list must contain
 * only {@code GetInvoice} from those two — proving the intersection logic, not just "smaller list".
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SpecialistChatExtensionIntegrationTest.TestTools.class})
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpecialistChatExtensionIntegrationTest {

  private static final String ORG_ID = "org_specialist_chat_test";

  @MockitoBean private LlmChatProviderRegistry llmChatProviderRegistry;
  @MockitoBean private PlanTierResolver planTierResolver;

  private LlmChatProvider mockProvider;

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private IntegrationService integrationService;
  @Autowired private SecretStore secretStore;

  private UUID memberIdOwner;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Specialist Chat Ext Test Org", null);
    var memberIdStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_spec_chat_owner", "spec_chat@test.com", "Owner", "owner");
    memberIdOwner = UUID.fromString(memberIdStr);
    var tenantSchema =
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
  }

  @BeforeEach
  void setUpMocks() {
    mockProvider = Mockito.mock(LlmChatProvider.class);
    when(llmChatProviderRegistry.get(anyString())).thenReturn(mockProvider);
    when(mockProvider.availableModels())
        .thenReturn(
            List.of(
                new ModelInfo("claude-sonnet-4-6", "Claude Sonnet 4.6", true),
                new ModelInfo("claude-opus-4-6", "Claude Opus 4.6", false)));
    when(mockProvider.validateKey(anyString(), anyString())).thenReturn(true);
    configureMockProviderTextResponse("ack");
    // Default: PRO tier so existing chat extension behaviour is exercised.
    when(planTierResolver.resolveForCurrentOrg()).thenReturn(PlanTier.PRO);
    when(planTierResolver.resolveForOrganization(any())).thenReturn(PlanTier.PRO);
  }

  @Test
  void chatWithSpecialistIdInjectsBillingPromptAndNarrowsTools() throws Exception {
    var mvcResult =
        mockMvc
            .perform(
                post("/api/assistant/chat")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_spec_chat_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"message":"Polish invoice","history":[],"currentPage":"/invoices/abc",\
                        "specialistId":"BILLING"}
                        """))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk());

    var captor = ArgumentCaptor.forClass(ChatRequest.class);
    verify(mockProvider).chat(captor.capture(), any());
    var captured = captor.getValue();

    // System prompt should contain billing-za.md tokens validated by 511A's linter.
    assertThat(captured.systemPrompt()).contains("ZAR");
    assertThat(captured.systemPrompt()).contains("LSSA tariff");

    // Concrete intersection assertion: BILLING declares "GetInvoice" + 8 others. Our two
    // test-tools register "GetInvoice" (matches) and "ZzzNonSpecialist" (does not). After
    // narrowing, the captured tool list must include "GetInvoice" and exclude "ZzzNonSpecialist".
    assertThat(captured.tools()).extracting("name").contains("GetInvoice");
    assertThat(captured.tools()).extracting("name").doesNotContain("ZzzNonSpecialist");
    var narrowedToolCount = captured.tools().size();

    // Reset and call without specialistId — should send the full capability-filtered list.
    Mockito.reset(mockProvider);
    when(mockProvider.availableModels())
        .thenReturn(List.of(new ModelInfo("claude-sonnet-4-6", "Claude Sonnet 4.6", true)));
    when(mockProvider.validateKey(anyString(), anyString())).thenReturn(true);
    configureMockProviderTextResponse("ack");

    var generalistResult =
        mockMvc
            .perform(
                post("/api/assistant/chat")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_spec_chat_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"message":"Generic","history":[],"currentPage":"/invoices/abc"}
                        """))
            .andExpect(request().asyncStarted())
            .andReturn();
    mockMvc.perform(asyncDispatch(generalistResult)).andExpect(status().isOk());

    var generalistCaptor = ArgumentCaptor.forClass(ChatRequest.class);
    verify(mockProvider).chat(generalistCaptor.capture(), any());
    var generalist = generalistCaptor.getValue();

    // Generalist gets the full capability-filtered tool list (strictly larger because the
    // non-specialist test tool is included only here).
    assertThat(generalist.tools().size())
        .as("generalist tool list should be strictly larger than the specialist-narrowed list")
        .isGreaterThan(narrowedToolCount);
    assertThat(generalist.tools()).extracting("name").contains("ZzzNonSpecialist");

    // Generalist prompt should NOT contain specialist-specific SA tokens.
    assertThat(generalist.systemPrompt()).doesNotContain("LSSA tariff");
  }

  @Test
  void chatWithUnknownSpecialistIdEmitsErrorEvent() throws Exception {
    var mvcResult =
        mockMvc
            .perform(
                post("/api/assistant/chat")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_spec_chat_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"message":"hi","history":[],"currentPage":"/dashboard",\
                        "specialistId":"NOT_A_REAL_SPECIALIST"}
                        """))
            .andExpect(request().asyncStarted())
            .andReturn();
    mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk());

    String body = mvcResult.getResponse().getContentAsString();
    assertThat(body).contains("error");
    assertThat(body).contains("NOT_A_REAL_SPECIALIST");
  }

  /**
   * Critical security guard: a STARTER-tier tenant must NOT be able to bypass the
   * read/start-session gates by passing {@code specialistId} on the chat endpoint. The provider
   * must never be invoked because gate evaluation aborts the stream before LLM invocation.
   */
  @Test
  void chatWithSpecialistIdReturns403WhenStarterTier() throws Exception {
    when(planTierResolver.resolveForCurrentOrg()).thenReturn(PlanTier.STARTER);

    var mvcResult =
        mockMvc
            .perform(
                post("/api/assistant/chat")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_spec_chat_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"message":"hi","history":[],"currentPage":"/invoices/abc",\
                        "specialistId":"BILLING"}
                        """))
            .andExpect(request().asyncStarted())
            .andReturn();
    mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk());

    String body = mvcResult.getResponse().getContentAsString();
    assertThat(body).contains("error");
    assertThat(body).containsIgnoringCase("PRO subscription");
    // Critical: the provider must NOT have been invoked, so no captured ChatRequest with the
    // billing prompt can have leaked to the LLM.
    verifyNoInteractions(mockProvider);
  }

  /**
   * Critical security guard: an authenticated member without {@code AI_ASSISTANT_USE} capability
   * must NOT be able to bypass the gate via the chat endpoint. The default {@code member} system
   * role does not grant AI_ASSISTANT_USE (only owner/admin via {@code ALL_NAMES}), so a JIT-synced
   * member-role JWT exercises the capability-missing branch directly. Provider must NOT be invoked.
   */
  @Test
  void chatWithSpecialistIdReturns403WhenCapabilityMissing() throws Exception {
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_spec_chat_nocap", "spec_chat_nocap@test.com", "NoCap", "member");

    var mvcResult =
        mockMvc
            .perform(
                post("/api/assistant/chat")
                    .with(TestJwtFactory.memberJwt(ORG_ID, "user_spec_chat_nocap"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"message":"hi","history":[],"currentPage":"/invoices/abc",\
                        "specialistId":"BILLING"}
                        """))
            .andExpect(request().asyncStarted())
            .andReturn();
    mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk());

    String body = mvcResult.getResponse().getContentAsString();
    assertThat(body).contains("error");
    assertThat(body).contains("permission to use AI specialists");
    verifyNoInteractions(mockProvider);
  }

  // --- Helpers ---

  private void configureMockProviderTextResponse(String text) {
    doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<StreamEvent> consumer = invocation.getArgument(1);
              consumer.accept(new StreamEvent.TextDelta(text));
              consumer.accept(new StreamEvent.Usage(10, 5));
              consumer.accept(new StreamEvent.Done());
              return null;
            })
        .when(mockProvider)
        .chat(any(ChatRequest.class), any());
  }

  /**
   * Test-only {@link AssistantTool} beans whose names exercise the specialist tool-subset
   * intersection logic concretely. {@code GetInvoice} matches a BILLING {@code toolId}; {@code
   * ZzzNonSpecialist} does not. Both have empty {@code requiredCapabilities} so they are visible to
   * any caller (no capability filtering interference). They never execute in these tests — the LLM
   * mock returns a text-only stream — so {@link #execute} is a never-called stub.
   */
  @TestConfiguration
  static class TestTools {
    @Bean
    AssistantTool getInvoiceTestTool() {
      return new StaticTool("GetInvoice", "Stub matching BILLING toolIds");
    }

    @Bean
    AssistantTool nonSpecialistTestTool() {
      return new StaticTool("ZzzNonSpecialist", "Stub NOT in any specialist toolIds");
    }
  }

  /** Minimal stub that returns the configured name. Never executed in this test class. */
  private static final class StaticTool implements AssistantTool {
    private final String name;
    private final String description;

    StaticTool(String name, String description) {
      this.name = name;
      this.description = description;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public String description() {
      return description;
    }

    @Override
    public Map<String, Object> inputSchema() {
      return Map.of("type", "object", "properties", Map.of(), "required", List.of());
    }

    @Override
    public boolean requiresConfirmation() {
      return false;
    }

    @Override
    public Set<String> requiredCapabilities() {
      return Set.of();
    }

    @Override
    public Object execute(Map<String, Object> input, TenantToolContext context) {
      throw new UnsupportedOperationException("test stub — should not be invoked");
    }
  }
}
