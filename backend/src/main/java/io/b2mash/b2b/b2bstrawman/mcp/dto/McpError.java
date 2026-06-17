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

  /**
   * Non-leaking authorization error — the caller lacks the per-domain capability gate required by
   * this tool/resource (e.g. {@code VIEW_TRUST}, {@code AI_MANAGE}). Returned (never thrown) so the
   * LLM sees a structured {@code forbidden} code rather than an {@code "Error invoking method"}
   * leak.
   */
  public static McpError forbidden() {
    return new McpError("forbidden", "You do not have permission to access this data.");
  }

  /**
   * Module-disabled error — the firm has not enabled this vertical module (e.g. trust accounting).
   * Returned for tenants whose {@code enabled_modules} lacks the module, so a non-legal firm gets a
   * clean signal instead of a stack trace.
   *
   * @param what human-oriented module name (e.g. {@code "trust accounting"})
   */
  public static McpError moduleDisabled(String what) {
    return new McpError("module_disabled", what + " is not enabled for this firm.");
  }

  /**
   * Connector-not-enabled error — the firm has not opted into the Kazi MCP connector (no enabled
   * {@code MCP} integration, or POPIA data-egress consent is absent/revoked). Returned (never
   * thrown) as the FIRST check on every tool/resource read so that, when consent is missing, no
   * firm data — and no data/member/matter existence — is disclosed to the LLM.
   */
  public static McpError notEnabled() {
    return new McpError("not_enabled", "the MCP connector is not enabled for this firm");
  }
}
