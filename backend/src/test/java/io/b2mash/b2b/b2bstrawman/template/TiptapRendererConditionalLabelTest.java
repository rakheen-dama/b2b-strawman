package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 89 G88-F16: the four client-facing legal-za templates (statement of account, matter closure
 * letter, client trust statement, trust receipt) carry the fee-note letterhead block, and no label
 * is rendered next to an empty value — "VAT Registration No:" and "File reference:" only appear
 * inside a {@code conditionalBlock} keyed on their value. Renders the shipped pack JSON through the
 * real {@link TiptapRenderer}; no Spring context.
 */
class TiptapRendererConditionalLabelTest {

  private static final Map<String, String> LEGAL_HINTS = Map.of("org.logoUrl", "image");

  private TiptapRenderer renderer;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    renderer = new TiptapRenderer("body { font-size: 11pt; }");
    objectMapper = new ObjectMapper();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> loadLegalTemplate(String key) throws IOException {
    var resource = new ClassPathResource("template-packs/legal-za/" + key + ".json");
    var json = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return objectMapper.readValue(json, Map.class);
  }

  private String render(String key, Map<String, Object> context) throws IOException {
    return renderer.render(loadLegalTemplate(key), context, Map.of(), null, LEGAL_HINTS);
  }

  private static Map<String, Object> fullLetterheadOrg() {
    var org = new LinkedHashMap<String, Object>();
    org.put("name", "Ndlovu Attorneys Inc.");
    org.put("logoUrl", "https://cdn.test/logo.png");
    org.put("address", "88 Fredman Drive, Illovo Point, 3rd Floor, Sandown 2196, South Africa");
    org.put("phone", "+27 11 555 0199");
    org.put("email", "accounts@ndlovu-attorneys.co.za");
    org.put("website", "https://www.ndlovu-attorneys.co.za");
    org.put("taxRegistrationNumber", "4123456789");
    return org;
  }

  /** The org context of a firm with no letterhead set (626B: groups null, address ""). */
  private static Map<String, Object> emptyLetterheadOrg() {
    var org = new HashMap<String, Object>();
    org.put("name", "Ndlovu Attorneys Inc.");
    org.put("logoUrl", null);
    org.put("address", "");
    org.put("addressLines", List.of());
    org.put("phone", null);
    org.put("email", null);
    org.put("website", null);
    org.put("taxRegistrationNumber", null);
    org.put("bankingDetails", null);
    org.put("signatory", null);
    return org;
  }

  private static Map<String, Object> soaContext(
      Map<String, Object> org, Map<String, Object> matter) {
    var context = new HashMap<String, Object>();
    context.put("org", org);
    context.put("matter", matter);
    return context;
  }

  private static String beforeFirst(String html, String marker) {
    int at = html.indexOf(marker);
    assertThat(at).as("marker %s present", marker).isGreaterThanOrEqualTo(0);
    return html.substring(0, at);
  }

  private static int occurrences(String haystack, String needle) {
    int count = 0;
    for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) {
      count++;
    }
    return count;
  }

  @ParameterizedTest
  @NullAndEmptySource
  void statementOfAccount_noTaxNumber_rendersNoVatLabel(String taxNumber) throws IOException {
    var org = emptyLetterheadOrg();
    org.put("taxRegistrationNumber", taxNumber);

    String html = render("statement-of-account", soaContext(org, Map.of("name", "Smith v Jones")));

    assertThat(html).doesNotContain("VAT Reg").doesNotContain("VAT Registration No");
  }

  @Test
  void statementOfAccount_taxNumberSet_rendersTheLabelExactlyOnce() throws IOException {
    var org = emptyLetterheadOrg();
    org.put("taxRegistrationNumber", "4123456789");

    String html = render("statement-of-account", soaContext(org, Map.of("name", "Smith v Jones")));

    assertThat(occurrences(html, "VAT Registration No:")).isEqualTo(1);
    assertThat(html).contains("<p><strong>VAT Registration No:</strong> 4123456789</p>");
    assertThat(html).doesNotContain("VAT Reg:");
  }

  @Test
  void statementOfAccount_emptyFileReference_rendersNoFileReferenceLabel() throws IOException {
    String html =
        render(
            "statement-of-account",
            soaContext(
                emptyLetterheadOrg(), Map.of("name", "Smith v Jones", "file_reference", "")));

    assertThat(html).doesNotContain("File reference");
  }

  @Test
  void statementOfAccount_fileReferenceSet_rendersTheLabelExactlyOnce() throws IOException {
    String html =
        render(
            "statement-of-account",
            soaContext(
                emptyLetterheadOrg(),
                Map.of("name", "Smith v Jones", "file_reference", "MAT-0042")));

    assertThat(occurrences(html, "File reference:")).isEqualTo(1);
    assertThat(html).contains("<p><strong>File reference: </strong>MAT-0042</p>");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "statement-of-account",
        "matter-closure-letter",
        "client-trust-statement",
        "trust-receipt"
      })
  void clientFacingTemplate_fullLetterhead_rendersEveryLine(String key) throws IOException {
    String html = render(key, soaContext(fullLetterheadOrg(), Map.of("name", "Smith v Jones")));

    String letterhead = beforeFirst(html, "<h2>");
    assertThat(letterhead)
        .contains(
            "<p><img class=\"letterhead-logo\" src=\"https://cdn.test/logo.png\" alt=\"Logo\"/></p>")
        .contains("<h1>Ndlovu Attorneys Inc.</h1>")
        .contains("<p>88 Fredman Drive, Illovo Point, 3rd Floor, Sandown 2196, South Africa</p>")
        .contains("<p><strong>Tel:</strong> +27 11 555 0199</p>")
        .contains("<p><strong>Email:</strong> accounts@ndlovu-attorneys.co.za</p>")
        .contains("<p><strong>Web:</strong> https://www.ndlovu-attorneys.co.za</p>")
        .contains("<p><strong>VAT Registration No:</strong> 4123456789</p>")
        .endsWith("<hr/>");
    assertThat(occurrences(html, "VAT Registration No:")).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "statement-of-account",
        "matter-closure-letter",
        "client-trust-statement",
        "trust-receipt"
      })
  void clientFacingTemplate_emptyLetterhead_hidesEveryOptionalLine(String key) throws IOException {
    String html = render(key, soaContext(emptyLetterheadOrg(), Map.of("name", "Smith v Jones")));

    String letterhead = beforeFirst(html, "<h2>");
    assertThat(letterhead)
        .contains("<h1>Ndlovu Attorneys Inc.</h1>")
        .doesNotContain("<img")
        .doesNotContain("Tel:")
        .doesNotContain("Email:")
        .doesNotContain("Web:")
        .doesNotContain("VAT")
        .doesNotContain("<p></p>")
        .endsWith("<hr/>");
  }
}
