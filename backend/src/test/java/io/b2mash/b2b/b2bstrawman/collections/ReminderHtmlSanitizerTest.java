package io.b2mash.b2b.b2bstrawman.collections;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Proves the frame-owns-facts invariant at the HTML boundary: AI-drafted body markup is stripped to
 * a minimal inline allowlist so a hallucinating model or prompt-injected customer data can never
 * forge a link, a fake amount, or a fake "Pay Now" CTA (ADR-327). Pure unit test — the sanitizer is
 * the single chokepoint used by both {@code CollectionReminderSkill.createGates} (pre-gate) and
 * {@code CollectionReminderSendService} (defense in depth at render).
 */
class ReminderHtmlSanitizerTest {

  @Test
  void stripsAnchors_keepingOnlyTheText() {
    String out =
        ReminderHtmlSanitizer.sanitize(
            "<p>Pay here <a href=\"https://evil.example/steal\">Pay Now</a></p>");
    assertThat(out).doesNotContain("<a").doesNotContain("href").doesNotContain("evil.example");
    assertThat(out).contains("Pay Now");
  }

  @Test
  void stripsImagesAndDivsAndTables() {
    String out =
        ReminderHtmlSanitizer.sanitize(
            "<div><img src=\"x\" onerror=\"alert(1)\"/><table><tr><td>R1,000,000 due</td>"
                + "</tr></table><p>hi</p></div>");
    assertThat(out)
        .doesNotContain("<img")
        .doesNotContain("<div")
        .doesNotContain("<table")
        .doesNotContain("onerror");
    // Text content survives even when its wrapping/forging tags are removed.
    assertThat(out).contains("R1,000,000 due").contains("hi");
  }

  @Test
  void stripsAllAttributesFromAllowedTags() {
    String out =
        ReminderHtmlSanitizer.sanitize("<p style=\"color:red\" onclick=\"x()\">reminder</p>");
    assertThat(out).contains("<p>reminder</p>");
    assertThat(out).doesNotContain("style").doesNotContain("onclick");
  }

  @Test
  void keepsAllowlistedInlineTags() {
    String out =
        ReminderHtmlSanitizer.sanitize(
            "<p>Please pay <strong>promptly</strong> — <em>thank you</em>.<br>Regards</p>");
    assertThat(out).contains("<p>").contains("<strong>").contains("<em>").contains("<br>");
  }

  @Test
  void nullOrBlankBecomesEmptyString() {
    assertThat(ReminderHtmlSanitizer.sanitize(null)).isEmpty();
    assertThat(ReminderHtmlSanitizer.sanitize("   ")).isEmpty();
  }
}
