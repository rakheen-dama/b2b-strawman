package io.b2mash.b2b.b2bstrawman.assistant.tool;

import java.util.Map;
import java.util.Set;

/**
 * Interface for tools the LLM can invoke via the assistant. Each tool declares its name, JSON
 * schema, confirmation requirement, and capability prerequisites. The registry auto-discovers all
 * {@code @Component} implementations.
 *
 * <p>Implementations follow the "thin tool" pattern: 20-40 lines delegating to an existing
 * {@code @Service}. No business logic, no validation, no authorization beyond capability filtering
 * in the registry.
 *
 * @see AssistantToolRegistry
 * @see TenantToolContext
 */
public interface AssistantTool {

  /** Tool name as exposed to the LLM (e.g., {@code "list_projects"}). Snake_case. */
  String name();

  /** Human-readable description for the LLM's tool selection. */
  String description();

  /** JSON Schema describing the tool's input parameters. */
  Map<String, Object> inputSchema();

  /**
   * Whether this tool requires user confirmation before execution. Returns {@code true} for all
   * write tools (create/update), {@code false} for read tools.
   */
  boolean requiresConfirmation();

  /**
   * Capability names required to use this tool. An empty set means accessible to all roles. The
   * registry filters tools by user capabilities — tools never check capabilities themselves.
   */
  Set<String> requiredCapabilities();

  /**
   * Execute the tool with the given input and tenant context. Returns a JSON-serializable result
   * object (typically a {@code Map}, {@code List}, or scalar).
   */
  Object execute(Map<String, Object> input, TenantToolContext context);
}
