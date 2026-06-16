package io.b2mash.b2b.b2bstrawman.mcp.tool;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

/**
 * Trivial read-only probe tool so {@code tools/list} is non-empty in the 562B skeleton and the
 * {@code @McpTool} discovery path is exercised at boot (de-risks 563). Replaced/joined by real
 * tools in 563+.
 */
@Component
public class McpPingTool {

  @McpTool(
      name = "kazi_ping",
      description = "Health probe — returns 'ok'. Proves the read-only tool surface is wired.")
  public String ping() {
    return "ok";
  }
}
