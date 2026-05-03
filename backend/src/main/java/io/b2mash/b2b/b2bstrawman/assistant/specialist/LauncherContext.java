package io.b2mash.b2b.b2bstrawman.assistant.specialist;

/**
 * UI surface where a {@link Specialist} may be offered as a launcher.
 *
 * @param route route prefix to match against the caller's current page (e.g. {@code "/billing"})
 * @param surface logical surface tag (e.g. {@code "billing"}, {@code "intake"})
 * @param ctaLabel call-to-action label rendered on the launcher button
 */
public record LauncherContext(String route, String surface, String ctaLabel) {}
