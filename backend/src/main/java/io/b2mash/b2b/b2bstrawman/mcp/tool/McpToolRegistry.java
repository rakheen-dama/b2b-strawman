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
 * <p>TODO(565B): the {@code MCP_ACCESS} front-door enablement/consent gate is NOT added here — it
 * is a later concern. This registry stays a pure read-only seam.
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
