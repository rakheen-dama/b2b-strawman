package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import io.b2mash.b2b.b2bstrawman.assistant.provider.ToolDefinition;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Phase 70 / Epic 511B. Adapts a generic chat request to a specialist's narrowed context: prepends
 * the specialist system prompt to the base prompt and intersects the specialist's declared {@code
 * toolIds} with the caller's capability-allowed tool list.
 *
 * <p>Capability filtering is performed inline (the existing {@code CapabilityAuthorizationService}
 * does not expose a list-filter primitive — see PR description for the architectural rationale).
 * The caller passes the already-capability-filtered tool list; the enricher simply narrows it to
 * the specialist's declared subset.
 */
@Service
public class SpecialistChatRequestEnricher {

  private final SpecialistRegistry registry;
  private final SpecialistSystemPromptLoader promptLoader;

  public SpecialistChatRequestEnricher(
      SpecialistRegistry registry, SpecialistSystemPromptLoader promptLoader) {
    this.registry = registry;
    this.promptLoader = promptLoader;
  }

  /**
   * Returns the enriched system prompt + narrowed tool list for the given specialist.
   *
   * @param baseSystemPrompt the generalist system prompt (kept as a suffix for shared behavioural
   *     instructions and the tenant context block)
   * @param capabilityFilteredTools tools already filtered by the caller's capability set (typically
   *     produced by {@code
   *     AssistantToolRegistry.getToolDefinitions(RequestScopes.getCapabilities())})
   * @param specialistId stable specialist id (e.g. {@code "BILLING"})
   * @throws ResourceNotFoundException if no specialist is registered with the given id
   */
  public EnrichedChatInputs enrich(
      String baseSystemPrompt, List<ToolDefinition> capabilityFilteredTools, String specialistId) {
    Specialist specialist;
    try {
      specialist = registry.findById(specialistId);
    } catch (IllegalArgumentException e) {
      throw new ResourceNotFoundException("Specialist", specialistId);
    }

    var prompt = promptLoader.loadPrompt(specialist.systemPromptResource());
    var enrichedPrompt = prompt.body() + "\n\n---\n\n" + baseSystemPrompt;

    var specialistToolIds = Set.copyOf(specialist.toolIds());
    var narrowed =
        capabilityFilteredTools.stream()
            .filter(td -> specialistToolIds.contains(td.name()))
            .toList();

    return new EnrichedChatInputs(enrichedPrompt, narrowed);
  }

  /**
   * Returns the intersection of the specialist's declared {@code toolIds} with the names of the
   * caller's capability-allowed tools, preserving the specialist's declaration order. Shared by the
   * chat-extension path (which intersects {@link ToolDefinition} objects) and the start-session
   * endpoint (which only needs the surviving tool-id strings). Pure helper, no side effects.
   */
  public static List<String> intersectToolIds(
      List<String> specialistToolIds, Set<String> allowedToolNames) {
    return specialistToolIds.stream().filter(allowedToolNames::contains).toList();
  }

  /** Result of {@link #enrich(String, List, String)}. */
  public record EnrichedChatInputs(String systemPrompt, List<ToolDefinition> toolDefs) {}
}
