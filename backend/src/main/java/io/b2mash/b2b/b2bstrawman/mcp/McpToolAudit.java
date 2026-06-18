package io.b2mash.b2b.b2bstrawman.mcp;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared emit of the {@code mcp.tool.invoked} / {@code mcp.access.denied} audit events AND the
 * per-tenant {@link McpMetrics} for the read-catalogue tools (563) and resources. Co-located with
 * {@link McpToolErrors}/{@link McpPagination} so the tool/resource classes share a single emit path
 * rather than each carrying an identical private helper.
 *
 * <p><b>Common-path note (Epic 567):</b> Spring AI 2.0.0-M6 exposes no tool-call interceptor and
 * there is no central {@code tools/call} dispatch, so this static helper IS the common path: every
 * tool funnels its audit + metrics emission through these methods. Epic 567A enriches that path
 * with sanitised metadata ({@link McpAuditMetadata}: row count, entity refs, params summary, denied
 * gate) and the {@link McpMetrics} call count + latency — a single-place edit reflected at each
 * call site.
 *
 * <p>Behaviour preserved verbatim from the original per-tool methods: event type {@code
 * mcp.tool.invoked} / {@code mcp.access.denied}, entity type {@code mcp_tool}, entity id = the
 * current member id, actor auto-resolved from {@code RequestScopes.MEMBER_ID}. Any {@link
 * RuntimeException} from audit OR metrics is swallowed so observability can never break a tool
 * call.
 */
public final class McpToolAudit {

  private McpToolAudit() {}

  // ---- mcp.tool.invoked -------------------------------------------------------

  /**
   * Emit {@code mcp.tool.invoked} for {@code tool} (legacy no-metadata overload); never throws.
   * Retained for the trivial probe path; the read tools use the metadata overload below.
   */
  public static void emitInvoked(String tool, AuditService auditService) {
    logInvoked(tool, Map.of("tool", tool), auditService);
  }

  /**
   * Emit {@code mcp.tool.invoked} for {@code tool} with sanitised {@code meta} (row count, entity
   * refs, params summary) AND record the {@code ok} {@link McpMetrics} sample with {@code latency}.
   * Never throws (audit/metrics must not break a successful tool call). {@code meta} / {@code
   * metrics} / {@code latency} may be null.
   */
  public static void emitInvoked(
      String tool,
      McpAuditMetadata meta,
      AuditService auditService,
      McpMetrics metrics,
      Duration latency) {
    Map<String, Object> details = meta != null ? meta.toDetails(tool) : Map.of("tool", tool);
    logInvoked(tool, details, auditService);
    if (metrics != null) {
      metrics.recordOk(tool, latency);
    }
  }

  private static void logInvoked(String tool, Map<String, Object> details, AuditService audit) {
    try {
      audit.log(
          AuditEventBuilder.builder()
              .eventType("mcp.tool.invoked")
              .entityType("mcp_tool")
              .entityId(RequestScopes.requireMemberId())
              .details(details)
              .build());
    } catch (RuntimeException e) {
      // Audit emission must never break a successful tool call.
    }
  }

  // ---- mcp.access.denied ------------------------------------------------------

  /** Emit {@code mcp.access.denied} for {@code tool} (legacy no-gate overload); never throws. */
  public static void emitDenied(String tool, AuditService auditService) {
    logDenied(tool, Map.of("tool", tool), auditService);
  }

  /**
   * Emit {@code mcp.access.denied} for {@code tool} carrying the {@code deniedGate} that turned the
   * caller away (e.g. {@code VIEW_TRUST}, {@code INVOICING}, {@code AI_MANAGE}, {@code MCP_ACCESS},
   * {@code project-access}) AND record the {@code denied} {@link McpMetrics} sample. Never throws.
   * {@code metrics} / {@code latency} may be null.
   */
  public static void emitDenied(
      String tool,
      String deniedGate,
      AuditService auditService,
      McpMetrics metrics,
      Duration latency) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("tool", tool);
    if (deniedGate != null) {
      details.put("deniedGate", deniedGate);
    }
    logDenied(tool, details, auditService);
    if (metrics != null) {
      metrics.recordDenied(tool, latency);
    }
  }

  private static void logDenied(String tool, Map<String, Object> details, AuditService audit) {
    try {
      audit.log(
          AuditEventBuilder.builder()
              .eventType("mcp.access.denied")
              .entityType("mcp_tool")
              .entityId(RequestScopes.requireMemberId())
              .details(details)
              .build());
    } catch (RuntimeException e) {
      // Audit emission must never break the call (denial path included).
    }
  }
}
