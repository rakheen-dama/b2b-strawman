package io.b2mash.b2b.b2bstrawman.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.util.List;
import java.util.Map;
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
 * Epic 567A.3 — audit emission + metric-label hardening tests, driving the real {@code /mcp}
 * JSON-RPC endpoint.
 *
 * <ol>
 *   <li>{@code mcp.session.opened} on {@code initialize}.
 *   <li>{@code mcp.tool.invoked} carries row count + entity refs on a {@code tools/call}.
 *   <li>params summary contains no free-text PII (ids/enums only).
 *   <li>{@code mcp.access.denied} on each refusal type (front-door/capability, project-access).
 *   <li>metric labels carry no PII ({@code tenant}/{@code tool}/{@code outcome} only).
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McpAuditEmissionTest {

  private static final String ORG_ID = "org_mcp_567_audit";
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
  @Autowired private MeterRegistry meterRegistry;

  private String tenantSchema;
  private String matterId;
  private String clientId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "MCP 567 Audit Org", null);
    UUID ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_owner", "owner@test.com", "Owner", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> enablementService.enable("popia-egress-v1")));

    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    matterId = TestEntityHelper.createProject(mockMvc, owner, "Audit Matter");
    clientId = TestEntityHelper.createCustomer(mockMvc, owner, "Audit Client", "ac@test.com");
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
            .reduce((__, last) -> last)
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

  private List<AuditEvent> readEvents(String eventTypePrefix) {
    @SuppressWarnings("unchecked")
    List<AuditEvent>[] holder = new List[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                holder[0] =
                    auditEventRepository
                        .findByFilter(
                            null, null, null, eventTypePrefix, null, null, PageRequest.of(0, 200))
                        .getContent());
    return holder[0];
  }

  // ---- (1) mcp.session.opened on initialize ----------------------------------

  @Test
  void initializeEmitsSessionOpened() throws Exception {
    mcpCall(INITIALIZE_BODY, TestJwtFactory.ownerJwt(ORG_ID, "user_owner"), null);
    assertThat(readEvents("mcp.session.opened"))
        .extracting(AuditEvent::getEventType)
        .contains("mcp.session.opened");
  }

  // ---- (2) mcp.tool.invoked carries row count + entity refs ------------------

  @Test
  void toolInvokedCarriesRowCountAndEntityRefs() throws Exception {
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    JsonNode result =
        callTool(owner, openSession(owner), "get_matter", "{\"id\":\"%s\"}".formatted(matterId));
    assertThat(result.at("/isError").asBoolean(false)).isFalse();

    var getMatterEvents =
        readEvents("mcp.tool.invoked").stream()
            .filter(e -> "get_matter".equals(e.getDetails().get("tool")))
            .toList();
    assertThat(getMatterEvents).isNotEmpty();
    assertThat(getMatterEvents)
        .anySatisfy(
            event -> {
              Map<String, Object> details = event.getDetails();
              // row count present and numeric == 1
              assertThat(details.get("rowCount")).isNotNull();
              assertThat(details.get("rowCount").toString()).isEqualTo("1");
              // entity refs contain the matter id
              assertThat(details.get("entityRefs")).isInstanceOf(List.class);
              @SuppressWarnings("unchecked")
              List<Object> entityRefs = (List<Object>) details.get("entityRefs");
              assertThat(entityRefs).contains(matterId);
            });
  }

  // ---- (3) params summary is ids/enums only — no free-text PII ----------------

  @Test
  void paramsSummaryContainsNoFreeTextPii() throws Exception {
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    // list_matters with a status enum filter — params should carry the enum, never any name/email.
    callTool(
        owner,
        openSession(owner),
        "list_matters",
        "{\"page\":0,\"size\":50,\"status\":\"ACTIVE\"}");

    var listMatterEvents =
        readEvents("mcp.tool.invoked").stream()
            .filter(e -> "list_matters".equals(e.getDetails().get("tool")))
            .filter(e -> e.getDetails().get("params") != null)
            .toList();
    assertThat(listMatterEvents).isNotEmpty();
    assertThat(listMatterEvents)
        .anySatisfy(
            event -> {
              Object params = event.getDetails().get("params");
              assertThat(params).isInstanceOf(Map.class);
              @SuppressWarnings("unchecked")
              Map<String, Object> summary = (Map<String, Object>) params;
              // Only id/enum keys are allowed in the summary.
              assertThat(summary.keySet()).isSubsetOf("status", "customerId");
              assertThat(summary.get("status")).isEqualTo("ACTIVE");
              // No PII tokens leaked: the seeded client name / email must never appear as a value.
              assertThat(summary.values().stream().map(String::valueOf))
                  .noneMatch(v -> v.contains("Audit Client") || v.contains("ac@test.com"));
            });
  }

  // ---- (3a) the param() sink itself drops free-text/PII for ALL callers -------

  @Test
  void paramSinkDropsFreeTextAndPiiRegardlessOfCaller() {
    // Defence-in-depth: McpAuditMetadata.Builder.param() sanitises at the sink, so even a tool that
    // (incorrectly) passed a rich free-text object can never leak it into the params summary.
    UUID id = UUID.randomUUID();
    Map<String, Object> params =
        McpAuditMetadata.builder()
            .param("freeText", "Audit Client ac@test.com SECRET") // spaces/@ → dropped
            .param("name", "Jane Doe") // PII free text → dropped
            .param("object", new Object()) // arbitrary object → dropped
            .param("status", "ACTIVE") // safe enum-shaped token → kept
            .param("customerId", id) // UUID → kept as string id
            .param("count", 7) // number → kept
            .param("flag", true) // boolean → kept
            .build()
            .paramsSummary();

    assertThat(params).doesNotContainKeys("freeText", "name", "object");
    assertThat(params)
        .containsEntry("status", "ACTIVE")
        .containsEntry("customerId", id.toString())
        .containsEntry("count", 7)
        .containsEntry("flag", true);
    assertThat(params.values().stream().map(String::valueOf))
        .as("no free-text/PII value may survive the param() sink")
        .noneMatch(v -> v.contains(" ") || v.contains("@") || v.contains("SECRET"));
  }

  // ---- (3b) a non-matching eventType is NOT written to the audit params summary ----

  @Test
  void unsafeEventTypeIsNotRecordedInAuditParamsSummary() throws Exception {
    // The audit tool's eventType is the only caller-supplied free-text field. An eventType that
    // does
    // not match the safe structural shape (here: contains PII-shaped free text with spaces / @ / an
    // uppercase name) must be used by the query filter but NEVER land in the POPIA-sensitive audit
    // params summary. Owner has TEAM_OVERSIGHT so the tool runs (no capability refusal).
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    String unsafeEventType = "Audit Client ac@test.com SECRET";
    callTool(
        owner,
        openSession(owner),
        "get_audit_events",
        "{\"page\":0,\"size\":50,\"eventType\":\"%s\"}".formatted(unsafeEventType));

    var auditToolEvents =
        readEvents("mcp.tool.invoked").stream()
            .filter(e -> "get_audit_events".equals(e.getDetails().get("tool")))
            .toList();
    assertThat(auditToolEvents).isNotEmpty();
    // No emitted audit event for this tool may carry the unsafe eventType in its params summary.
    assertThat(auditToolEvents)
        .allSatisfy(
            event -> {
              Object params = event.getDetails().get("params");
              if (params != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> summary = (Map<String, Object>) params;
                assertThat(summary).doesNotContainKey("eventType");
                assertThat(summary.values().stream().map(String::valueOf))
                    .as("unsafe free-text eventType must never reach the audit params summary")
                    .noneMatch(v -> v.contains(unsafeEventType) || v.contains("ac@test.com"));
              }
            });
  }

  // ---- (4) mcp.access.denied on each refusal type ----------------------------

  @Test
  void accessDeniedOnCapabilityAndProjectAccessRefusals() throws Exception {
    // Baseline: another test method may have emitted mcp.access.denied events into this shared
    // tenant. Capture the current count so the assertions below only inspect the NEW denials this
    // test produces (guards against cross-method contamination satisfying the assertion
    // spuriously).
    int deniedBaseline = readEvents("mcp.access.denied").size();

    // (a) capability refusal: a member without VIEW_TRUST calling get_trust_balance.
    String denyRoleId =
        createCustomRole("mcp-567-cap-deny", Set.of("MCP_ACCESS", "CUSTOMER_MANAGEMENT"));
    String denyMemberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_567_cap", "cap@test.com", "Cap Deny", "member");
    assignRole(denyMemberId, denyRoleId);
    JwtRequestPostProcessor capMember = TestJwtFactory.memberJwt(ORG_ID, "user_567_cap");
    JsonNode capResult =
        callTool(
            capMember,
            openSession(capMember),
            "get_trust_balance",
            "{\"trustAccountId\":\"%s\"}".formatted(UUID.randomUUID()));
    assertThat(capResult.at("/isError").asBoolean()).isTrue();

    // (b) project-access refusal: a non-member calling get_matter on the owner-only matter → 404,
    // which 567A also emits mcp.access.denied for (gate=project-access).
    String paRoleId = createCustomRole("mcp-567-pa", Set.of("MCP_ACCESS"));
    String paMemberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_567_pa", "pa@test.com", "PA Member", "member");
    assignRole(paMemberId, paRoleId);
    JwtRequestPostProcessor paMember = TestJwtFactory.memberJwt(ORG_ID, "user_567_pa");
    JsonNode paResult =
        callTool(
            paMember, openSession(paMember), "get_matter", "{\"id\":\"%s\"}".formatted(matterId));
    assertThat(paResult.at("/isError").asBoolean()).isTrue();
    assertThat(parsePayload(paResult).at("/error").asString()).isEqualTo("not_found");

    List<AuditEvent> allDenied = readEvents("mcp.access.denied");
    // Only inspect the denials emitted AFTER the baseline (this test's own refusals).
    List<AuditEvent> denied = allDenied.subList(0, allDenied.size() - deniedBaseline);
    assertThat(denied)
        .as("this test produced at least the capability + project-access denials")
        .hasSizeGreaterThanOrEqualTo(2);
    // capability denial carries the gate.
    assertThat(denied)
        .anySatisfy(
            e -> {
              assertThat(e.getDetails()).containsEntry("tool", "get_trust_balance");
              assertThat(e.getDetails()).containsEntry("deniedGate", "VIEW_TRUST");
            });
    // project-access denial is audited too (non-leaking not_found to the client, denial in audit).
    assertThat(denied)
        .anySatisfy(
            e -> {
              assertThat(e.getDetails()).containsEntry("tool", "get_matter");
              assertThat(e.getDetails()).containsEntry("deniedGate", "project-access");
            });
  }

  // ---- (5) metric labels carry no PII ----------------------------------------

  @Test
  void metricLabelsCarryNoPii() throws Exception {
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    callTool(owner, openSession(owner), "list_clients", "{\"page\":0,\"size\":50}");

    var counters = meterRegistry.find("kazi_mcp_tool_calls_total").counters();
    assertThat(counters).isNotEmpty();
    counters.forEach(
        c -> {
          var tagKeys = c.getId().getTags().stream().map(Tag::getKey).toList();
          assertThat(tagKeys).containsExactlyInAnyOrder("tenant", "tool", "outcome");
          // tenant value is a schema hash, never a name/email.
          String tenantTag = c.getId().getTag("tenant");
          assertThat(tenantTag).matches("tenant_[0-9a-f]+|unknown");
          assertThat(tenantTag).doesNotContain("@").doesNotContain(" ");
          // outcome is one of the fixed enum-like values.
          assertThat(c.getId().getTag("outcome")).isIn("ok", "denied", "error");
        });
  }

  // ---- helpers ---------------------------------------------------------------

  private JsonNode parsePayload(JsonNode result) {
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
    return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void assignRole(String memberId, String roleId) throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/members/" + memberId + "/role")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgRoleId\":\"%s\",\"capabilityOverrides\":[]}".formatted(roleId)))
        .andExpect(status().isOk());
  }
}
