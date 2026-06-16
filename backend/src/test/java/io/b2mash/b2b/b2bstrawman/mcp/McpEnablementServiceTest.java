package io.b2mash.b2b.b2bstrawman.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.mcp.consent.McpEgressConsentRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration tests for the Epic 565B effective-state gate: an MCP {@code tools/call} only returns
 * data when the connector is effectively enabled (an enabled {@code MCP} {@link
 * io.b2mash.b2b.b2bstrawman.integration.OrgIntegration} row AND a GRANTED POPIA consent). When
 * disabled it refuses with a non-leaking {@code not_enabled} error; the {@code initialize}
 * handshake still succeeds. Drives the real {@code /mcp} JSON-RPC endpoint over MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McpEnablementServiceTest {

  private static final String ORG_ID = "org_mcp_enablement_test";
  private static final String CONSENT_VERSION = "popia-egress-v1";
  private static final String ACCEPT = "application/json, text/event-stream";

  private static final String INITIALIZE_BODY =
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"kazi-itest","version":"1.0.0"}}}""";
  private static final String INITIALIZED_NOTIFICATION_BODY =
      """
      {"jsonrpc":"2.0","method":"notifications/initialized"}""";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private McpEnablementService enablementService;
  @Autowired private McpEgressConsentRepository consentRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID ownerMemberId;
  private String clientId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "MCP Enablement Test Org", null);
    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_owner", "owner@test.com", "Owner", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    clientId = TestEntityHelper.createCustomer(mockMvc, owner, "Acme Corp", "acme@test.com");
  }

  /** Each test starts from a known-disabled state: clear consent history and disable MCP. */
  @BeforeEach
  void resetToDisabled() {
    inTenant(
        () -> {
          enablementService.revoke();
          consentRepository.deleteAll();
        });
  }

  // ---- (1) disabled (no consent) → tools/call refuses, non-leaking -----------

  @Test
  void disabledTenantRefusesToolCallWithoutLeaking() throws Exception {
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    JsonNode result =
        callTool(owner, openSession(owner), "list_clients", "{\"page\":0,\"size\":50}");

    assertThat(result.at("/isError").asBoolean()).isTrue();
    JsonNode payload = resultPayload(result);
    assertThat(payload.at("/error").asString()).isEqualTo("not_enabled");
    String message = payload.at("/message").asString();
    // Non-leaking: no data, no member/matter/client existence disclosure, no stack trace.
    assertThat(message)
        .doesNotContainIgnoringCase("exception")
        .doesNotContainIgnoringCase("forbidden")
        .doesNotContain(clientId)
        .doesNotContain(ownerMemberId.toString());
  }

  // ---- (2) enabled + GRANTED → tools/call returns data -----------------------

  @Test
  void enabledAndGrantedTenantReturnsData() throws Exception {
    inTenant(() -> enablementService.enable(CONSENT_VERSION));

    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    JsonNode result =
        callTool(owner, openSession(owner), "list_clients", "{\"page\":0,\"size\":50}");

    assertThat(result.at("/isError").asBoolean()).isFalse();
    JsonNode payload = resultPayload(result);
    assertThat(payload.at("/items").isArray()).isTrue();
  }

  // ---- (3) consent REVOKED but integration enabled → refusal -----------------

  @Test
  void integrationEnabledButConsentRevokedRefuses() throws Exception {
    // Enable (consent GRANTED + integration enabled), then append a REVOKED consent only.
    inTenant(
        () -> {
          enablementService.enable(CONSENT_VERSION);
          // Append a REVOKED row without disabling the integration row, so only consent is absent.
          consentRepository.saveAndFlush(
              io.b2mash.b2b.b2bstrawman.mcp.consent.McpEgressConsent.revoke(
                  ownerMemberId, CONSENT_VERSION));
        });

    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    JsonNode result =
        callTool(owner, openSession(owner), "list_clients", "{\"page\":0,\"size\":50}");

    assertThat(result.at("/isError").asBoolean()).isTrue();
    assertThat(resultPayload(result).at("/error").asString()).isEqualTo("not_enabled");
  }

  // ---- (4) revoke takes effect on the very next call -------------------------

  @Test
  void revokeTakesEffectImmediately() throws Exception {
    inTenant(() -> enablementService.enable(CONSENT_VERSION));

    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    JsonNode enabled =
        callTool(owner, openSession(owner), "list_clients", "{\"page\":0,\"size\":50}");
    assertThat(enabled.at("/isError").asBoolean()).isFalse();

    inTenant(() -> enablementService.revoke());

    JsonNode refused =
        callTool(owner, openSession(owner), "list_clients", "{\"page\":0,\"size\":50}");
    assertThat(refused.at("/isError").asBoolean()).isTrue();
    assertThat(resultPayload(refused).at("/error").asString()).isEqualTo("not_enabled");
  }

  // ---- (5) consent history preserved through enable -> revoke -> re-enable ----

  @Test
  void consentHistoryIsPreservedThroughToggling() {
    inTenant(
        () -> {
          enablementService.enable(CONSENT_VERSION); // GRANTED
          enablementService.revoke(); // REVOKED
          enablementService.enable(CONSENT_VERSION); // GRANTED

          assertThat(consentRepository.count()).isGreaterThanOrEqualTo(3);
          var latest = consentRepository.findTopByOrderByConsentedAtDesc().orElseThrow();
          assertThat(latest.isGranted()).isTrue();
          assertThat(enablementService.effectiveState()).isTrue();
        });
  }

  // ---- (6) initialize succeeds even when disabled ----------------------------

  @Test
  void initializeSucceedsWhenDisabled() throws Exception {
    // resetToDisabled() leaves the tenant disabled. The handshake must still open a session.
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    String sessionId = openSession(owner);
    assertThat(sessionId).isNotNull();
  }

  // ---- helpers ---------------------------------------------------------------

  private void inTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(() -> transactionTemplate.executeWithoutResult(tx -> action.run()));
  }

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
    MvcResult result = mockMvc.perform(builder).andExpect(status().is2xxSuccessful()).andReturn();
    if (result.getRequest().isAsyncStarted()) {
      result =
          mockMvc.perform(asyncDispatch(result)).andExpect(status().is2xxSuccessful()).andReturn();
    }
    return result;
  }

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

  private String openSession(JwtRequestPostProcessor jwt) throws Exception {
    MvcResult init = mcpCall(INITIALIZE_BODY, jwt, null);
    String sessionId = init.getResponse().getHeader("Mcp-Session-Id");
    mcpCall(INITIALIZED_NOTIFICATION_BODY, jwt, sessionId);
    return sessionId;
  }

  private JsonNode callTool(JwtRequestPostProcessor jwt, String sessionId, String name, String args)
      throws Exception {
    String body =
        """
        {"jsonrpc":"2.0","id":99,"method":"tools/call","params":{"name":"%s","arguments":%s}}"""
            .formatted(name, args);
    MvcResult res = mcpCall(body, jwt, sessionId);
    return parseRpc(res.getResponse().getContentAsString()).at("/result");
  }

  private JsonNode resultPayload(JsonNode result) {
    String text = result.at("/content/0/text").asString();
    return objectMapper.readTree(text);
  }
}
