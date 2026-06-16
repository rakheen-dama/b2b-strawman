package io.b2mash.b2b.b2bstrawman.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * MCP handshake / registry / binding / audit integration tests (Epic 562C, scenarios 1, 2, 4, 6).
 *
 * <p>Drives the streamable {@code /mcp} endpoint over MockMvc with the {@code jwt()} post-processor
 * (pre-validated JWT, no JWKS) and {@code asyncDispatch()} to capture the SSE response. See the
 * sibling {@link McpAuthEnforcementTest} for the auth-failure / capability-absence scenarios (3,
 * 5).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McpHandshakeIntegrationTest {

  private static final String ORG_ID = "org_mcp_handshake_test";
  private static final String ACCEPT = "application/json, text/event-stream";

  private static final String INITIALIZE_BODY =
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"kazi-itest","version":"1.0.0"}}}""";
  private static final String INITIALIZED_NOTIFICATION_BODY =
      """
      {"jsonrpc":"2.0","method":"notifications/initialized"}""";
  private static final String TOOLS_LIST_BODY =
      """
      {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}""";
  private static final String RESOURCES_LIST_BODY =
      """
      {"jsonrpc":"2.0","id":3,"method":"resources/list","params":{}}""";

  @Autowired private MockMvc mockMvc;

  @Autowired
  private io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService provisioningService;

  @Autowired
  private io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository
      orgSchemaMappingRepository;

  @Autowired private ObjectMapper objectMapper;
  @Autowired private io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository auditEventRepository;

  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "MCP Handshake Test Org", null);
    TestMemberHelper.syncMember(mockMvc, ORG_ID, "user_owner", "owner@test.com", "Owner", "owner");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_member", "member@test.com", "Member", "member");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  // ---- helpers ----------------------------------------------------------------

  /** POST a JSON-RPC body to /mcp as {@code jwt}, complete async dispatch, return the result. */
  private MvcResult mcpCall(String body, JwtRequestPostProcessor jwt, String sessionId)
      throws Exception {
    var builder =
        post("/mcp")
            .with(jwt)
            .header("Accept", ACCEPT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
    if (sessionId != null) {
      builder = builder.header("Mcp-Session-Id", sessionId);
    }
    MvcResult result = mockMvc.perform(builder).andReturn();
    // The streamable transport uses a functional RouterFunction + ServerResponse.SseBuilder, which
    // (unlike @Controller + SseEmitter) completes synchronously under MockMvc — no async dispatch.
    // Only fall through to asyncDispatch if the transport DID start an async request.
    if (result.getRequest().isAsyncStarted()) {
      result = mockMvc.perform(asyncDispatch(result)).andReturn();
    }
    return result;
  }

  /**
   * Extract the JSON-RPC envelope from an {@code /mcp} response. The streamable WebMVC transport
   * returns a single JSON-RPC reply either as plain {@code application/json} (the common case under
   * MockMvc) or SSE-framed ({@code event: message\ndata: {json}}). Handle both: prefer the first
   * SSE {@code data:} line if present, otherwise parse the whole body as JSON.
   */
  private JsonNode parseRpc(String body) {
    String json =
        body.lines()
            .filter(l -> l.startsWith("data:"))
            .map(l -> l.substring(5).trim())
            .findFirst()
            .orElse(body.trim());
    if (json.isBlank()) {
      throw new AssertionError("empty /mcp response body");
    }
    return objectMapper.readTree(json);
  }

  // ---- (1) initialize ---------------------------------------------------------

  @Test
  void initializeNegotiatesResourcesAndToolsWithReadOnlyInstructions() throws Exception {
    MvcResult init = mcpCall(INITIALIZE_BODY, TestJwtFactory.ownerJwt(ORG_ID, "user_owner"), null);

    String sessionId = init.getResponse().getHeader("Mcp-Session-Id");
    assertThat(sessionId).as("initialize must set Mcp-Session-Id header").isNotBlank();

    JsonNode result = parseRpc(init.getResponse().getContentAsString()).get("result");
    assertThat(result).isNotNull();
    assertThat(result.at("/serverInfo/name").asString()).isEqualTo("kazi");

    // resources + tools advertised; prompts + completion NOT advertised (read-only surface)
    assertThat(result.at("/capabilities/tools").isMissingNode()).isFalse();
    assertThat(result.at("/capabilities/resources").isMissingNode()).isFalse();
    assertThat(result.path("capabilities").has("prompts")).isFalse();
    assertThat(result.path("capabilities").has("completion")).isFalse();

    // read-only instructions advertised
    assertThat(result.has("instructions")).isTrue();
    assertThat(result.at("/instructions").asString()).containsIgnoringCase("read-only");
  }

  // ---- (2) tools/list + resources/list ---------------------------------------

  @Test
  void toolsListAndResourcesListReturnTheTrivialRegistry() throws Exception {
    JwtRequestPostProcessor jwt = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    MvcResult init = mcpCall(INITIALIZE_BODY, jwt, null);
    String sessionId = init.getResponse().getHeader("Mcp-Session-Id");

    // notifications/initialized — notification, no result expected
    mcpCall(INITIALIZED_NOTIFICATION_BODY, jwt, sessionId);

    MvcResult tl = mcpCall(TOOLS_LIST_BODY, jwt, sessionId);
    JsonNode tools = parseRpc(tl.getResponse().getContentAsString()).at("/result/tools");
    assertThat(tools.isArray()).isTrue();
    boolean hasPing = false;
    for (JsonNode t : tools) {
      if ("kazi_ping".equals(t.at("/name").asString())) {
        hasPing = true;
        break;
      }
    }
    assertThat(hasPing).as("trivial registry exposes the kazi_ping tool (562B)").isTrue();

    MvcResult rl = mcpCall(RESOURCES_LIST_BODY, jwt, sessionId);
    assertThat(parseRpc(rl.getResponse().getContentAsString()).at("/result/resources").isArray())
        .isTrue();
  }

  // ---- (4) valid token binds RequestScopes (via probe) -----------------------

  @Test
  void validTokenBindsRequestScopes() throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/me/capabilities")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_owner")))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.isOwner")
                .value(true))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.capabilities")
                .isArray());
  }

  // ---- (6) mcp.session.opened emitted on initialize --------------------------

  @Test
  void initializeEmitsSessionOpenedAuditEvent() throws Exception {
    mcpCall(INITIALIZE_BODY, TestJwtFactory.ownerJwt(ORG_ID, "user_owner"), null);

    ScopedValue.where(io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var page =
                  auditEventRepository.findByFilter(
                      null,
                      null,
                      null,
                      "mcp.session.opened",
                      null,
                      null,
                      org.springframework.data.domain.PageRequest.of(0, 50));
              assertThat(page.getContent())
                  .extracting(io.b2mash.b2b.b2bstrawman.audit.AuditEvent::getEventType)
                  .contains("mcp.session.opened");
            });
  }
}
