package io.b2mash.b2b.b2bstrawman.mcp;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Per-tenant Micrometer metrics for the Kazi MCP server (Epic 567A.2). Records a call count and a
 * latency timer for every {@code @McpTool}/{@code @McpResource} invocation, tagged by tenant
 * schema, tool name and outcome.
 *
 * <p><b>No-PII label contract:</b> the only label dimensions are {@code tenant} (the tenant schema
 * — a {@code tenant_<12hex>} hash resolved from the validated JWT, NOT a name), {@code tool} (a
 * fixed {@code @McpTool} name) and {@code outcome} ({@code ok} | {@code denied} | {@code error}).
 * Member ids, client/matter/invoice ids, and any free text are NEVER used as labels (they would
 * explode cardinality and leak PII into the metrics store).
 *
 * <p>Modelled on {@code JobQueueMetrics} (Phase 75 555A): {@link Counter}/{@link Timer} instances
 * are cached per label-tuple in a {@link ConcurrentHashMap} via {@code computeIfAbsent}. Recording
 * never throws — a metrics failure must never break a tool call (mirrors the {@link McpToolAudit}
 * swallow contract). Unconditional {@code @Component}: the MCP surface is always present, so no
 * {@code @ConditionalOnProperty} gate is needed (avoids context-cache churn in tests).
 */
@Component
public class McpMetrics {

  public static final String OUTCOME_OK = "ok";
  public static final String OUTCOME_DENIED = "denied";
  public static final String OUTCOME_ERROR = "error";

  private static final String UNKNOWN_TENANT = "unknown";

  private final MeterRegistry registry;
  private final ConcurrentHashMap<String, Counter> callCounters = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Timer> latencyTimers = new ConcurrentHashMap<>();

  public McpMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  /** Record a successful tool invocation with its wall-clock latency. */
  public void recordOk(String tool, Duration latency) {
    record(tool, OUTCOME_OK, latency);
  }

  /** Record a tool invocation turned away by a gate (capability / front-door / module). */
  public void recordDenied(String tool, Duration latency) {
    record(tool, OUTCOME_DENIED, latency);
  }

  /** Record a tool invocation that errored. */
  public void recordError(String tool, Duration latency) {
    record(tool, OUTCOME_ERROR, latency);
  }

  /**
   * Increment the call counter and record the latency timer for {@code (tenant, tool, outcome)}.
   * Tenant schema is resolved from {@link RequestScopes#getTenantIdOrNull()} (a hash, not PII).
   * Never throws.
   */
  public void record(String tool, String outcome, Duration latency) {
    try {
      String tenant = resolveTenant();
      String key = tenant + "|" + tool + "|" + outcome;
      callCounters
          .computeIfAbsent(
              key,
              k ->
                  Counter.builder("kazi_mcp_tool_calls_total")
                      .tag("tenant", tenant)
                      .tag("tool", tool)
                      .tag("outcome", outcome)
                      .description("MCP tool invocations, per tenant / tool / outcome")
                      .register(registry))
          .increment();
      if (latency != null) {
        latencyTimers
            .computeIfAbsent(
                key,
                k ->
                    Timer.builder("kazi_mcp_tool_latency_seconds")
                        .tag("tenant", tenant)
                        .tag("tool", tool)
                        .tag("outcome", outcome)
                        .description("MCP tool latency, per tenant / tool / outcome")
                        .register(registry))
            .record(latency.toMillis(), TimeUnit.MILLISECONDS);
      }
    } catch (RuntimeException e) {
      // Metrics must never break a tool call (mirrors McpToolAudit's swallow contract).
    }
  }

  private static String resolveTenant() {
    String tenant = RequestScopes.getTenantIdOrNull();
    return tenant != null ? tenant : UNKNOWN_TENANT;
  }
}
