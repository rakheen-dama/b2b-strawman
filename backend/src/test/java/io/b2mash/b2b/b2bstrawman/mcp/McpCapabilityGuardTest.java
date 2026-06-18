package io.b2mash.b2b.b2bstrawman.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventRecord;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

/**
 * Plain unit test (no Spring context) for {@link McpCapabilityGuard} in isolation — the single
 * reusable capability gate the MCP read tools/resources delegate to (issue #1463). Pins the refusal
 * contract the {@code McpCapabilityGatingTest} / {@code McpAuditEmissionTest} integration tests
 * rely on: a missing capability NEVER runs the body, emits {@code mcp.access.denied} carrying the
 * {@code deniedGate}, records the {@code denied} metric, and returns a non-leaking {@code
 * forbidden} payload; a present capability runs the body and emits no denial.
 */
class McpCapabilityGuardTest {

  private static final UUID MEMBER = UUID.randomUUID();

  @Test
  void deniedTool_emitsAccessDeniedWithGate_returnsForbidden_andSkipsBody() {
    AuditService audit = mock(AuditService.class);
    McpMetrics metrics = new McpMetrics(new SimpleMeterRegistry());
    ObjectMapper om = new ObjectMapper();
    AtomicBoolean bodyRan = new AtomicBoolean(false);

    Object result =
        runScoped(
            Set.of("MCP_ACCESS"), // no VIEW_TRUST
            () ->
                McpCapabilityGuard.gatedTool(
                    "VIEW_TRUST",
                    "get_trust_balance",
                    audit,
                    metrics,
                    om,
                    startNanos -> {
                      bodyRan.set(true);
                      return "should-not-run";
                    }));

    assertThat(bodyRan).isFalse();
    assertThat(result).isInstanceOf(CallToolResult.class);
    CallToolResult toolResult = (CallToolResult) result;
    assertThat(toolResult.isError()).isTrue();
    assertThat(((TextContent) toolResult.content().get(0)).text()).contains("forbidden");

    ArgumentCaptor<AuditEventRecord> captor = ArgumentCaptor.forClass(AuditEventRecord.class);
    verify(audit).log(captor.capture());
    AuditEventRecord event = captor.getValue();
    assertThat(event.eventType()).isEqualTo("mcp.access.denied");
    assertThat(event.details()).containsEntry("tool", "get_trust_balance");
    assertThat(event.details()).containsEntry("deniedGate", "VIEW_TRUST");
  }

  @Test
  void grantedTool_runsBody_andEmitsNoDenial() {
    AuditService audit = mock(AuditService.class);
    McpMetrics metrics = new McpMetrics(new SimpleMeterRegistry());
    ObjectMapper om = new ObjectMapper();

    Object result =
        runScoped(
            Set.of("MCP_ACCESS", "VIEW_TRUST"),
            () ->
                McpCapabilityGuard.gatedTool(
                    "VIEW_TRUST", "get_trust_balance", audit, metrics, om, startNanos -> "ok"));

    assertThat(result).isEqualTo("ok");
    verify(audit, never()).log(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void deniedResource_returnsSerializedForbiddenJson_andSkipsBody() {
    AuditService audit = mock(AuditService.class);
    McpMetrics metrics = new McpMetrics(new SimpleMeterRegistry());
    ObjectMapper om = new ObjectMapper();
    AtomicBoolean bodyRan = new AtomicBoolean(false);

    String result =
        runScoped(
            Set.of("MCP_ACCESS"), // no AI_MANAGE
            () ->
                McpCapabilityGuard.gatedResource(
                    "AI_MANAGE",
                    "kazi://firm-profile",
                    audit,
                    metrics,
                    om,
                    startNanos -> {
                      bodyRan.set(true);
                      return "should-not-run";
                    }));

    assertThat(bodyRan).isFalse();
    assertThat(result).contains("\"forbidden\"");

    ArgumentCaptor<AuditEventRecord> captor = ArgumentCaptor.forClass(AuditEventRecord.class);
    verify(audit).log(captor.capture());
    assertThat(captor.getValue().details()).containsEntry("deniedGate", "AI_MANAGE");
  }

  /** Bind a member id (so the denial audit can resolve an actor) plus the capability set. */
  private static <T> T runScoped(Set<String> capabilities, java.util.function.Supplier<T> body) {
    Object[] holder = new Object[1];
    ScopedValue.where(RequestScopes.MEMBER_ID, MEMBER)
        .where(RequestScopes.CAPABILITIES, capabilities)
        .run(() -> holder[0] = body.get());
    @SuppressWarnings("unchecked")
    T result = (T) holder[0];
    return result;
  }
}
