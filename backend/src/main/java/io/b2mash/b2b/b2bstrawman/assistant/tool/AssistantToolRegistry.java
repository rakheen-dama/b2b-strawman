package io.b2mash.b2b.b2bstrawman.assistant.tool;

import io.b2mash.b2b.b2bstrawman.assistant.provider.ToolDefinition;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Auto-discovers all {@link AssistantTool} beans at startup and provides capability-filtered
 * lookup. Fails fast on duplicate tool names.
 *
 * <p>Follows the same auto-discovery pattern as {@link
 * io.b2mash.b2b.b2bstrawman.assistant.provider.LlmChatProviderRegistry}.
 */
@Component
public class AssistantToolRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(AssistantToolRegistry.class);

  private final Map<String, AssistantTool> tools;

  public AssistantToolRegistry(List<AssistantTool> assistantTools) {
    var mutable = new HashMap<String, AssistantTool>();
    for (var tool : assistantTools) {
      var existing = mutable.putIfAbsent(tool.name(), tool);
      if (existing != null) {
        throw new IllegalStateException(
            "Duplicate AssistantTool name: \""
                + tool.name()
                + "\" registered by both "
                + existing.getClass().getName()
                + " and "
                + tool.getClass().getName());
      }
    }
    this.tools = Map.copyOf(mutable);
    LOG.info("Registered {} assistant tools", tools.size());
  }

  /**
   * Returns tools accessible to a user with the given capabilities. A tool is accessible when its
   * {@link AssistantTool#requiredCapabilities()} is empty OR is a subset of the user's
   * capabilities.
   */
  public List<AssistantTool> getToolsForUser(Set<String> capabilities) {
    return tools.values().stream()
        .filter(
            tool ->
                tool.requiredCapabilities().isEmpty()
                    || capabilities.containsAll(tool.requiredCapabilities()))
        .toList();
  }

  /**
   * Returns {@link ToolDefinition} records for tools accessible to a user with the given
   * capabilities. Used to populate the {@code tools} field in a {@link
   * io.b2mash.b2b.b2bstrawman.assistant.provider.ChatRequest}.
   */
  public List<ToolDefinition> getToolDefinitions(Set<String> capabilities) {
    return getToolsForUser(capabilities).stream()
        .map(tool -> new ToolDefinition(tool.name(), tool.description(), tool.inputSchema()))
        .toList();
  }

  /**
   * Returns the tool with the given name, enforcing capability checks. The tool is returned only if
   * the user's capabilities satisfy the tool's {@link AssistantTool#requiredCapabilities()}.
   *
   * @throws IllegalArgumentException if no tool is registered with the given name (this indicates
   *     an LLM hallucination — the {@code AssistantService} handles this gracefully by sending an
   *     error tool result back to the LLM)
   * @throws InsufficientToolCapabilityException if the user's capabilities do not satisfy the
   *     tool's required capabilities
   */
  public AssistantTool getTool(String name, Set<String> userCapabilities) {
    var tool = getToolInternal(name);
    if (!tool.requiredCapabilities().isEmpty()
        && !userCapabilities.containsAll(tool.requiredCapabilities())) {
      throw new InsufficientToolCapabilityException(name, tool.requiredCapabilities());
    }
    return tool;
  }

  /**
   * Returns the tool with the given name without capability checks. Package-private — intended for
   * internal use only (e.g., tests, registry introspection).
   */
  AssistantTool getToolInternal(String name) {
    var tool = tools.get(name);
    if (tool == null) {
      throw new IllegalArgumentException("No AssistantTool registered with name: \"" + name + "\"");
    }
    return tool;
  }
}
