package io.b2mash.b2b.b2bstrawman.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.mcp.tool.McpToolRegistry;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.ai.mcp.annotation.McpTool;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Epic 567B.3 — read-only-by-construction registry assertion (ADR-306, §11.3).
 *
 * <p>Two complementary structural proofs that the MCP surface mutates state only through an
 * explicit, MCP_WRITE-gated write-tool allowlist, plus a negotiated-capability proof that the
 * surface never advertises a model-driven elicitation channel:
 *
 * <ol>
 *   <li>Every registered {@code @McpTool}/{@code @McpResource} name uses a read verb ({@code
 *       list_}/{@code get_}/{@code search_} or the trivial {@code kazi_ping} probe / a {@code
 *       kazi://} resource URI) — UNLESS it is a sanctioned write tool in {@link #WRITE_TOOL_NAMES}
 *       (Phase 81, each MCP_WRITE-gated; see {@code McpWriteCapabilityGateTest}). No mutating verb
 *       (create/update/delete/save/...) appears on ANY tool, even a write tool. The registered bean
 *       set is a subset of the union of an explicit read-tool allowlist ({@link
 *       #ALLOWED_TOOL_BEANS}) and the sanctioned write-tool allowlist ({@link
 *       #ALLOWED_WRITE_TOOL_BEANS}), so an UNSANCTIONED new write-back tool is a NEW bean and fails
 *       this gate until explicitly added.
 *   <li>{@code initialize} negotiates {@code tools} + {@code resources} but NOT {@code prompts},
 *       {@code sampling} or {@code completion} — the server never offers a model-driven elicitation
 *       channel (write tools are still {@code tools}, not prompts/sampling/elicitation).
 * </ol>
 *
 * <p>Bytecode introspection of the actual service-method calls is deliberately avoided (fragile);
 * the verb + bean-allowlist + capability-advertisement triad is the structural guarantee. As of
 * Phase 81 the surface carries an explicit, MCP_WRITE-gated write-tool allowlist: the guard now
 * proves "no tool mutates state EXCEPT the sanctioned write tools", and read tools still must use
 * read verbs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McpReadOnlyRegistryTest {

  private static final String ORG_ID = "org_mcp_567_readonly";
  private static final String ACCEPT = "application/json, text/event-stream";

  private static final String INITIALIZE_BODY =
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"kazi-itest","version":"1.0.0"}}}""";

  /** Read verbs allowed on a tool name. A mutating tool would not match any of these. */
  private static final Pattern READ_TOOL_NAME = Pattern.compile("^(list|get|search)_[a-z_]+$");

  /**
   * Trivial probe tools exempt from the read-verb pattern (they answer a liveness ping, touch no
   * data). A future probe tool must be added here explicitly — the failure message lists this set
   * so the diagnostic is clear.
   */
  private static final Set<String> PROBE_TOOL_NAMES = Set.of("kazi_ping");

  /**
   * Sanctioned write-tool names — exempt from the read-verb pattern; each is MCP_WRITE-gated (see
   * McpWriteCapabilityGateTest).
   */
  private static final Set<String> WRITE_TOOL_NAMES = Set.of("file_correspondence");

  /** Mutating verbs that must NEVER appear at the start of a registered tool name. */
  private static final List<String> MUTATING_VERBS =
      List.of(
          "create",
          "update",
          "delete",
          "save",
          "set",
          "add",
          "remove",
          "cancel",
          "approve",
          "send",
          "void",
          "transition",
          "enable",
          "revoke",
          "post",
          "put",
          "patch",
          "propose");

  /** Explicit allowlist of read-tool bean classes (562B probe + the 563/564 catalogue). */
  private static final Set<String> ALLOWED_TOOL_BEANS =
      Set.of(
          "McpPingTool",
          "MatterTools",
          "ClientTools",
          "TrustTools",
          "BillingTools",
          "ComplianceTools",
          "DocumentTools",
          "ActivityTools",
          "AuditTools");

  /** Explicit allowlist of SANCTIONED write-tool bean classes (Phase 81 — gated by MCP_WRITE). */
  private static final Set<String> ALLOWED_WRITE_TOOL_BEANS = Set.of("CorrespondenceWriteTools");

  /** Explicit allowlist of read-resource bean classes (563B + 564B). */
  private static final Set<String> ALLOWED_RESOURCE_BEANS =
      Set.of("MatterResource", "ClientResource", "FirmProfileResource");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private McpToolRegistry registry;

  @Autowired
  private io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService provisioningService;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "MCP 567 Read-Only Org", null);
    TestMemberHelper.syncMember(mockMvc, ORG_ID, "user_owner", "owner@test.com", "Owner", "owner");
  }

  // ---- (1) no registered tool/resource mutates state -------------------------

  @Test
  void allRegisteredToolsAndResourcesAreReadOnly() {
    List<String> toolNames = toolNames(registry.registeredToolBeans());
    assertThat(toolNames)
        .as("at least the kazi_ping probe + catalogue tools registered")
        .isNotEmpty();

    // (a) tool-name verb assertion: no name starts with a mutating verb.
    for (String name : toolNames) {
      assertThat(MUTATING_VERBS)
          .as("tool '%s' must not start with a mutating verb", name)
          .noneMatch(verb -> name.toLowerCase(java.util.Locale.ROOT).startsWith(verb));
      // Trivial probe tools and sanctioned write tools are the only non list_/get_/search_ tools.
      if (!PROBE_TOOL_NAMES.contains(name) && !WRITE_TOOL_NAMES.contains(name)) {
        assertThat(READ_TOOL_NAME.matcher(name).matches())
            .as(
                "catalogue tool '%s' must use a read verb (list_/get_/search_), be an allowlisted"
                    + " probe tool %s, or be a sanctioned write tool %s",
                name, PROBE_TOOL_NAMES, WRITE_TOOL_NAMES)
            .isTrue();
      }
    }

    // (b) bean-allowlist assertion: every @McpTool bean is a known read-tool class.
    List<String> toolBeanClasses = beanClassNames(registry.registeredToolBeans());
    assertThat(toolBeanClasses)
        .as(
            "every @McpTool bean must be an allowlisted read-tool class or a sanctioned write-tool"
                + " class (an unsanctioned write-back tool fails here)")
        .allSatisfy(
            c ->
                assertThat(c)
                    .matches(
                        n -> ALLOWED_TOOL_BEANS.contains(n) || ALLOWED_WRITE_TOOL_BEANS.contains(n),
                        "an allowlisted read-tool or sanctioned write-tool class"));

    // (c) resource bean-allowlist: every @McpResource bean is a known read-resource class.
    List<String> resourceBeanClasses = beanClassNames(registry.registeredResourceBeans());
    assertThat(resourceBeanClasses)
        .as("every @McpResource bean must be an allowlisted read-resource class")
        .allSatisfy(c -> assertThat(ALLOWED_RESOURCE_BEANS).contains(c));
  }

  // ---- (2) prompts / sampling / completion NOT advertised --------------------

  @Test
  void initializeDoesNotAdvertisePromptsOrSamplingOrCompletion() throws Exception {
    MvcResult init = mcpCall(INITIALIZE_BODY, TestJwtFactory.ownerJwt(ORG_ID, "user_owner"));
    JsonNode result = parseRpc(init.getResponse().getContentAsString()).get("result");
    assertThat(result).isNotNull();

    // Read-only surface advertises tools + resources only.
    assertThat(result.at("/capabilities/tools").isMissingNode()).isFalse();
    assertThat(result.at("/capabilities/resources").isMissingNode()).isFalse();
    // It must NOT offer a model-driven write/elicitation channel.
    assertThat(result.path("capabilities").has("prompts")).isFalse();
    assertThat(result.path("capabilities").has("sampling")).isFalse();
    assertThat(result.path("capabilities").has("completion")).isFalse();
    assertThat(result.path("capabilities").has("elicitation")).isFalse();
  }

  // ---- reflection helpers ----------------------------------------------------

  private static List<String> toolNames(List<Object> beans) {
    List<String> names = new ArrayList<>();
    for (Object bean : beans) {
      for (Method m : AopUtils.getTargetClass(bean).getDeclaredMethods()) {
        McpTool ann = m.getAnnotation(McpTool.class);
        if (ann != null) {
          names.add(ann.name());
        }
      }
    }
    return names;
  }

  private static List<String> beanClassNames(List<Object> beans) {
    return beans.stream().map(b -> AopUtils.getTargetClass(b).getSimpleName()).toList();
  }

  // ---- /mcp transport helpers ------------------------------------------------

  private MvcResult mcpCall(String body, JwtRequestPostProcessor jwt) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/mcp")
                    .with(jwt)
                    .header("Accept", ACCEPT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
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
}
