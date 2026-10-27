package io.b2mash.b2b.b2bstrawman.template;

import java.util.regex.Pattern;

/**
 * Validates the firm's brand colour before it is interpolated into document CSS (Phase 88 U8,
 * ADR-353, architecture §13.6.4). OpenHTMLToPDF has no CSS custom properties, so the colour reaches
 * the PDF only as a concrete rule string; the stored value is firm-entered and must never be able
 * to close a {@code <style>} block or smuggle a {@code url()}. Whole-string match ({@code
 * matches()}, not {@code find()}): a trailing newline is rejected.
 *
 * <p>Write-time validation already exists ({@code UpdateSettingsRequest} {@code @Pattern}, the
 * {@code chk_brand_color_*} DB checks); this render-time check is defence in depth for contexts
 * that do not come from the database (audit export, unit tests, future callers).
 */
public final class BrandColour {

  /** Heading / rule colour when no valid brand colour is set (matches document-default.css). */
  public static final String DEFAULT = "#1a1a2e";

  private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");

  private BrandColour() {}

  /** Returns {@code candidate} when it is exactly {@code #RRGGBB}, else {@code fallback}. */
  public static String validHexOrDefault(String candidate, String fallback) {
    return candidate != null && HEX.matcher(candidate).matches() ? candidate : fallback;
  }
}
