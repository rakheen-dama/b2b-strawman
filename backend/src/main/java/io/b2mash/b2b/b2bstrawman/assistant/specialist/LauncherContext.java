package io.b2mash.b2b.b2bstrawman.assistant.specialist;

/**
 * Where a specialist surfaces in the UI: which route, which discrete UI surface (used as an i18n
 * discriminator and as the filter key for {@link SpecialistRegistry#visibleTo}), and the
 * call-to-action label key shown on the inline launcher button.
 *
 * <p>Per architecture phase70 §2.1.
 */
public record LauncherContext(String route, String surface, String ctaLabel) {}
