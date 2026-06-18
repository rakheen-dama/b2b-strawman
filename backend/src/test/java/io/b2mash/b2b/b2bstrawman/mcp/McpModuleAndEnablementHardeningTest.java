package io.b2mash.b2b.b2bstrawman.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.ArrayList;
import java.util.UUID;
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
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Epic 567B.5 — module-gating tolerance + enablement/revoke refusal (§11.5, §11.7). Three tenants:
 *
 * <ul>
 *   <li><b>LEGAL_ENABLED</b> — trust module on, connector enabled+consented (the happy path + the
 *       revoke-then-refuse flow).
 *   <li><b>NON_LEGAL</b> — connector enabled+consented but trust module OFF (module_disabled
 *       tolerance).
 *   <li><b>NEVER_ENABLED</b> — provisioned but connector never enabled (not_enabled refusal).
 * </ul>
 *
 * <ol>
 *   <li>a non-legal tenant calling {@code get_trust_balance} → clean {@code module_disabled} (no
 *       stack trace / "Exception").
 *   <li>a tenant that never enabled the connector → non-leaking {@code not_enabled} refusal.
 *   <li>after {@code revoke()} the very next call is refused with {@code not_enabled} (no caching).
 *   <li>an enabled + consented tenant returns data.
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McpModuleAndEnablementHardeningTest {

  private static final String LEGAL_ORG_ID = "org_mcp_567_legal";
  private static final String NON_LEGAL_ORG_ID = "org_mcp_567_nonlegal";
  private static final String NEVER_ENABLED_ORG_ID = "org_mcp_567_neverenabled";
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
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String legalSchema;
  private UUID legalOwnerId;

  @BeforeAll
  void setup() throws Exception {
    // ---- LEGAL_ENABLED: trust module + connector ----
    provisioningService.provisionTenant(LEGAL_ORG_ID, "MCP 567 Legal Org", null);
    legalOwnerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                LEGAL_ORG_ID,
                "user_legal_owner",
                "legal@test.com",
                "Legal Owner",
                "owner"));
    legalSchema =
        orgSchemaMappingRepository.findByClerkOrgId(LEGAL_ORG_ID).orElseThrow().getSchemaName();
    ScopedValue.where(RequestScopes.TENANT_ID, legalSchema)
        .where(RequestScopes.ORG_ID, LEGAL_ORG_ID)
        .where(RequestScopes.MEMBER_ID, legalOwnerId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      var modules = new ArrayList<>(settings.getEnabledModules());
                      if (!modules.contains("trust_accounting")) {
                        modules.add("trust_accounting");
                      }
                      settings.setEnabledModules(modules);
                      orgSettingsRepository.save(settings);
                      enablementService.enable("popia-egress-v1");
                    }));

    // ---- NON_LEGAL: connector enabled, NO trust module ----
    provisioningService.provisionTenant(NON_LEGAL_ORG_ID, "MCP 567 NonLegal Org", null);
    UUID nlOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, NON_LEGAL_ORG_ID, "user_nl_owner", "nl@test.com", "NL Owner", "owner"));
    String nlSchema =
        orgSchemaMappingRepository.findByClerkOrgId(NON_LEGAL_ORG_ID).orElseThrow().getSchemaName();
    ScopedValue.where(RequestScopes.TENANT_ID, nlSchema)
        .where(RequestScopes.ORG_ID, NON_LEGAL_ORG_ID)
        .where(RequestScopes.MEMBER_ID, nlOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> enablementService.enable("popia-egress-v1")));

    // ---- NEVER_ENABLED: provisioned, connector never enabled ----
    provisioningService.provisionTenant(NEVER_ENABLED_ORG_ID, "MCP 567 NeverEnabled Org", null);
    TestMemberHelper.syncMember(
        mockMvc, NEVER_ENABLED_ORG_ID, "user_ne_owner", "ne@test.com", "NE Owner", "owner");
  }

  // ---- (1) module disabled: clean, no stack trace ----------------------------

  @Test
  void nonLegalTenantGetsCleanModuleDisabled() throws Exception {
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(NON_LEGAL_ORG_ID, "user_nl_owner");
    JsonNode result =
        callTool(
            owner,
            openSession(owner),
            "get_trust_balance",
            "{\"trustAccountId\":\"%s\"}".formatted(UUID.randomUUID()));
    assertThat(result.at("/isError").asBoolean()).isTrue();
    JsonNode payload = payload(result);
    assertThat(payload.at("/error").asString()).isEqualTo("module_disabled");
    // Clean refusal — no leaked internals.
    assertThat(payload.at("/message").asString())
        .doesNotContain("Exception")
        .doesNotContainIgnoringCase("stack")
        .doesNotContain("io.b2mash");
  }

  // ---- (2) connector never enabled: non-leaking not_enabled ------------------

  @Test
  void neverEnabledTenantIsRefusedWithNotEnabled() throws Exception {
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(NEVER_ENABLED_ORG_ID, "user_ne_owner");
    JsonNode result =
        callTool(owner, openSession(owner), "list_matters", "{\"page\":0,\"size\":50}");
    assertThat(result.at("/isError").asBoolean()).isTrue();
    JsonNode payload = payload(result);
    assertThat(payload.at("/error").asString()).isEqualTo("not_enabled");
    assertThat(payload.at("/message").asString())
        .doesNotContain("Exception")
        .doesNotContain("io.b2mash");
  }

  // ---- (3) revoke takes effect on the next call ------------------------------

  @Test
  void revokeRefusesTheVeryNextCall() throws Exception {
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_legal_owner");

    // Self-contained: ensure enabled at the start (does not rely on @BeforeAll surviving an earlier
    // method), and ALWAYS re-enable in a finally so a mid-test failure cannot leave the tenant
    // revoked for any other method (JUnit 5 does not guarantee method order).
    setLegalConnectorEnabled(true);
    try {
      // Before revoke: a list tool succeeds (connector enabled + consented).
      JsonNode before =
          callTool(owner, openSession(owner), "list_clients", "{\"page\":0,\"size\":50}");
      assertThat(before.at("/isError").asBoolean(false))
          .as("connector enabled before revoke")
          .isFalse();

      // Revoke (disables integration + appends REVOKED consent). effectiveState() is not cached.
      setLegalConnectorEnabled(false);

      // Next call is refused with not_enabled.
      JsonNode after =
          callTool(owner, openSession(owner), "list_clients", "{\"page\":0,\"size\":50}");
      assertThat(after.at("/isError").asBoolean()).isTrue();
      assertThat(payload(after).at("/error").asString()).isEqualTo("not_enabled");
    } finally {
      // Restore enabled state regardless of outcome — order-independent isolation.
      setLegalConnectorEnabled(true);
    }
  }

  // ---- (4) enabled + consented returns data ----------------------------------

  @Test
  void enabledAndConsentedTenantReturnsData() throws Exception {
    // Set up its own state: enable the LEGAL connector explicitly rather than depending on the
    // revoke test having re-enabled it (method order is not guaranteed).
    setLegalConnectorEnabled(true);
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_legal_owner");
    JsonNode result =
        callTool(owner, openSession(owner), "list_clients", "{\"page\":0,\"size\":50}");
    assertThat(result.at("/isError").asBoolean(false)).isFalse();
    // A real (possibly empty) page is returned — not an error envelope.
    assertThat(payload(result).has("items")).isTrue();
  }

  /** Enable (consent + integration) or revoke the LEGAL tenant's MCP connector. Idempotent. */
  private void setLegalConnectorEnabled(boolean enabled) {
    ScopedValue.where(RequestScopes.TENANT_ID, legalSchema)
        .where(RequestScopes.ORG_ID, LEGAL_ORG_ID)
        .where(RequestScopes.MEMBER_ID, legalOwnerId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      if (enabled) {
                        enablementService.enable("popia-egress-v1");
                      } else {
                        enablementService.revoke();
                      }
                    }));
  }

  // ---- /mcp harness ----------------------------------------------------------

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

  private JsonNode payload(JsonNode result) {
    return objectMapper.readTree(result.at("/content/0/text").asString());
  }
}
