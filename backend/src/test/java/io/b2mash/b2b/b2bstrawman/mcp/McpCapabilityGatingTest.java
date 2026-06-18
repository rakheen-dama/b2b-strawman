package io.b2mash.b2b.b2bstrawman.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Epic 567B.2 — capability-gating per regime (§11.6, ADR-305). Proves the MCP capability surface is
 * EXACTLY the web capability surface and never widens: each tool/resource enforces the same
 * capability the web app does, granted via a custom org-role with a precise capability set.
 *
 * <ol>
 *   <li>MCP_ACCESS without VIEW_TRUST → {@code get_trust_balance} forbidden, but {@code
 *       list_matters} (project-access, no capability) allowed.
 *   <li>MCP_ACCESS without AI_MANAGE → {@code kazi://firm-profile} resource forbidden [D1].
 *   <li>INVOICING enforced on {@code list_invoices}; granting it flips forbidden → allowed.
 *   <li>CUSTOMER_MANAGEMENT enforced on {@code list_compliance_gaps}.
 *   <li>TEAM_OVERSIGHT enforced on {@code get_audit_events}.
 *   <li>project-access: a non-member calling {@code get_matter} on an owner-only matter →
 *       non-leaking {@code not_found} (no widening to a global read).
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McpCapabilityGatingTest {

  private static final String ORG_ID = "org_mcp_567_capgate";
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
  @Autowired private TransactionTemplate transactionTemplate;

  private String ownerOnlyMatterId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "MCP 567 CapGate Org", null);
    UUID ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_owner", "owner@test.com", "Owner", "owner"));
    String tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> enablementService.enable("popia-egress-v1")));

    // An owner-only matter (no member assigned) for the project-access regime.
    ownerOnlyMatterId =
        TestEntityHelper.createProject(
            mockMvc, TestJwtFactory.ownerJwt(ORG_ID, "user_owner"), "Owner-Only Matter");
  }

  // ---- (1) MCP_ACCESS without VIEW_TRUST: trust denied, matters allowed ------

  @Test
  void withoutViewTrustTrustIsDeniedButMattersAllowed() throws Exception {
    JwtRequestPostProcessor jwt =
        memberWithCaps("567cap-no-trust", "user_567_notrust", Set.of("MCP_ACCESS"));
    String session = openSession(jwt);

    // get_trust_balance requires VIEW_TRUST → forbidden.
    JsonNode trust =
        callTool(
            jwt,
            session,
            "get_trust_balance",
            "{\"trustAccountId\":\"%s\"}".formatted(UUID.randomUUID()));
    assertThat(trust.at("/isError").asBoolean()).isTrue();
    assertThat(payload(trust).at("/error").asString()).isEqualTo("forbidden");

    // list_matters carries NO capability gate (project-access only) → allowed.
    JsonNode matters = callTool(jwt, session, "list_matters", "{\"page\":0,\"size\":50}");
    assertThat(matters.at("/isError").asBoolean(false))
        .as("list_matters must not be capability-gated")
        .isFalse();
  }

  // ---- (2) MCP_ACCESS without AI_MANAGE: firm-profile resource denied --------

  @Test
  void withoutAiManageFirmProfileResourceIsDenied() throws Exception {
    JwtRequestPostProcessor jwt =
        memberWithCaps("567cap-no-aimanage", "user_567_noai", Set.of("MCP_ACCESS"));
    JsonNode read = readResource(jwt, openSession(jwt), "kazi://firm-profile");
    assertThat(resourcePayload(read).at("/error").asString()).isEqualTo("forbidden");
  }

  // ---- (3) INVOICING enforced on list_invoices -------------------------------

  @Test
  void invoicingIsEnforcedOnListInvoices() throws Exception {
    // Without INVOICING → forbidden.
    JwtRequestPostProcessor noInv =
        memberWithCaps("567cap-no-inv", "user_567_noinv", Set.of("MCP_ACCESS"));
    JsonNode denied =
        callTool(noInv, openSession(noInv), "list_invoices", "{\"page\":0,\"size\":50}");
    assertThat(denied.at("/isError").asBoolean()).isTrue();
    assertThat(payload(denied).at("/error").asString()).isEqualTo("forbidden");

    // With INVOICING → allowed (same scope as the web app — no widening, no narrowing).
    JwtRequestPostProcessor withInv =
        memberWithCaps("567cap-inv", "user_567_inv", Set.of("MCP_ACCESS", "INVOICING"));
    JsonNode ok =
        callTool(withInv, openSession(withInv), "list_invoices", "{\"page\":0,\"size\":50}");
    assertThat(ok.at("/isError").asBoolean(false)).isFalse();
  }

  // ---- (4) CUSTOMER_MANAGEMENT enforced on list_compliance_gaps --------------

  @Test
  void customerManagementIsEnforcedOnComplianceGaps() throws Exception {
    JwtRequestPostProcessor noCm =
        memberWithCaps("567cap-no-cm", "user_567_nocm", Set.of("MCP_ACCESS"));
    JsonNode denied =
        callTool(
            noCm,
            openSession(noCm),
            "list_compliance_gaps",
            "{\"customerId\":\"%s\"}".formatted(UUID.randomUUID()));
    assertThat(denied.at("/isError").asBoolean()).isTrue();
    assertThat(payload(denied).at("/error").asString()).isEqualTo("forbidden");
  }

  // ---- (5) TEAM_OVERSIGHT enforced on get_audit_events -----------------------

  @Test
  void teamOversightIsEnforcedOnAuditEvents() throws Exception {
    // Without TEAM_OVERSIGHT → forbidden.
    JwtRequestPostProcessor noTo =
        memberWithCaps("567cap-no-to", "user_567_noto", Set.of("MCP_ACCESS"));
    JsonNode denied =
        callTool(noTo, openSession(noTo), "get_audit_events", "{\"page\":0,\"size\":50}");
    assertThat(denied.at("/isError").asBoolean()).isTrue();
    assertThat(payload(denied).at("/error").asString()).isEqualTo("forbidden");

    // With TEAM_OVERSIGHT → allowed.
    JwtRequestPostProcessor withTo =
        memberWithCaps("567cap-to", "user_567_to", Set.of("MCP_ACCESS", "TEAM_OVERSIGHT"));
    JsonNode ok =
        callTool(withTo, openSession(withTo), "get_audit_events", "{\"page\":0,\"size\":50}");
    assertThat(ok.at("/isError").asBoolean(false)).isFalse();
  }

  // ---- (6) project-access non-member → non-leaking not_found -----------------

  @Test
  void projectAccessNonMemberGetsNotFound() throws Exception {
    JwtRequestPostProcessor nonMember =
        memberWithCaps("567cap-pa", "user_567_pa", Set.of("MCP_ACCESS"));
    JsonNode result =
        callTool(
            nonMember,
            openSession(nonMember),
            "get_matter",
            "{\"id\":\"%s\"}".formatted(ownerOnlyMatterId));
    assertThat(result.at("/isError").asBoolean()).isTrue();
    JsonNode payload = payload(result);
    assertThat(payload.at("/error").asString()).isEqualTo("not_found");
    // No widening: the error must not echo the id or leak "forbidden".
    assertThat(payload.at("/message").asString())
        .doesNotContain(ownerOnlyMatterId)
        .doesNotContainIgnoringCase("forbidden");
  }

  // ---- role / member helpers -------------------------------------------------

  /** Creates a custom org-role with exactly {@code caps}, a fresh member, assigns the role, JWT. */
  private JwtRequestPostProcessor memberWithCaps(String roleName, String subject, Set<String> caps)
      throws Exception {
    String roleId = createCustomRole(roleName, caps);
    String memberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, subject, subject + "@test.com", roleName, "member");
    assignRole(memberId, roleId);
    return TestJwtFactory.memberJwt(ORG_ID, subject);
  }

  private String createCustomRole(String name, Set<String> capabilities) throws Exception {
    var body =
        """
        {"name":"%s","description":"Test role","capabilities":[%s]}"""
            .formatted(
                name,
                capabilities.stream()
                    .map(c -> "\"" + c + "\"")
                    .reduce((a, b) -> a + "," + b)
                    .orElse(""));
    var result =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void assignRole(String memberId, String roleId) throws Exception {
    mockMvc
        .perform(
            put("/api/members/" + memberId + "/role")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgRoleId\":\"%s\",\"capabilityOverrides\":[]}".formatted(roleId)))
        .andExpect(status().isOk());
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

  private JsonNode readResource(JwtRequestPostProcessor jwt, String sessionId, String uri)
      throws Exception {
    String body =
        """
        {"jsonrpc":"2.0","id":98,"method":"resources/read","params":{"uri":"%s"}}"""
            .formatted(uri);
    MvcResult res = mcpCall(body, jwt, sessionId);
    return parseRpc(res.getResponse().getContentAsString()).at("/result");
  }

  private JsonNode payload(JsonNode result) {
    return objectMapper.readTree(result.at("/content/0/text").asString());
  }

  private JsonNode resourcePayload(JsonNode result) {
    return objectMapper.readTree(result.at("/contents/0/text").asString());
  }
}
