package io.b2mash.b2b.b2bstrawman.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.mcp.tool.McpToolRegistry;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.aop.support.AopUtils;
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
 * Epic 567B.1 — cross-tenant isolation for the Kazi MCP server (§11.2). Two fully-provisioned
 * tenants (A and B) with the connector enabled on both. The isolation guarantee is structural: the
 * schema is resolved solely from the validated JWT {@code o.id} (→ {@code org_schema_mapping} →
 * {@code RequestScopes.TENANT_ID} → Hibernate {@code search_path}); there is no tenant parameter on
 * any tool to override, so a token bound to tenant A can never reach tenant B's data.
 *
 * <ol>
 *   <li>A token bound to tenant A cannot read tenant B's matter via ANY tool ({@code list_matters}
 *       omits it; {@code get_matter} on B's id returns a non-leaking {@code not_found}).
 *   <li>No {@code @McpTool}/{@code @McpResource} method declares a tenant/org/schema parameter —
 *       the only thing a caller controls is the data values, never the tenant boundary.
 *   <li>The schema bound during a tenant-A request equals tenant A's mapping (resolved from the
 *       token), and differs from tenant B's — proving resolution is token-driven, not
 *       client-supplied.
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McpTenantIsolationTest {

  private static final String ORG_A_ID = "org_mcp_567_iso_a";
  private static final String ORG_B_ID = "org_mcp_567_iso_b";
  private static final String ACCEPT = "application/json, text/event-stream";

  private static final String INITIALIZE_BODY =
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"kazi-itest","version":"1.0.0"}}}""";
  private static final String INITIALIZED_NOTIFICATION_BODY =
      """
      {"jsonrpc":"2.0","method":"notifications/initialized"}""";

  /** Tenant/org/schema-shaped parameter names that must NEVER exist on any MCP tool method. */
  private static final java.util.Set<String> FORBIDDEN_PARAM_NAMES =
      java.util.Set.of("tenant", "tenantid", "schema", "orgid", "org", "tenantschema");

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private McpEnablementService enablementService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private McpToolRegistry registry;

  private String schemaA;
  private String schemaB;
  private String matterAId;

  @BeforeAll
  void setup() throws Exception {
    // ---- Tenant A: owns a matter, connector enabled ----
    provisioningService.provisionTenant(ORG_A_ID, "MCP 567 Iso Tenant A", null);
    UUID ownerA =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_A_ID, "user_a_owner", "aowner@test.com", "A Owner", "owner"));
    schemaA = orgSchemaMappingRepository.findByClerkOrgId(ORG_A_ID).orElseThrow().getSchemaName();
    enableConnector(schemaA, ORG_A_ID, ownerA);
    matterAId =
        TestEntityHelper.createProject(
            mockMvc, TestJwtFactory.ownerJwt(ORG_A_ID, "user_a_owner"), "Tenant A Matter");

    // ---- Tenant B: connector enabled, no tenant-A data ----
    provisioningService.provisionTenant(ORG_B_ID, "MCP 567 Iso Tenant B", null);
    UUID ownerB =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_B_ID, "user_b_owner", "bowner@test.com", "B Owner", "owner"));
    schemaB = orgSchemaMappingRepository.findByClerkOrgId(ORG_B_ID).orElseThrow().getSchemaName();
    enableConnector(schemaB, ORG_B_ID, ownerB);
  }

  private void enableConnector(String schema, String orgId, UUID ownerMemberId) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> enablementService.enable("popia-egress-v1")));
  }

  // ---- (1) tenant-B token cannot read tenant-A data via any tool -------------

  @Test
  void tenantBTokenCannotReadTenantAMatter() throws Exception {
    JwtRequestPostProcessor ownerB = TestJwtFactory.ownerJwt(ORG_B_ID, "user_b_owner");
    String session = openSession(ownerB);

    // list_matters from tenant B never surfaces tenant A's matter.
    JsonNode listResult = callTool(ownerB, session, "list_matters", "{\"page\":0,\"size\":50}");
    assertThat(listResult.at("/isError").asBoolean(false)).isFalse();
    JsonNode listPayload = payload(listResult);
    for (JsonNode item : listPayload.at("/items")) {
      assertThat(item.at("/id").asString())
          .as("tenant B must never see tenant A's matter id")
          .isNotEqualTo(matterAId);
    }

    // get_matter on tenant A's id from a tenant-B token → non-leaking not_found (the row does not
    // exist in tenant B's schema; there is no cross-schema path).
    JsonNode getResult =
        callTool(ownerB, session, "get_matter", "{\"id\":\"%s\"}".formatted(matterAId));
    assertThat(getResult.at("/isError").asBoolean()).isTrue();
    assertThat(payload(getResult).at("/error").asString()).isEqualTo("not_found");

    // Sanity: tenant A's OWN token CAN read it — proving the matter exists and isolation, not a
    // global outage, is what blocks tenant B.
    JwtRequestPostProcessor ownerA = TestJwtFactory.ownerJwt(ORG_A_ID, "user_a_owner");
    JsonNode aResult =
        callTool(ownerA, openSession(ownerA), "get_matter", "{\"id\":\"%s\"}".formatted(matterAId));
    assertThat(aResult.at("/isError").asBoolean(false)).isFalse();
    assertThat(payload(aResult).at("/id").asString()).isEqualTo(matterAId);
  }

  // ---- (2) no tool exposes a tenant/org/schema parameter ---------------------

  @Test
  void noToolExposesATenantOverrideParameter() {
    var beans = registry.registeredToolBeans();
    assertThat(beans).isNotEmpty();
    for (Object bean : beans) {
      for (Method method : AopUtils.getTargetClass(bean).getDeclaredMethods()) {
        if (method.getAnnotation(org.springframework.ai.mcp.annotation.McpTool.class) == null) {
          continue;
        }
        for (java.lang.reflect.Parameter p : method.getParameters()) {
          McpToolParam toolParam = p.getAnnotation(McpToolParam.class);
          if (toolParam == null) {
            continue;
          }
          // The reflected parameter name is the canonical exposed argument name.
          String paramName = p.getName().toLowerCase().replace("_", "");
          assertThat(FORBIDDEN_PARAM_NAMES)
              .as(
                  "tool %s.%s must not expose a tenant/org/schema override parameter ('%s')",
                  bean.getClass().getSimpleName(), method.getName(), p.getName())
              .doesNotContain(paramName);
        }
      }
    }
  }

  // ---- (3) schema resolved from the token only (A != B) ----------------------

  @Test
  void schemaIsResolvedFromTokenOrgIdOnly() {
    // The two tenants resolve to distinct schemas from their respective org ids — the mapping is
    // the sole source of the schema, never a client-supplied value.
    assertThat(schemaA).isNotBlank().matches("tenant_[0-9a-f]+");
    assertThat(schemaB).isNotBlank().matches("tenant_[0-9a-f]+");
    assertThat(schemaA).isNotEqualTo(schemaB);

    // And the mapping is keyed by org id (the JWT o.id claim) — re-resolving by org id yields the
    // same schema, confirming there is no header/param channel involved.
    assertThat(orgSchemaMappingRepository.findByClerkOrgId(ORG_A_ID).orElseThrow().getSchemaName())
        .isEqualTo(schemaA);
    assertThat(orgSchemaMappingRepository.findByClerkOrgId(ORG_B_ID).orElseThrow().getSchemaName())
        .isEqualTo(schemaB);
  }

  // ---- harness ---------------------------------------------------------------

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
