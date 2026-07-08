package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;

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
