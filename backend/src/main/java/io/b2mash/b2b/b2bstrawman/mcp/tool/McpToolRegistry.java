package io.b2mash.b2b.b2bstrawman.mcp.tool;

import java.util.List;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration.ServerMcpAnnotatedBeans;
import org.springframework.stereotype.Component;

/**
 * Read-only view over the Spring AI-discovered {@code @McpTool} beans (ADR-306, §11.3
 * read-only-by-construction). Wraps the SDK's {@link ServerMcpAnnotatedBeans} discovery bean rather
 * than re-scanning the classpath — {@code @McpTool} is a method-level annotation, so a hand-rolled
 * {@code ApplicationContext.getBeansWithAnnotation(...)} type-level scan would miss them; the SDK's
 * discovery bean is the correct source.
 *
 * <p>Provides the iteration seam the 567B read-only-assertion test uses to prove no registered tool
 * mutates state. At slice 562C the only registered tool is the trivial {@code kazi_ping} probe
 * (562B).
 *
 * <p>This registry is observational only — it does not itself register or gate tools; Spring AI
 * autoconfiguration performs registration from {@code @McpTool} annotations.
 *
 * <p>The 565B effective-state (enablement + POPIA consent) gate is intentionally NOT routed through
 * this registry: there is no central {@code tools/call} dispatch and Spring AI 2.0.0-M6 exposes no
 * tool-call interceptor, so the gate is a per-tool/per-resource inline guard (the first statement
 * of every {@code @McpTool} method and {@code @McpResource} read calls {@link
 * io.b2mash.b2b.b2bstrawman.mcp.McpEnablementService#effectiveState()} and returns {@link
 * io.b2mash.b2b.b2bstrawman.mcp.dto.McpError#notEnabled()} when disabled). This registry stays a
 * pure read-only seam.
 */
@Component
public class McpToolRegistry {

  private final ServerMcpAnnotatedBeans annotatedBeans;

  public McpToolRegistry(ServerMcpAnnotatedBeans annotatedBeans) {
    this.annotatedBeans = annotatedBeans;
  }

  /** All Spring beans that declare at least one {@code @McpTool} method. */
  public List<Object> registeredToolBeans() {
    return annotatedBeans.getBeansByAnnotation(McpTool.class);
  }

  /**
   * Count of discovered {@code @McpTool}-bearing beans ({@code >= 1} once McpPingTool is on the
   * path).
   */
  public int toolBeanCount() {
    return registeredToolBeans().size();
  }
}
