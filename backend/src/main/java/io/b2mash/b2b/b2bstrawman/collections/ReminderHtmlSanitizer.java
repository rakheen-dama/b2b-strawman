package io.b2mash.b2b.b2bstrawman.collections;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * Sanitizes AI-drafted collection-reminder body HTML down to a minimal inline allowlist so a
 * hallucinating model — or prompt-injection smuggled in through customer data — can never emit
 * markup that forges facts, injects a link, or plants a fake "Pay Now" CTA inside the frame-owned
 * email (frame-owns-facts, ADR-327). The AI owns ONLY the letter paragraphs; every fact, table and
 * CTA is template-rendered.
 *
 * <p>Allowlist: paragraph / line-break plus basic emphasis only — NO {@code a}, {@code table},
 * {@code div}, {@code img}, and NO attributes of any kind. Applied at two points: when the draft
 * enters the gate's {@code proposed_action} (so the approver reviews exactly what will be sent) and
 * defensively again at render time (defense in depth).
 */
public final class ReminderHtmlSanitizer {

  private static final Safelist SAFELIST = new Safelist().addTags("p", "br", "strong", "em");

  private ReminderHtmlSanitizer() {}

  /**
   * Strips {@code html} to the allowlist above. Returns {@code ""} for null/blank input so callers
   * never store a null gate value or render a literal "null".
   */
  public static String sanitize(String html) {
    if (html == null || html.isBlank()) {
      return "";
    }
    return Jsoup.clean(html, SAFELIST);
  }
}
