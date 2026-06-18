package io.b2mash.b2b.b2bstrawman.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
 * Epic 567B.4 — pagination-cap + response-ceiling hardening (§11.12, ADR-307). A dedicated pass
 * over the live {@code /mcp} {@code tools/call} path (the existing {@code McpPaginationTest} covers
 * the pure {@link McpPagination} arithmetic edges):
 *
 * <ol>
 *   <li>list tools clamp an over-large requested size to the default server max (50).
 *   <li>the audit tool clamps to its higher cap (200), still server-enforced.
 *   <li>an unbounded service result is sliced and the {@code truncated} + {@code total} flags are
 *       surfaced so the client knows there is more.
 * </ol>
 *
 * <p>The response-ceiling guard ({@code response_too_large} on an oversized materialised result
 * set) is asserted in pure-unit form by {@code ClientToolsCeilingTest} and is intentionally NOT
 * duplicated in this heavy {@code @SpringBootTest} — see the comment at the former test 4.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McpPaginationCapTest {

  private static final String ORG_ID = "org_mcp_567_pagecap";
  private static final String ACCEPT = "application/json, text/event-stream";

  private static final String INITIALIZE_BODY =
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"kazi-itest","version":"1.0.0"}}}""";
  private static final String INITIALIZED_NOTIFICATION_BODY =
      """
      {"jsonrpc":"2.0","method":"notifications/initialized"}""";

  // 51 clients: more than the default page max (50) but under the 200 response ceiling — so a
  // size=50 page is truncated with total=51, exercising the slice + truncated/total surfacing
  // without tripping the response_too_large guard.
  private static final int SEEDED_CLIENTS = 51;

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private McpEnablementService enablementService;
  @Autowired private TransactionTemplate transactionTemplate;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "MCP 567 PageCap Org", null);
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

    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    for (int i = 0; i < SEEDED_CLIENTS; i++) {
      io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper.createCustomer(
          mockMvc, owner, "PageCap Client " + i, "pagecap" + i + "@test.com");
    }
  }

  // ---- (1) list tool clamps requested size to 50 -----------------------------

  @Test
  void listToolClampsRequestedSizeToFifty() throws Exception {
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    JsonNode result =
        callTool(owner, openSession(owner), "list_clients", "{\"page\":0,\"size\":5000}");
    assertThat(result.at("/isError").asBoolean(false)).isFalse();
    JsonNode payload = payload(result);
    assertThat(payload.at("/size").asInt()).isEqualTo(McpPagination.DEFAULT_MAX_SIZE);
    assertThat(payload.at("/items").size()).isLessThanOrEqualTo(McpPagination.DEFAULT_MAX_SIZE);
  }

  // ---- (2) audit tool clamps to its higher cap (200) -------------------------

  @Test
  void auditToolClampsRequestedSizeToTwoHundred() throws Exception {
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    // Owner has TEAM_OVERSIGHT, so the audit tool runs and clamps size 5000 → 200.
    JsonNode result =
        callTool(owner, openSession(owner), "get_audit_events", "{\"page\":0,\"size\":5000}");
    assertThat(result.at("/isError").asBoolean(false)).isFalse();
    assertThat(payload(result).at("/size").asInt()).isEqualTo(McpPagination.AUDIT_MAX_SIZE);
  }

  // ---- (3) truncated + total surfaced on an over-page result -----------------

  @Test
  void unboundedResultIsSlicedAndTruncatedTotalSurfaced() throws Exception {
    JwtRequestPostProcessor owner = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    JsonNode result =
        callTool(owner, openSession(owner), "list_clients", "{\"page\":0,\"size\":50}");
    assertThat(result.at("/isError").asBoolean(false)).isFalse();
    JsonNode payload = payload(result);
    // 51 clients seeded: page 0 of size 50 returns 50 items, total >= 51, truncated true.
    assertThat(payload.at("/items").size()).isEqualTo(50);
    assertThat(payload.at("/total").asLong()).isGreaterThanOrEqualTo(SEEDED_CLIENTS);
    assertThat(payload.at("/truncated").asBoolean())
        .as("a result larger than the page must surface truncated=true")
        .isTrue();
  }

  // ---- (4) oversized result set → response_too_large "narrow your query" -----
  //
  // The response-ceiling guard (a materialised list strictly larger than RESPONSE_ITEM_CEILING must
  // fail with response_too_large rather than emit a truncated page) is asserted in pure-unit form
  // by
  // ClientToolsCeilingTest. It is deliberately NOT duplicated here: driving it through the live
  // /mcp
  // path would need >200 seeded rows per run, and re-running the same mock-based unit assertion
  // inside this heavy @SpringBootTest only adds context-startup cost without extra coverage. This
  // class keeps the live clamp/slice/truncate assertions; the ceiling math lives in
  // ClientToolsCeilingTest (the guard idiom is identical for all three ceiling-guarded tools).

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

  private JsonNode payload(JsonNode result) {
    return objectMapper.readTree(result.at("/content/0/text").asString());
  }
}
