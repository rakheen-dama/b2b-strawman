package io.b2mash.b2b.b2bstrawman.mcp.dto;

/**
 * Non-leaking tool-level error, surfaced to the MCP client as {@code isError:true}. Carries a short
 * stable {@code error} code and a human-oriented {@code message}; never echoes internal detail
 * (stack traces, SQL, entity internals).
 *
 * @param error stable machine-readable code (e.g. {@code response_too_large})
 * @param message safe, actionable guidance for the caller
 */
public record McpError(String error, String message) {

  public static McpError responseTooLarge() {
    return new McpError(
        "response_too_large",
        "Result set too large — narrow your query (add filters or reduce the time range).");
  }
}
