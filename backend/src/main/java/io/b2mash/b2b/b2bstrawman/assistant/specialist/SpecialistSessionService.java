package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantToolRegistry;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationGuardService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Starts a specialist chat session: validates AI integration is enabled, resolves the specialist's
 * tool subset against the caller's capabilities, builds the system prompt, and returns a {@link
 * SessionHandle}.
 *
 * <p>511A scope: in-memory only — persistence of {@code AiSpecialistInvocation} lands in 515A.
 */
@Service
public class SpecialistSessionService {

  private final SpecialistRegistry specialistRegistry;
  private final AssistantToolRegistry assistantToolRegistry;
  private final SystemPromptBuilder systemPromptBuilder;
  private final IntegrationGuardService integrationGuardService;

  public SpecialistSessionService(
      SpecialistRegistry specialistRegistry,
      AssistantToolRegistry assistantToolRegistry,
      SystemPromptBuilder systemPromptBuilder,
      IntegrationGuardService integrationGuardService) {
    this.specialistRegistry = specialistRegistry;
    this.assistantToolRegistry = assistantToolRegistry;
    this.systemPromptBuilder = systemPromptBuilder;
    this.integrationGuardService = integrationGuardService;
  }

  public SessionHandle start(String specialistId, ContextRef ref, String initialPrompt) {
    integrationGuardService.requireEnabled(IntegrationDomain.AI);
    var specialist = specialistRegistry.requireById(specialistId);
    var capabilities = RequestScopes.getCapabilities();
    var filteredTools =
        assistantToolRegistry.filterBy(specialist.toolIds(), capabilities).stream()
            .map(AssistantTool::name)
            .toList();
    // Hash only the static specialist body — full prompt assembly happens at /chat time.
    var promptHash = systemPromptBuilder.bodyHash(specialistId);
    var sessionId = UUID.randomUUID();
    var greeting =
        initialPrompt != null && !initialPrompt.isBlank()
            ? null
            : "Hi, I'm the " + specialist.displayName() + ". " + specialist.tagline();
    return new SessionHandle(
        sessionId, specialistId, promptHash, filteredTools, specialist.displayName(), greeting);
  }
}
