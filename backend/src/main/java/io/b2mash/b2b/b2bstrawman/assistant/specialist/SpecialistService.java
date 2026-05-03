package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantToolRegistry;
import io.b2mash.b2b.b2bstrawman.billing.PlanTier;
import io.b2mash.b2b.b2bstrawman.billing.PlanTierResolver;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Phase 70 / Epic 511B service that backs the {@code /api/assistant/specialists} HTTP endpoints.
 * Pure orchestration over {@link SpecialistRegistry} + {@link PlanTierResolver} + {@link
 * SpecialistSystemPromptLoader}. Read endpoints return DTO projections; the start-session endpoint
 * mints an ephemeral session id and returns the resolved tool subset and a stable system-prompt
 * hash.
 */
@Service
public class SpecialistService {

  private final SpecialistRegistry registry;
  private final SpecialistSystemPromptLoader promptLoader;
  private final PlanTierResolver planTierResolver;
  private final AssistantToolRegistry toolRegistry;

  public SpecialistService(
      SpecialistRegistry registry,
      SpecialistSystemPromptLoader promptLoader,
      PlanTierResolver planTierResolver,
      AssistantToolRegistry toolRegistry) {
    this.registry = registry;
    this.promptLoader = promptLoader;
    this.planTierResolver = planTierResolver;
    this.toolRegistry = toolRegistry;
  }

  /**
   * Returns specialists visible to the current member on {@code surface} ({@code null} or blank
   * means "no surface filter"). Visibility rules: PRO tier + {@code AI_ASSISTANT_USE} capability.
   * Returns an empty list when either gate fails — does NOT throw, so the UI can hide launchers
   * without producing 403s in the page render path.
   */
  public List<SpecialistDtos.SpecialistSummary> listVisible(String surface) {
    var tier = planTierResolver.resolveForCurrentOrg();
    var caps = RequestScopes.getCapabilities();
    return registry.visibleToCapabilities(caps, tier, surface).stream()
        .map(SpecialistDtos::toSummary)
        .toList();
  }

  /**
   * Returns a single specialist by id. Per spec §3.7 a STARTER (or capability-missing) caller
   * receives 403 with an explanatory ProblemDetail; only an unknown id surfaces as 404. The tier +
   * capability gate is therefore checked BEFORE the registry lookup so that an entitled lookup of
   * an unknown id is the only path to 404.
   */
  public SpecialistDtos.SpecialistSummary getOne(String specialistId) {
    requirePro();
    requireAiAssistantUseCapability();
    Specialist specialist;
    try {
      specialist = registry.findById(specialistId);
    } catch (IllegalArgumentException e) {
      throw new ResourceNotFoundException("Specialist", specialistId);
    }
    return SpecialistDtos.toSummary(specialist);
  }

  /**
   * Starts a specialist session. Performs the PRO + capability gate (403 if STARTER or missing
   * capability), looks up the specialist (404 if unknown), resolves the tool subset (intersection
   * of specialist toolIds and capability-allowed tool names), and returns an ephemeral session id +
   * the resolved system-prompt hash so the client can confirm prompt freshness.
   *
   * <p>The session id is a freshly-minted UUID. The chat endpoint does not currently persist
   * session state, so the id is purely client-correlated; persistence belongs to a later slice.
   */
  public SpecialistDtos.StartSessionResponse startSession(
      String specialistId, SpecialistDtos.StartSessionRequest request) {
    requirePro();
    requireAiAssistantUseCapability();
    var caps = RequestScopes.getCapabilities();
    Specialist specialist;
    try {
      specialist = registry.findById(specialistId);
    } catch (IllegalArgumentException e) {
      throw new ResourceNotFoundException("Specialist", specialistId);
    }
    var prompt = promptLoader.loadPrompt(specialist.systemPromptResource());
    var allowedToolNames =
        toolRegistry.getToolsForUser(caps).stream()
            .map(AssistantTool::name)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    var resolvedToolIds =
        SpecialistChatRequestEnricher.intersectToolIds(specialist.toolIds(), allowedToolNames);
    var sessionId = UUID.randomUUID().toString();
    var hash = sha256(prompt.body());
    return new SpecialistDtos.StartSessionResponse(
        sessionId,
        specialist.id(),
        specialist.displayName(),
        "sha256:" + hash,
        resolvedToolIds,
        request != null ? request.contextRef() : null);
  }

  private void requirePro() {
    if (planTierResolver.resolveForCurrentOrg() != PlanTier.PRO) {
      throw new ForbiddenException(
          "Specialist unavailable",
          "AI specialists require a PRO subscription. Please upgrade to access this feature.");
    }
  }

  private void requireAiAssistantUseCapability() {
    Set<String> caps = RequestScopes.getCapabilities();
    if (caps == null || !caps.contains(SpecialistRegistry.CAPABILITY_AI_ASSISTANT_USE)) {
      throw new ForbiddenException(
          "Specialist unavailable", "Your role does not have permission to use AI specialists.");
    }
  }

  private static String sha256(String input) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      var bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
