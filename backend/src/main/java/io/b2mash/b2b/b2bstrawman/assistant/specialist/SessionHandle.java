package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import java.util.List;
import java.util.UUID;

/**
 * Result returned from {@link SpecialistSessionService#start} — used by the frontend to bootstrap
 * the chat panel with the correct specialist, tool subset, and pre-seeded greeting.
 *
 * @param sessionId opaque session identifier (used by 515A to correlate audit rows)
 * @param specialistId the specialist that was started
 * @param systemPromptHash content hash of the assembled system prompt (advisory — used to detect
 *     prompt drift across reload)
 * @param toolIds tool names the specialist may invoke for this caller (post capability filter)
 * @param displayName label to show in the chat header
 * @param preSeededAssistantMessage optional first assistant message (e.g. "Hi, I'm the Billing
 *     specialist. What can I help with?") — may be {@code null}
 */
public record SessionHandle(
    UUID sessionId,
    String specialistId,
    String systemPromptHash,
    List<String> toolIds,
    String displayName,
    String preSeededAssistantMessage) {

  public SessionHandle {
    toolIds = toolIds != null ? List.copyOf(toolIds) : List.of();
  }
}
