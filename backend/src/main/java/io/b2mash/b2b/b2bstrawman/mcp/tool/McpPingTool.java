package io.b2mash.b2b.b2bstrawman.mcp.tool;

import io.b2mash.b2b.b2bstrawman.mcp.McpEnablementService;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpError;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Trivial read-only probe tool so {@code tools/list} is non-empty in the 562B skeleton and the
 * {@code @McpTool} discovery path is exercised at boot (de-risks 563). Replaced/joined by real
 * tools in 563+.
 *
 * <p>Gated by the 565B effective-state guard like every other {@code @McpTool}: a firm that has not
 * enabled the connector (no consent) receives the non-leaking {@code not_enabled} payload rather
 * than {@code "ok"} — so even connector liveness is not signalled without consent.
 */
@Component
public class McpPingTool {

  private final McpEnablementService enablement;
  private final ObjectMapper objectMapper;

  public McpPingTool(McpEnablementService enablement, ObjectMapper objectMapper) {
    this.enablement = enablement;
    this.objectMapper = objectMapper;
  }

  @McpTool(
      name = "kazi_ping",
      description = "Health probe — returns 'ok'. Proves the read-only tool surface is wired.")
  public String ping() {
    if (!enablement.effectiveState()) {
      return objectMapper.writeValueAsString(McpError.notEnabled());
    }
    return "ok";
  }
}
