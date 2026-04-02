package io.b2mash.b2b.b2bstrawman.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AssistantControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_assistant_ctrl_test";

  @MockitoBean private LlmChatProviderRegistry llmChatProviderRegistry;

  private LlmChatProvider mockProvider;

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private IntegrationService integrationService;
  @Autowired private SecretStore secretStore;
  @Autowired private AssistantService assistantService;

  private String tenantSchema;
  private UUID memberIdOwner;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Controller Test Org", null);
    var memberIdStr =
        syncMember(ORG_ID, "user_actrl_owner", "actrl_owner@test.com", "ACTrl Owner", "owner");
    memberIdOwner = UUID.fromString(memberIdStr);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Configure AI integration
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
  }

  // Test 1: POST /api/assistant/chat returns Content-Type: text/event-stream
  @Test
  void chatEndpointReturnsTextEventStream() throws Exception {
    configureMockProviderTextResponse("Hello");

    var mvcResult =
        mockMvc
            .perform(
                post("/api/assistant/chat")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"message":"Hello","history":[],"currentPage":"/dashboard"}
                        """))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc
        .perform(asyncDispatch(mvcResult))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string("Content-Type", org.hamcrest.Matchers.containsString("text/event-stream")));
  }

  // Test 2: SSE response contains text_delta events
  @Test
  void chatEndpointEmitsTextDeltaEvents() throws Exception {
    configureMockProviderTextResponse("Here is the data");

    var mvcResult =
        mockMvc
            .perform(
                post("/api/assistant/chat")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"message":"Show me projects","history":[],"currentPage":"/projects"}
                        """))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk());

    String body = mvcResult.getResponse().getContentAsString();
    assertThat(body).contains("text_delta");
    assertThat(body).contains("Here is the data");
    assertThat(body).contains("done");
  }

  // Test 3: Unauthenticated request returns 401
  @Test
  void chatEndpointRequiresAuthentication() throws Exception {
    mockMvc
        .perform(
            post("/api/assistant/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"message":"Hello","history":[],"currentPage":"/dashboard"}
                    """))
        .andExpect(status().isUnauthorized());
  }

  // Test 4: Confirm with unknown toolCallId returns 404
  @Test
  void confirmUnknownToolCallIdReturns404() throws Exception {
    mockMvc
        .perform(
            post("/api/assistant/chat/confirm")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"toolCallId":"unknown-tool-call-id-12345","approved":true}
                    """))
        .andExpect(status().isNotFound());
  }

  // Test 5: Confirm with valid pending confirmation returns {"acknowledged": true}
  @Test
  void confirmEndpointReturnsAcknowledgedForKnownToolCallId() throws Exception {
    // Plant a confirmation via the package-private test helper and capture the future
    var future = assistantService.plantConfirmation("test-confirm-tool-call", memberIdOwner);

    mockMvc
        .perform(
            post("/api/assistant/chat/confirm")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"toolCallId":"test-confirm-tool-call","approved":true}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.acknowledged").value(true));

    // Verify the confirmation was actually applied to the future
    assertThat(future).isCompletedWithValue(true);
  }

  // Test 6: GET /api/settings/integrations/ai/models returns model list
  @Test
  void getAiModelsReturnsListOfModels() throws Exception {
    mockMvc
        .perform(get("/api/settings/integrations/ai/models").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.models").isArray())
        .andExpect(jsonPath("$.models[0].id").exists())
        .andExpect(jsonPath("$.models[0].name").exists())
        .andExpect(jsonPath("$.models[0].recommended").exists());
  }

  // Test 7: POST /api/integrations/AI/test returns success with mocked provider
  @Test
  void testConnectionAiDomainReturnsSuccess() throws Exception {
    mockMvc
        .perform(post("/api/integrations/AI/test").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.providerName").value("anthropic"));
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

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_actrl_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")));
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
                        {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s","name":"%s","avatarUrl":null,"orgRole":"%s"}
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
