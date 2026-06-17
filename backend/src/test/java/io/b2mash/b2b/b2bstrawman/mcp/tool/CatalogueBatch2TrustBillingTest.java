package io.b2mash.b2b.b2bstrawman.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.mcp.McpEnablementService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
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
 * Integration tests for the Epic 564A trust + billing MCP tools, driving the real {@code /mcp}
 * JSON-RPC endpoint. Uses a legal tenant (trust module enabled) plus a non-legal tenant to exercise
 * the module-disabled path.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogueBatch2TrustBillingTest {

  private static final String ORG_ID = "org_mcp_batch2_legal";
  private static final String NON_LEGAL_ORG_ID = "org_mcp_batch2_nonlegal";
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
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private McpEnablementService enablementService;

  private String tenantSchema;
  private String trustAccountId;
  private String customerId;
  private String invoiceClientId;
  private String invoiceId;
  private String matterId;

  @BeforeAll
  void setup() throws Exception {
    // ---- Legal tenant: trust module enabled ----
    provisioningService.provisionTenant(ORG_ID, "MCP Batch2 Legal Org", null);
    UUID ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_owner", "owner@test.com", "Owner", "owner"));
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_member", "member@test.com", "Member", "member");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      var modules = new java.util.ArrayList<>(settings.getEnabledModules());
                      if (!modules.contains("trust_accounting")) {
                        modules.add("trust_accounting");
                      }
                      settings.setEnabledModules(modules);
                      orgSettingsRepository.save(settings);

                      // 565B: enable the MCP connector so the catalogue tools pass the
                      // effective-state gate (consent recorded + integration enabled).
                      enablementService.enable("popia-egress-v1");

                      // Invoice creation needs an ACTIVE customer with billing prerequisite
                      // fields — the API helper only makes a bare PROSPECT, which fails 422.
                      var invoiceCustomer =
                          TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                              "Invoice Client", "inv@test.com", ownerMemberId);
                      invoiceClientId = customerRepository.save(invoiceCustomer).getId().toString();
                    }));

    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");

    // Trust account + funded ledger card
    MvcResult accountResult =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"accountName":"Test Trust Account","bankName":"Test Bank","branchCode":"250655",
                         "accountNumber":"62000000002","accountType":"GENERAL","isPrimary":false,
                         "requireDualApproval":false,"openedDate":"2026-01-15"}"""))
            .andExpect(status().isCreated())
            .andReturn();
    trustAccountId = TestEntityHelper.extractId(accountResult);
    customerId = TestEntityHelper.createCustomer(mockMvc, owner, "Trust Customer", "tc@test.com");
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"customerId":"%s","amount":25000.00,"reference":"DEP-SETUP",
                     "description":"Setup deposit","transactionDate":"2026-03-01"}"""
                        .formatted(customerId)))
        .andExpect(status().isCreated());

    // Invoice for billing tools (invoiceClientId seeded above as an ACTIVE customer)
    matterId = TestEntityHelper.createProject(mockMvc, owner, "Billing Matter");
    MvcResult invoiceResult =
        mockMvc
            .perform(
                post("/api/invoices")
                    .with(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"customerId":"%s","currency":"ZAR","dueDate":"2026-03-31"}"""
                            .formatted(invoiceClientId)))
            .andExpect(status().isCreated())
            .andReturn();
    invoiceId = TestEntityHelper.extractId(invoiceResult);

    // ---- Non-legal tenant: trust module NOT enabled ----
    provisioningService.provisionTenant(NON_LEGAL_ORG_ID, "MCP Batch2 NonLegal Org", null);
    UUID nlOwnerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                NON_LEGAL_ORG_ID,
                "user_nl_owner",
                "nlowner@test.com",
                "NL Owner",
                "owner"));
    String nonLegalSchema =
        orgSchemaMappingRepository.findByClerkOrgId(NON_LEGAL_ORG_ID).orElseThrow().getSchemaName();
    // 565B: enable the MCP connector on the non-legal tenant too, so the trust tool reaches its
    // module-disabled check (not the connector-not-enabled gate).
    ScopedValue.where(RequestScopes.TENANT_ID, nonLegalSchema)
        .where(RequestScopes.ORG_ID, NON_LEGAL_ORG_ID)
        .where(RequestScopes.MEMBER_ID, nlOwnerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> enablementService.enable("popia-egress-v1")));
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

  private JsonNode resultPayload(JsonNode result) {
    return objectMapper.readTree(result.at("/content/0/text").asString());
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
  void getTrustBalanceWithViewTrustReturnsBalance() throws Exception {
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    JsonNode result =
        callTool(
            owner,
            openSession(owner),
            "get_trust_balance",
            "{\"trustAccountId\":\"%s\",\"customerId\":\"%s\"}"
                .formatted(trustAccountId, customerId));
    assertThat(result.at("/isError").asBoolean(false)).isFalse();
    JsonNode payload = resultPayload(result);
    assertThat(payload.has("balanceMinor")).isTrue();
    assertThat(payload.at("/balanceMinor").asLong()).isEqualTo(2_500_000L);
    assertThat(payload.at("/currency").asString()).isNotBlank();
  }

  @Test
  void getTrustBalanceWithoutViewTrustIsForbiddenAndAudited() throws Exception {
    String roleId =
        createCustomRole("mcp-only-trust-deny", Set.of("MCP_ACCESS", "CUSTOMER_MANAGEMENT"));
    String memberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_deny_trust", "denytrust@test.com", "Deny Trust", "member");
    assignRole(memberId, roleId);

    JwtRequestPostProcessor member = TestJwtFactory.memberJwt(ORG_ID, "user_deny_trust");
    JsonNode result =
        callTool(
            member,
            openSession(member),
            "get_trust_balance",
            "{\"trustAccountId\":\"%s\",\"customerId\":\"%s\"}"
                .formatted(trustAccountId, customerId));
    assertThat(result.at("/isError").asBoolean()).isTrue();
    assertThat(resultPayload(result).at("/error").asString()).isEqualTo("forbidden");

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
                        assertThat(event.getDetails()).containsEntry("tool", "get_trust_balance");
                      });
            });
  }

  @Test
  void trustToolOnNonLegalTenantReturnsModuleDisabled() throws Exception {
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(NON_LEGAL_ORG_ID, "user_nl_owner");
    // Use a dummy id, not the legal tenant's real trustAccountId: the module check must run before
    // any entity lookup, so the call returns module_disabled regardless of whether the id exists.
    JsonNode result =
        callTool(
            owner,
            openSession(owner),
            "get_trust_balance",
            "{\"trustAccountId\":\"%s\"}".formatted(UUID.randomUUID()));
    assertThat(result.at("/isError").asBoolean()).isTrue();
    JsonNode payload = resultPayload(result);
    assertThat(payload.at("/error").asString()).isEqualTo("module_disabled");
    assertThat(payload.at("/message").asString()).doesNotContain("Exception");
  }

  @Test
  void listTrustTransactionsClampsPageSizeTo50() throws Exception {
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    JsonNode result =
        callTool(
            owner,
            openSession(owner),
            "list_trust_transactions",
            "{\"customerId\":\"%s\",\"trustAccountId\":\"%s\",\"page\":0,\"size\":500}"
                .formatted(customerId, trustAccountId));
    assertThat(result.at("/isError").asBoolean(false)).isFalse();
    JsonNode payload = resultPayload(result);
    assertThat(payload.at("/size").asInt()).isEqualTo(50);
    assertThat(payload.at("/total").asLong()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void listInvoicesRequiresInvoicingAndPaginates() throws Exception {
    // Owner (has INVOICING) succeeds and is paginated.
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    JsonNode ok = callTool(owner, openSession(owner), "list_invoices", "{\"page\":0,\"size\":500}");
    assertThat(ok.at("/isError").asBoolean(false)).isFalse();
    assertThat(resultPayload(ok).at("/size").asInt()).isEqualTo(50);

    // Member without INVOICING is forbidden.
    String roleId = createCustomRole("mcp-only-no-invoicing", Set.of("MCP_ACCESS", "VIEW_TRUST"));
    String memberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_deny_inv", "denyinv@test.com", "Deny Inv", "member");
    assignRole(memberId, roleId);
    JwtRequestPostProcessor member = TestJwtFactory.memberJwt(ORG_ID, "user_deny_inv");
    JsonNode denied =
        callTool(member, openSession(member), "list_invoices", "{\"page\":0,\"size\":50}");
    assertThat(denied.at("/isError").asBoolean()).isTrue();
    assertThat(resultPayload(denied).at("/error").asString()).isEqualTo("forbidden");
  }

  @Test
  void getInvoiceReturnsDetailWithTruncatedFlag() throws Exception {
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    JsonNode result =
        callTool(
            owner,
            openSession(owner),
            "get_invoice",
            "{\"invoiceId\":\"%s\"}".formatted(invoiceId));
    assertThat(result.at("/isError").asBoolean(false)).isFalse();
    JsonNode payload = resultPayload(result);
    assertThat(payload.at("/lines").isArray()).isTrue();
    assertThat(payload.at("/payments").isArray()).isTrue();
    // Small invoice: lines/payments well under the 50 cap, so not truncated.
    assertThat(payload.at("/truncated").asBoolean()).isFalse();
    assertThat(payload.at("/id").asString()).isEqualTo(invoiceId);
  }

  @Test
  void getUnbilledTimeFirmWideVsPerMatter() throws Exception {
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    String session = openSession(owner);

    // Firm-wide: returns a paginated list (has "items").
    JsonNode firmWide = callTool(owner, session, "get_unbilled_time", "{\"currency\":\"ZAR\"}");
    assertThat(firmWide.at("/isError").asBoolean(false)).isFalse();
    assertThat(resultPayload(firmWide).has("items")).isTrue();

    // Per-matter: returns a single object keyed by projectId, not a page.
    JsonNode perMatter =
        callTool(owner, session, "get_unbilled_time", "{\"projectId\":\"%s\"}".formatted(matterId));
    assertThat(perMatter.at("/isError").asBoolean(false)).isFalse();
    JsonNode payload = resultPayload(perMatter);
    assertThat(payload.has("items")).isFalse();
    assertThat(payload.at("/projectId").asString()).isEqualTo(matterId);
  }
}
