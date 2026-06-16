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

  /**
   * Non-leaking "not found / no access" error. The same message is returned whether the entity does
   * not exist or the caller cannot view it (security-by-obscurity): the LLM must never be able to
   * distinguish the two and so probe for entity existence.
   *
   * @param entityNoun lower-case singular noun for the entity ({@code "matter"}, {@code "client"},
   *     {@code "document"}) — used only to phrase the safe message, never echoes any id detail
   */
  public static McpError notFound(String entityNoun) {
    return new McpError(
        "not_found", "No " + entityNoun + " found for that id, or you do not have access.");
  }

  /** Non-leaking "bad request" error for invalid tool arguments (e.g. a malformed scope). */
  public static McpError invalidRequest(String message) {
    return new McpError("invalid_request", message);
  }
}
