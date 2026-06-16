package io.b2mash.b2b.b2bstrawman.mcp;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.Map;

/**
 * Shared emit of the {@code mcp.tool.invoked} audit event from the read-catalogue tools (563).
 * Co-located with {@link McpToolErrors}/{@link McpPagination} so the four tool classes ({@code
 * MatterTools}, {@code ClientTools}, {@code DocumentTools}, {@code ActivityTools}) share a single
 * emit path rather than each carrying an identical private {@code emitInvoked}. Epic 567 extends
 * this same path with richer metadata (row counts etc.), so keeping it in one place makes that a
 * single edit.
 *
 * <p>Behaviour preserved verbatim from the original per-tool methods: event type {@code
 * mcp.tool.invoked}, entity type {@code mcp_tool}, entity id = the current member id, details
 * {@code {"tool": <name>}}; any {@link RuntimeException} is swallowed so audit emission can never
 * break a successful tool call.
 */
public final class McpToolAudit {

  private McpToolAudit() {}

  /**
   * Emit {@code mcp.tool.invoked} for {@code tool}; never throws (audit must not break the call).
   */
  public static void emitInvoked(String tool, AuditService auditService) {
    try {
      auditService.log(
          AuditEventBuilder.builder()
              .eventType("mcp.tool.invoked")
              .entityType("mcp_tool")
              .entityId(RequestScopes.requireMemberId())
              .details(Map.of("tool", tool))
              .build());
    } catch (RuntimeException e) {
      // Audit emission must never break a successful tool call (567 finalises richer metadata).
    }
  }
}
