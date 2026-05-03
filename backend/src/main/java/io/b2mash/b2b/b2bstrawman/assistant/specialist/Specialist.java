package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import java.util.List;

/**
 * In-code definition of an AI specialist. Loaded into {@link SpecialistRegistry} at startup.
 *
 * <p>Per architecture phase70 §2.1.
 *
 * @param id stable id (e.g. {@code "BILLING"})
 * @param displayName i18n key for the display name
 * @param tagline i18n key for the tagline
 * @param systemPromptResource classpath path to the markdown prompt resource
 * @param toolIds names of tools (subset of {@code AssistantToolRegistry}) the specialist may call
 * @param launchers UI surfaces that render an inline launcher for this specialist
 * @param automationCapable whether DIRECT-mode (autonomous) execution is allowed (only INBOX, per
 *     ADR-267)
 * @param maxToolIterations runner cap on tool-loop iterations; default 8 (per §3.1)
 */
public record Specialist(
    String id,
    String displayName,
    String tagline,
    String systemPromptResource,
    List<String> toolIds,
    List<LauncherContext> launchers,
    boolean automationCapable,
    int maxToolIterations) {

  public Specialist {
    toolIds = List.copyOf(toolIds);
    launchers = List.copyOf(launchers);
  }
}
