package io.b2mash.b2b.b2bstrawman.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration tests for the Epic 564B compliance + audit MCP tools and the {@code
 * kazi://firm-profile} resource, driving the real {@code /mcp} JSON-RPC endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogueBatch2ComplianceAuditTest {

  private static final String ORG_ID = "org_mcp_batch2_compliance";
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
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private McpEnablementService enablementService;
  @Autowired private TransactionTemplate transactionTemplate;

  private String clientId;
  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "MCP Batch2 Compliance Org", null);
    UUID ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_owner", "owner@test.com", "Owner", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // 565B: enable the MCP connector so the catalogue tools/resource pass the effective-state gate.
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> enablementService.enable("popia-egress-v1")));

    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    clientId = TestEntityHelper.createCustomer(mockMvc, owner, "Compliance Client", "cc@test.com");
    // Generate a handful of audit events (customer + project creation) so the firm-wide audit feed
    // has multiple rows to assert ordering on.
    TestEntityHelper.createProject(mockMvc, owner, "Audit Matter A");
    TestEntityHelper.createProject(mockMvc, owner, "Audit Matter B");
  }

  // ---- JSON-RPC harness helpers ---------------------------------------------

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

  private JsonNode rpc(JwtRequestPostProcessor jwt, String sessionId, String method)
      throws Exception {
    String body =
        """
        {"jsonrpc":"2.0","id":97,"method":"%s","params":{}}"""
            .formatted(method);
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

  private JsonNode resultPayload(JsonNode result) {
    return objectMapper.readTree(result.at("/content/0/text").asString());
  }

  private JsonNode resourcePayload(JsonNode result) {
    return objectMapper.readTree(result.at("/contents/0/text").asString());
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

  // ---- Tests ----------------------------------------------------------------

  @Test
  void listComplianceGapsCustomerManagementGate() throws Exception {
    // Owner (has CUSTOMER_MANAGEMENT) succeeds.
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    JsonNode ok =
        callTool(
            owner,
            openSession(owner),
            "list_compliance_gaps",
            "{\"customerId\":\"%s\"}".formatted(clientId));
    assertThat(ok.at("/isError").asBoolean(false)).isFalse();
    JsonNode payload = resultPayload(ok);
    assertThat(payload.at("/customerId").asString()).isEqualTo(clientId);
    assertThat(payload.has("ficaStatus")).isTrue();
    assertThat(payload.at("/items").isArray()).isTrue();
    assertThat(payload.at("/truncated").asBoolean()).isFalse();

    // Member without CUSTOMER_MANAGEMENT is forbidden.
    String roleId = createCustomRole("mcp-only-no-cm", Set.of("MCP_ACCESS", "INVOICING"));
    String memberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_deny_cm", "denycm@test.com", "Deny CM", "member");
    assignRole(memberId, roleId);
    JwtRequestPostProcessor member = TestJwtFactory.memberJwt(ORG_ID, "user_deny_cm");
    JsonNode denied =
        callTool(
            member,
            openSession(member),
            "list_compliance_gaps",
            "{\"customerId\":\"%s\"}".formatted(clientId));
    assertThat(denied.at("/isError").asBoolean()).isTrue();
    assertThat(resultPayload(denied).at("/error").asString()).isEqualTo("forbidden");
  }

  @Test
  void getAuditEventsTeamOversightGateOrderingAndMaxSize() throws Exception {
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    JsonNode result =
        callTool(owner, openSession(owner), "get_audit_events", "{\"page\":0,\"size\":500}");
    assertThat(result.at("/isError").asBoolean(false)).isFalse();
    JsonNode payload = resultPayload(result);
    // size 500 clamps to the audit max of 200.
    assertThat(payload.at("/size").asInt()).isEqualTo(200);

    JsonNode items = payload.at("/items");
    assertThat(items.isArray()).isTrue();
    assertThat(items.size()).isGreaterThanOrEqualTo(2);
    // Fixed occurredAt DESC ordering — first item is newest.
    String first = items.get(0).at("/occurredAt").asString();
    String second = items.get(1).at("/occurredAt").asString();
    assertThat(first.compareTo(second)).isGreaterThanOrEqualTo(0);

    // Member without TEAM_OVERSIGHT is forbidden.
    String roleId = createCustomRole("mcp-only-no-oversight", Set.of("MCP_ACCESS", "INVOICING"));
    String memberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_deny_oversight", "denyov@test.com", "Deny Ov", "member");
    assignRole(memberId, roleId);
    JwtRequestPostProcessor member = TestJwtFactory.memberJwt(ORG_ID, "user_deny_oversight");
    JsonNode denied =
        callTool(member, openSession(member), "get_audit_events", "{\"page\":0,\"size\":50}");
    assertThat(denied.at("/isError").asBoolean()).isTrue();
    assertThat(resultPayload(denied).at("/error").asString()).isEqualTo("forbidden");
  }

  @Test
  void firmProfileResourceWithAiManageReturnsAllowedFields() throws Exception {
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    JsonNode result = readResource(owner, openSession(owner), "kazi://firm-profile");
    JsonNode payload = resourcePayload(result);
    assertThat(payload.has("practiceAreas")).isTrue();
    assertThat(payload.has("jurisdiction")).isTrue();
    assertThat(payload.has("riskCalibration")).isTrue();
    assertThat(payload.has("houseStyleNotes")).isTrue();
    assertThat(payload.has("feeEstimationNotes")).isTrue();
  }

  @Test
  void firmProfileResourceDeniedForMcpAccessOnlyMember() throws Exception {
    String roleId = createCustomRole("mcp-only-no-ai", Set.of("MCP_ACCESS", "CUSTOMER_MANAGEMENT"));
    String memberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_deny_ai", "denyai@test.com", "Deny AI", "member");
    assignRole(memberId, roleId);
    JwtRequestPostProcessor member = TestJwtFactory.memberJwt(ORG_ID, "user_deny_ai");
    JsonNode result = readResource(member, openSession(member), "kazi://firm-profile");
    JsonNode payload = resourcePayload(result);
    assertThat(payload.at("/error").asString()).isEqualTo("forbidden");

    // The resource denial path must emit mcp.access.denied, like the tool denial paths.
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var page =
                  auditEventRepository.findByFilter(
                      null, null, null, "mcp.access.denied", null, null, PageRequest.of(0, 50));
              assertThat(page.getContent())
                  .anySatisfy(
                      event -> {
                        assertThat(event.getEventType()).isEqualTo("mcp.access.denied");
                        assertThat(event.getDetails()).containsEntry("tool", "kazi://firm-profile");
                      });
            });
  }

  @Test
  void firmProfileResourceExcludesBudgetKeyModelFields() throws Exception {
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    JsonNode result = readResource(owner, openSession(owner), "kazi://firm-profile");
    JsonNode payload = resourcePayload(result);
    assertThat(payload.has("monthlyBudgetCents")).isFalse();
    assertThat(payload.has("preferredModel")).isFalse();
    assertThat(payload.has("ficaRequirements")).isFalse();
    assertThat(payload.has("profileVersion")).isFalse();
    assertThat(payload.has("coldStartCompleted")).isFalse();
  }

  @Test
  void resourcesListContainsFirmProfileAndTemplatesContainMatterAndClient() throws Exception {
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    String session = openSession(owner);

    JsonNode list = rpc(owner, session, "resources/list");
    JsonNode resources = list.at("/resources");
    assertThat(resources.isArray()).isTrue();
    boolean hasFirmProfile = false;
    for (JsonNode r : resources) {
      if ("kazi://firm-profile".equals(r.at("/uri").asString())) {
        hasFirmProfile = true;
      }
    }
    assertThat(hasFirmProfile).as("resources/list contains kazi://firm-profile").isTrue();

    JsonNode templates = rpc(owner, session, "resources/templates/list");
    JsonNode templateList = templates.at("/resourceTemplates");
    assertThat(templateList.isArray()).isTrue();
    boolean hasMatter = false;
    boolean hasClient = false;
    for (JsonNode t : templateList) {
      String uri = t.at("/uriTemplate").asString();
      if (uri.contains("kazi://matter/")) {
        hasMatter = true;
      }
      if (uri.contains("kazi://client/")) {
        hasClient = true;
      }
    }
    assertThat(hasMatter).as("templates contain matter").isTrue();
    assertThat(hasClient).as("templates contain client").isTrue();
  }
}
