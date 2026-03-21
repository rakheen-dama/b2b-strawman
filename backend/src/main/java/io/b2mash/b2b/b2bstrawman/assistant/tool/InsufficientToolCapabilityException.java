package io.b2mash.b2b.b2bstrawman.assistant.tool;

import java.util.Set;

/**
 * Thrown when a user attempts to execute a tool they lack the required capabilities for. This is
 * caught by the {@code AssistantService} and returned to the LLM as a tool error result so it can
 * inform the user gracefully.
 */
public class InsufficientToolCapabilityException extends RuntimeException {

  private final String toolName;
  private final Set<String> requiredCapabilities;

  public InsufficientToolCapabilityException(String toolName, Set<String> requiredCapabilities) {
    super(
        "Insufficient capabilities to execute tool \""
            + toolName
            + "\": requires "
            + requiredCapabilities);
    this.toolName = toolName;
    this.requiredCapabilities = Set.copyOf(requiredCapabilities);
  }

  public String getToolName() {
    return toolName;
  }

  public Set<String> getRequiredCapabilities() {
    return requiredCapabilities;
  }
}
