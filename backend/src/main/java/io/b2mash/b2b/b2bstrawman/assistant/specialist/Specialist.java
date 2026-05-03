package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import java.util.List;

/**
 * Defines a specialist AI assistant — a focused persona with a curated tool subset, vertical-aware
 * system prompt, and surface launchers that decide where the specialist appears in the UI.
 *
 * <p>Constructed via {@code @Bean} factories in {@code *SpecialistConfig} classes; collected by
 * {@link SpecialistRegistry} at startup.
 *
 * @param id stable identifier (e.g. {@code "billing-za"}) — also the markdown filename stem
 * @param displayName human-friendly label shown in the launcher
 * @param tagline short blurb shown in the launcher
 * @param systemPromptResource classpath path to the markdown file containing front-matter + body
 * @param toolIds whitelisted tool names this specialist may invoke (intersected with the caller's
 *     resolved capabilities at session start)
 * @param launchers UI surfaces where this specialist may be offered
 * @param automationCapable reserved for future automation-mode work; currently advisory
 * @param maxToolIterations safety bound for the multi-turn tool loop
 * @param directModeAllowedTools ADR-267 hardening placeholder — empty default in 511A
 */
public record Specialist(
    String id,
    String displayName,
    String tagline,
    String systemPromptResource,
    List<String> toolIds,
    List<LauncherContext> launchers,
    boolean automationCapable,
    int maxToolIterations,
    List<String> directModeAllowedTools) {

  public Specialist {
    toolIds = toolIds != null ? List.copyOf(toolIds) : List.of();
    launchers = launchers != null ? List.copyOf(launchers) : List.of();
    directModeAllowedTools =
        directModeAllowedTools != null ? List.copyOf(directModeAllowedTools) : List.of();
  }

  /**
   * Convenience constructor with default {@code maxToolIterations=8} (per arch §3.1 supplement).
   */
  public Specialist(
      String id,
      String displayName,
      String tagline,
      String systemPromptResource,
      List<String> toolIds,
      List<LauncherContext> launchers,
      boolean automationCapable) {
    this(
        id,
        displayName,
        tagline,
        systemPromptResource,
        toolIds,
        launchers,
        automationCapable,
        8,
        List.of());
  }
}
