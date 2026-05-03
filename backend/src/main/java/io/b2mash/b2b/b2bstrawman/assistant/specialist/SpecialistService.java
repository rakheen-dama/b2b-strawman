package io.b2mash.b2b.b2bstrawman.assistant.specialist;

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

  /** Returns a single specialist by id, gated by visibility. 404 if hidden, 404 if unknown. */
  public SpecialistDtos.SpecialistSummary getOne(String specialistId) {
    var tier = planTierResolver.resolveForCurrentOrg();
    var caps = RequestScopes.getCapabilities();
    var visible = registry.visibleToCapabilities(caps, tier, null);
    return visible.stream()
        .filter(s -> s.id().equals(specialistId))
        .findFirst()
        .map(SpecialistDtos::toSummary)
        .orElseThrow(() -> new ResourceNotFoundException("Specialist", specialistId));
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
    var tier = planTierResolver.resolveForCurrentOrg();
    if (tier != PlanTier.PRO) {
      throw new ForbiddenException(
          "Specialist unavailable",
          "AI specialists require a PRO subscription. Please upgrade to access this feature.");
    }
    var caps = RequestScopes.getCapabilities();
    if (!caps.contains(SpecialistRegistry.CAPABILITY_AI_ASSISTANT_USE)) {
      throw new ForbiddenException(
          "Specialist unavailable", "Your role does not have permission to use AI specialists.");
    }
    Specialist specialist;
    try {
      specialist = registry.findById(specialistId);
    } catch (IllegalArgumentException e) {
      throw new ResourceNotFoundException("Specialist", specialistId);
    }
    var prompt = promptLoader.loadPrompt(specialist.systemPromptResource());
    var allowedToolNames = toolRegistry.getToolsForUser(caps).stream().map(t -> t.name()).toList();
    var allowed = java.util.Set.copyOf(allowedToolNames);
    var resolvedToolIds = specialist.toolIds().stream().filter(allowed::contains).toList();
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
