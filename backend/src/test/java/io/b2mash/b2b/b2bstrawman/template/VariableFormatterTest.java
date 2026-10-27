package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class VariableFormatterTest {

  @Test
  void currency_formats_with_rand_sign_and_spaces() {
    assertThat(VariableFormatter.format(50000.00, "currency")).isEqualTo("R50&nbsp;000,00");
  }

  @Test
  void currency_formats_string_value() {
    assertThat(VariableFormatter.format("1234.56", "currency")).isEqualTo("R1&nbsp;234,56");
  }

  @Test
  void currency_formats_zero() {
    assertThat(VariableFormatter.format(0, "currency")).isEqualTo("R0,00");
  }

  @Test
  void currencyText_is_plain_text_not_html_escaped() {
    // 625B: the activity feed is rendered verbatim by React, so the plain-text variant keeps the
    // JDK's U+00A0 grouping separator instead of "&nbsp;".
    assertThat(
            VariableFormatter.formatCurrencyText(
                "50000.00", VariableFormatter.resolveLocale("ZAR")))
        .isEqualTo("R50\u00a0000,00")
        .doesNotContain("&");
    assertThat(VariableFormatter.formatCurrencyText(new java.math.BigDecimal("1500"), null))
        .isEqualTo("R1\u00a0500,00");
    assertThat(VariableFormatter.formatCurrencyText("1500", VariableFormatter.resolveLocale("USD")))
        .isEqualTo("$1,500.00");
    assertThat(VariableFormatter.formatCurrencyText(null, null)).isEmpty();
    assertThat(VariableFormatter.formatCurrencyText("R&D", null)).isEqualTo("R&D");
  }

  @Test
  void currency_invalid_number_falls_back_to_raw() {
    assertThat(VariableFormatter.format("not-a-number", "currency")).isEqualTo("not-a-number");
  }

  @Test
  void date_formats_iso_local_date() {
    assertThat(VariableFormatter.format("2026-03-08", "date")).isEqualTo("8 March 2026");
  }

  @Test
  void date_formats_iso_instant() {
    assertThat(VariableFormatter.format("2026-03-08T14:30:00Z", "date")).isEqualTo("8 March 2026");
  }

  @Test
  void date_formats_iso_instant_near_midnight_utc() {
    assertThat(VariableFormatter.format("2026-01-01T00:00:00Z", "date"))
        .isEqualTo("1 January 2026");
  }

  @Test
  void dateText_is_plain_text_and_document_date_wraps_it() {
    assertThat(VariableFormatter.formatDateText(LocalDate.of(2026, 3, 8)))
        .isEqualTo("8 March 2026");
    assertThat(VariableFormatter.formatDateText("2026-03-08T14:30:00Z")).isEqualTo("8 March 2026");
    assertThat(VariableFormatter.formatDateText(null)).isEmpty();
    // Unparsable: text variant is raw, document variant is the same text HTML-escaped.
    assertThat(VariableFormatter.formatDateText("<b>&</b>")).isEqualTo("<b>&</b>");
    assertThat(VariableFormatter.format("<b>&</b>", "date")).isEqualTo("&lt;b&gt;&amp;&lt;/b&gt;");
  }

  @Test
  void date_invalid_falls_back_to_raw_escaped() {
    assertThat(VariableFormatter.format("not-a-date", "date")).isEqualTo("not-a-date");
  }

  @Test
  void number_formats_with_spaces() {
    assertThat(VariableFormatter.format(1234567, "number")).isEqualTo("1&nbsp;234&nbsp;567");
  }

  @Test
  void number_formats_decimal() {
    assertThat(VariableFormatter.format("1234567.89", "number"))
        .isEqualTo("1&nbsp;234&nbsp;567,89");
  }

  @Test
  void number_invalid_falls_back_to_raw() {
    assertThat(VariableFormatter.format("abc", "number")).isEqualTo("abc");
  }

  @Test
  void null_value_returns_empty_string() {
    assertThat(VariableFormatter.format(null, "currency")).isEmpty();
    assertThat(VariableFormatter.format(null, "date")).isEmpty();
    assertThat(VariableFormatter.format(null, "number")).isEmpty();
    assertThat(VariableFormatter.format(null, null)).isEmpty();
  }

  @Test
  void null_type_hint_returns_raw_escaped() {
    assertThat(VariableFormatter.format("Hello", null)).isEqualTo("Hello");
  }

  @Test
  void string_type_hint_returns_raw_escaped() {
    assertThat(VariableFormatter.format("Hello", "string")).isEqualTo("Hello");
  }

  @Test
  void unknown_type_hint_returns_raw_escaped() {
    assertThat(VariableFormatter.format("value", "unknown")).isEqualTo("value");
  }

  // --- LZKC-007/017: "image" type hint renders an <img> element (letterhead logo) ---

  @Test
  void image_type_hint_renders_img_element_with_escaped_src() {
    assertThat(
            VariableFormatter.format(
                "http://test-storage/test-bucket/logos/org/logo.png?download=true", "image"))
        .isEqualTo(
            "<img class=\"letterhead-logo\""
                + " src=\"http://test-storage/test-bucket/logos/org/logo.png?download=true\""
                + " alt=\"Logo\"/>");
  }

  @Test
  void image_type_hint_escapes_html_metacharacters_in_url() {
    assertThat(VariableFormatter.format("https://x.test/logo.png?a=1&b=\"2\"", "image"))
        .isEqualTo(
            "<img class=\"letterhead-logo\""
                + " src=\"https://x.test/logo.png?a=1&amp;b=&quot;2&quot;\" alt=\"Logo\"/>");
  }

  @Test
  void image_type_hint_rejects_non_http_urls() {
    assertThat(VariableFormatter.format("javascript:alert(1)", "image")).isEmpty();
    assertThat(VariableFormatter.format("data:text/html,x", "image")).isEmpty();
    assertThat(VariableFormatter.format("  ", "image")).isEmpty();
  }

  /**
   * Phase 88 626D (ruling R-1): {@code org.logoUrl} may be a {@code data:} URI of the logo bytes
   * (PNG/JPEG) so the PDF renderer never fetches the logo over the network.
   */
  @Test
  void image_type_hint_accepts_strict_png_and_jpeg_data_uris() {
    assertThat(VariableFormatter.format("data:image/png;base64,iVBORw0KGgo=", "image"))
        .isEqualTo(
            "<img class=\"letterhead-logo\" src=\"data:image/png;base64,iVBORw0KGgo=\""
                + " alt=\"Logo\"/>");
    assertThat(VariableFormatter.format("data:image/jpeg;base64,/9j/4AAQSkZJRg==", "image"))
        .isEqualTo(
            "<img class=\"letterhead-logo\" src=\"data:image/jpeg;base64,/9j/4AAQSkZJRg==\""
                + " alt=\"Logo\"/>");
  }

  @Test
  void image_type_hint_rejects_other_data_uris() {
    assertThat(VariableFormatter.format("data:image/svg+xml;base64,PHN2Zz4=", "image")).isEmpty();
    assertThat(VariableFormatter.format("data:text/html;base64,PHNjcmlwdD4=", "image")).isEmpty();
    assertThat(VariableFormatter.format("data:image/png,rawbytes", "image")).isEmpty();
    assertThat(VariableFormatter.format("data:image/png;base64,abc\"onerror=x", "image")).isEmpty();
    assertThat(VariableFormatter.format("data:image/png;base64,", "image")).isEmpty();
    assertThat(VariableFormatter.format("javascript:alert(1)", "image")).isEmpty();
  }

  @Test
  void image_type_hint_null_value_returns_empty_string() {
    assertThat(VariableFormatter.format(null, "image")).isEmpty();
  }

  @Test
  void html_characters_are_escaped() {
    assertThat(VariableFormatter.format("<script>alert(1)</script>", null))
        .isEqualTo("&lt;script&gt;alert(1)&lt;/script&gt;");
  }

  @Test
  void html_characters_escaped_in_string_type() {
    assertThat(VariableFormatter.format("<b>bold</b>", "string"))
        .isEqualTo("&lt;b&gt;bold&lt;/b&gt;");
  }
}
