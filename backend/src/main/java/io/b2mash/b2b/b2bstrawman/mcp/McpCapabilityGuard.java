package io.b2mash.b2b.b2bstrawman.mcp;

import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.mcp.dto.McpError;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.function.LongFunction;
import tools.jackson.databind.ObjectMapper;

/**
 * Single reusable capability gate for the MCP read tools (563/564) and resources. Collapses the
 * per-method {@code if (!RequestScopes.hasCapability(cap)) { emitDenied(...); return forbidden; }}
 * block into one call so the refusal path (capability check + {@code mcp.access.denied} audit +
 * {@code outcome=denied} metric + non-leaking forbidden payload) is emitted identically everywhere
 * and cannot be forgotten when a new tool is added.
 *
 * <p><b>Why not {@code @RequiresCapability} (issue #1463):</b> the repo-standard declarative
 * annotation denies by throwing {@code AccessDeniedException}, which is structurally wrong for MCP
 * tools under Spring AI 2.0.0-M6 for three reasons: (1) the annotated-tool result converter only
 * sets {@code isError:true} when the method <i>throws</i>, wrapping the message as {@code "Error
 * invoking method: <message>"} — leaking the refusal reason and breaking the non-leaking {@code
 * {"error":"forbidden"}} contract; (2) there is no central {@code tools/call} dispatch / tool-call
 * interceptor (see {@link McpToolRegistry} / {@link McpToolAudit}), so annotation enforcement would
 * depend on whether the SDK happens to invoke the AOP proxy — a security-fragility bet; (3) the
 * annotation short-circuits before the body, bypassing the bespoke {@link McpToolAudit#emitDenied}
 * audit + {@link McpMetrics} the MCP layer requires on every refusal. The gate therefore stays
 * inline; this helper makes it DRY and uniform instead.
 *
 * <p>The body is a {@link LongFunction} of {@code startNanos} (a {@link System#nanoTime()} reading
 * captured before the capability check) so the success path keeps the single timing origin it uses
 * for the {@code mcp.tool.invoked} latency.
 */
public final class McpCapabilityGuard {

  private McpCapabilityGuard() {}

  /**
   * Gate a {@code @McpTool} method. Returns a {@code McpToolErrors.asResult(forbidden)} {@code
   * CallToolResult} (and emits {@code mcp.access.denied} + the {@code denied} metric) when the
   * caller lacks {@code capability}; otherwise runs {@code body} with the start-nanos timing
   * origin.
   */
  public static Object gatedTool(
      String capability,
      String tool,
      AuditService auditService,
      McpMetrics metrics,
      ObjectMapper objectMapper,
      LongFunction<Object> body) {
    long startNanos = System.nanoTime();
    if (!RequestScopes.hasCapability(capability)) {
      McpToolAudit.emitDenied(
          tool, capability, auditService, metrics, McpToolAudit.elapsed(startNanos));
      return McpToolErrors.asResult(McpError.forbidden(), objectMapper);
    }
    return body.apply(startNanos);
  }

  /**
   * Gate a {@code @McpResource} read. Returns the serialised {@code McpError.forbidden()} JSON
   * String (and emits {@code mcp.access.denied} + the {@code denied} metric) when the caller lacks
   * {@code capability}; otherwise runs {@code body} with the start-nanos timing origin. A resource
   * returns a JSON String and must never throw (a thrown {@code AccessDeniedException} would leak
   * as "Error invoking method").
   */
  public static String gatedResource(
      String capability,
      String resourceName,
      AuditService auditService,
      McpMetrics metrics,
      ObjectMapper objectMapper,
      LongFunction<String> body) {
    long startNanos = System.nanoTime();
    if (!RequestScopes.hasCapability(capability)) {
      McpToolAudit.emitDenied(
          resourceName, capability, auditService, metrics, McpToolAudit.elapsed(startNanos));
      return objectMapper.writeValueAsString(McpError.forbidden());
    }
    return body.apply(startNanos);
  }
}
