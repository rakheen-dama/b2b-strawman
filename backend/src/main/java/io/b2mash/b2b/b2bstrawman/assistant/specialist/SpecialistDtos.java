package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import java.util.List;
import java.util.Map;

/** Phase 70 specialist API DTOs (request/response records). */
public final class SpecialistDtos {

  private SpecialistDtos() {}

  /** Summary projection returned by the list and detail endpoints. */
  public record SpecialistSummary(
      String id,
      String displayName,
      String tagline,
      List<String> toolIds,
      List<LauncherSummary> launchers,
      boolean automationCapable) {}

  /** Launcher projection — the route + surface key + i18n cta key. */
  public record LauncherSummary(String route, String surface, String ctaLabel) {}

  /** Optional context reference passed by the launcher (entity-scoped specialist sessions). */
  public record ContextRef(String entityType, String entityId, Map<String, Object> attributes) {}

  /** Start-session request body. Both fields are optional. */
  public record StartSessionRequest(ContextRef contextRef, String initialPrompt) {}

  /** Start-session response. {@code resolvedToolIds} is the capability-narrowed subset. */
  public record StartSessionResponse(
      String sessionId,
      String specialistId,
      String displayName,
      String systemPromptHash,
      List<String> resolvedToolIds,
      ContextRef contextRef) {}

  static SpecialistSummary toSummary(Specialist specialist) {
    return new SpecialistSummary(
        specialist.id(),
        specialist.displayName(),
        specialist.tagline(),
        specialist.toolIds(),
        specialist.launchers().stream()
            .map(l -> new LauncherSummary(l.route(), l.surface(), l.ctaLabel()))
            .toList(),
        specialist.automationCapable());
  }
}
