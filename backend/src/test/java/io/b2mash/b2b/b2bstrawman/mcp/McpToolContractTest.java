package io.b2mash.b2b.b2bstrawman.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * ADR-343 MCP contract pin: snapshots {@code tools/list} (name, description, inputSchema) plus
 * {@code resources/list} and {@code resources/templates/list} exactly as served to the
 * kazi-legal-za plugin, and compares them byte-for-byte to {@code
 * src/test/resources/mcp/tool-contract.json}. The Phase 87 matter/client wire rename (ADR-337)
 * explicitly excludes {@code mcp/}; this test proves it.
 *
 * <p>The two {@code {id}} resources are URI templates, so the server advertises them via {@code
 * resources/templates/list} (not {@code resources/list}) — both lists are snapshotted.
 *
 * <p>On mismatch the actual is written to {@code target/mcp/tool-contract.actual.json}. Regenerate
 * ONLY deliberately with {@code -Dmcp.contract.update=true}; the PR that does so must cite an ADR.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McpToolContractTest {

  private static final String ORG_ID = "org_mcp_tool_contract_test";
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
  private static final String RESOURCE_TEMPLATES_LIST_BODY =
      """
      {"jsonrpc":"2.0","id":4,"method":"resources/templates/list","params":{}}""";

  /** The 21 tools pinned at Phase 87 start (architecture §12.10.5), sorted. */
  private static final List<String> EXPECTED_TOOLS =
      List.of(
          "attach_document",
          "file_correspondence",
          "get_audit_events",
          "get_client",
          "get_correspondence",
          "get_document_url",
          "get_invoice",
          "get_matter",
          "get_matter_activity",
          "get_trust_balance",
          "get_unbilled_time",
          "kazi_ping",
          "list_clients",
          "list_compliance_gaps",
          "list_correspondence",
          "list_invoices",
          "list_matters",
          "list_trust_transactions",
          "propose_task",
          "resolve_matter_by_email",
          "search_documents");

  private static final Path SNAPSHOT = Path.of("src/test/resources/mcp/tool-contract.json");
  private static final Path ACTUAL = Path.of("target/mcp/tool-contract.actual.json");

  private static final JsonMapper CANONICAL =
      JsonMapper.builder()
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .enable(SerializationFeature.INDENT_OUTPUT)
          .build();

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TenantProvisioningService provisioningService;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "MCP Tool Contract Test Org", null);
    TestMemberHelper.syncMember(mockMvc, ORG_ID, "user_owner", "owner@test.com", "Owner", "owner");
  }

  @Test
  void servedToolAndResourceContractMatchesCommittedSnapshot() throws Exception {
    JwtRequestPostProcessor jwt = TestJwtFactory.ownerJwt(ORG_ID, "user_owner");
    MvcResult init = mcpCall(INITIALIZE_BODY, jwt, null);
    String session = init.getResponse().getHeader("Mcp-Session-Id");
    assertThat(session).as("initialize must set Mcp-Session-Id").isNotBlank();
    mcpCall(INITIALIZED_NOTIFICATION_BODY, jwt, session);

    JsonNode toolsResult = rpcResult(TOOLS_LIST_BODY, jwt, session);
    JsonNode resourcesResult = rpcResult(RESOURCES_LIST_BODY, jwt, session);
    JsonNode templatesResult = rpcResult(RESOURCE_TEMPLATES_LIST_BODY, jwt, session);

    assertThat(
            toolsResult.path("nextCursor").isMissingNode()
                || toolsResult.path("nextCursor").isNull())
        .as("tools/list must return the whole catalogue in one page")
        .isTrue();

    List<Map<String, Object>> tools = new ArrayList<>();
    for (JsonNode t : toolsResult.path("tools")) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("name", t.path("name").asString());
      m.put("description", t.path("description").asString());
      m.put("inputSchema", objectMapper.treeToValue(t.path("inputSchema"), Object.class));
      tools.add(m);
    }
    tools.sort(Comparator.comparing(m -> (String) m.get("name")));

    List<Object> resources = sortedNodes(resourcesResult.path("resources"), "uri");
    List<Object> templates = sortedNodes(templatesResult.path("resourceTemplates"), "uriTemplate");

    assertThat(tools).as("ADR-343: 21 MCP tools").hasSize(21);
    assertThat(tools)
        .extracting(m -> (String) m.get("name"))
        .containsExactlyElementsOf(EXPECTED_TOOLS);
    assertThat(resources).as("ADR-343: 1 concrete MCP resource (resources/list)").hasSize(1);
    assertThat(templates)
        .as("ADR-343: 2 URI-templated MCP resources (resources/templates/list)")
        .hasSize(2);
    List<String> uris = new ArrayList<>();
    for (JsonNode r : resourcesResult.path("resources")) {
      uris.add(r.path("uri").asString());
    }
    for (JsonNode r : templatesResult.path("resourceTemplates")) {
      uris.add(r.path("uriTemplate").asString());
    }
    assertThat(uris)
        .as("ADR-343: 3 MCP resources (1 fixed + 2 URI templates)")
        .containsExactlyInAnyOrder(
            "kazi://firm-profile", "kazi://matter/{id}", "kazi://client/{id}");

    Map<String, Object> contract = new LinkedHashMap<>();
    contract.put("tools", tools);
    contract.put("resources", resources);
    contract.put("resourceTemplates", templates);
    String actual = normalise(CANONICAL.writeValueAsString(contract));

    if (Boolean.getBoolean("mcp.contract.update")) {
      Files.createDirectories(SNAPSHOT.getParent());
      Files.writeString(SNAPSHOT, actual, StandardCharsets.UTF_8);
      System.out.println(
          "[McpToolContractTest] -Dmcp.contract.update=true: rewrote "
              + SNAPSHOT
              + " — the PR must cite an ADR (ADR-343)");
      return;
    }

    assertThat(SNAPSHOT)
        .as("missing %s — generate with -Dmcp.contract.update=true (cite an ADR)", SNAPSHOT)
        .exists();
    String expected = normalise(Files.readString(SNAPSHOT, StandardCharsets.UTF_8));
    if (!expected.equals(actual)) {
      Files.createDirectories(ACTUAL.getParent());
      Files.writeString(ACTUAL, actual, StandardCharsets.UTF_8);
      fail(
          String.format(
              "ADR-343: the served MCP contract changed. The kazi-legal-za plugin depends on it.%n"
                  + "Inspect: diff -u %s %s%n"
                  + "If the change is intended, rerun with -Dmcp.contract.update=true and cite an"
                  + " ADR in the PR.%n%s",
              SNAPSHOT, ACTUAL, diff(expected, actual)));
    }
  }

  // ---- helpers ------------------------------------------------------------------

  private List<Object> sortedNodes(JsonNode array, String key) {
    List<JsonNode> nodes = new ArrayList<>();
    array.forEach(nodes::add);
    nodes.sort(Comparator.comparing(n -> n.path(key).asString()));
    List<Object> out = new ArrayList<>();
    for (JsonNode n : nodes) {
      out.add(objectMapper.treeToValue(n, Object.class));
    }
    return out;
  }

  private static String normalise(String s) {
    return s.replace("\r\n", "\n").stripTrailing() + "\n";
  }

  /** Positional line diff of the first 40 differing lines (full file is in target/). */
  private static String diff(String expected, String actual) {
    List<String> e = expected.lines().toList();
    List<String> a = actual.lines().toList();
    StringBuilder sb = new StringBuilder();
    int shown = 0;
    for (int i = 0; i < Math.max(e.size(), a.size()) && shown < 40; i++) {
      String el = i < e.size() ? e.get(i) : null;
      String al = i < a.size() ? a.get(i) : null;
      if (Objects.equals(el, al)) {
        continue;
      }
      sb.append("@@ line ").append(i + 1).append('\n');
      if (el != null) {
        sb.append("- ").append(el).append('\n');
      }
      if (al != null) {
        sb.append("+ ").append(al).append('\n');
      }
      shown++;
    }
    return sb.toString();
  }

  private JsonNode rpcResult(String body, JwtRequestPostProcessor jwt, String session)
      throws Exception {
    JsonNode rpc = parseRpc(mcpCall(body, jwt, session).getResponse().getContentAsString());
    assertThat(rpc.has("error")).as("JSON-RPC error for %s: %s", body, rpc.path("error")).isFalse();
    return rpc.path("result");
  }

  /** Copied from McpHandshakeIntegrationTest#mcpCall. */
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

  /** Copied from McpHandshakeIntegrationTest#parseRpc (first SSE data: line, else whole body). */
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
}
