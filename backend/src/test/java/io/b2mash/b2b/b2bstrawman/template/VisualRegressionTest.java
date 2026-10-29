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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

/**
 * Visual regression tests for platform JSON pack templates rendered via {@link TiptapRenderer}.
 *
 * <p>Each test loads a real template JSON from the classpath, builds a context with known test
 * data, renders via TiptapRenderer, and asserts the output HTML contains expected structural
 * elements (headings, resolved variables, bold labels, paragraphs).
 */
class VisualRegressionTest {

  private TiptapRenderer renderer;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    renderer = new TiptapRenderer("body { font-size: 11pt; }");
    objectMapper = new ObjectMapper();
  }

  private Map<String, Object> loadPackTemplate(String name) throws IOException {
    return loadPackTemplate("common", name);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> loadPackTemplate(String pack, String name) throws IOException {
    var resource = new ClassPathResource("template-packs/" + pack + "/" + name + ".json");
    var json = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return objectMapper.readValue(json, Map.class);
  }

  @Test
  void engagementLetter_rendersExpectedStructure() throws IOException {
    var doc = loadPackTemplate("engagement-letter");

    var context = new HashMap<String, Object>();
    context.put("org", Map.of("name", "Acme Corp", "documentFooterText", "Confidential"));
    context.put("customer", Map.of("name", "Jane Smith"));
    context.put(
        "project",
        Map.of(
            "name", "Website Redesign",
            "status", "IN_PROGRESS",
            "description", "Full redesign of corporate website"));

    String html = renderer.render(doc, context, Map.of(), null);

    // Full HTML document structure
    assertThat(html).startsWith("<!DOCTYPE html>");
    assertThat(html).contains("<html>");
    assertThat(html).contains("</body></html>");

    // Heading with org name variable resolved
    assertThat(html).contains("<h1>Acme Corp</h1>");
    // Static heading
    assertThat(html).contains("<h2>Engagement Letter</h2>");
    // Customer greeting
    assertThat(html).contains("Dear ");
    assertThat(html).contains("Jane Smith");
    // Project name resolved
    assertThat(html).contains("Website Redesign");
    // Bold labels
    assertThat(html).contains("<strong>Matter:</strong>");
    assertThat(html).contains("<strong>Status:</strong>");
    assertThat(html).contains("<strong>Description:</strong>");
    // Variable values
    assertThat(html).contains("IN_PROGRESS");
    assertThat(html).contains("Full redesign of corporate website");
    // Footer
    assertThat(html).contains("Confidential");
  }

  @Test
  void projectSummary_rendersExpectedStructure() throws IOException {
    var doc = loadPackTemplate("project-summary");

    var context = new HashMap<String, Object>();
    context.put(
        "org", Map.of("name", "Legal Partners LLP", "documentFooterText", "Internal Use Only"));
    context.put("customer", Map.of("name", "BigCo Inc"));
    context.put(
        "project",
        Map.of(
            "name", "Due Diligence Review",
            "status", "ACTIVE",
            "description", "Comprehensive review of acquisition target",
            "lead", "Alice Johnson"));

    String html = renderer.render(doc, context, Map.of(), null);

    // Full HTML document
    assertThat(html).startsWith("<!DOCTYPE html>");

    // Heading with org name
    assertThat(html).contains("<h1>Legal Partners LLP</h1>");
    // Static heading
    assertThat(html).contains("<h2>Matter Summary Report</h2>");
    // Project name in h3
    assertThat(html).contains("<h3>Due Diligence Review</h3>");
    // Customer
    assertThat(html).contains("<strong>Client:</strong>");
    assertThat(html).contains("BigCo Inc");
    // Status
    assertThat(html).contains("<strong>Status:</strong>");
    assertThat(html).contains("ACTIVE");
    // Description
    assertThat(html).contains("Comprehensive review of acquisition target");
    // Lead
    assertThat(html).contains("<strong>Matter Lead:</strong>");
    assertThat(html).contains("Alice Johnson");
    // Footer
    assertThat(html).contains("Internal Use Only");
  }

  @Test
  void invoiceCoverLetter_rendersExpectedStructure() throws IOException {
    var doc = loadPackTemplate("invoice-cover-letter");

    var context = new HashMap<String, Object>();
    context.put(
        "org",
        Map.of("name", "Consulting Group SA", "documentFooterText", "Thank you for your business"));
    context.put("customer", Map.of("name", "Client Corp"));
    // LZKC-010: the template's variable key is invoice.invoiceNumber (matching
    // InvoiceContextBuilder); the old "number" key was the placeholder typo that rendered blank.
    context.put("invoice", Map.of("invoiceNumber", "INV-2026-001", "total", "R 15,000.00"));

    String html = renderer.render(doc, context, Map.of(), null);

    // Full HTML document
    assertThat(html).startsWith("<!DOCTYPE html>");

    // Heading with org name
    assertThat(html).contains("<h1>Consulting Group SA</h1>");
    // Static heading
    assertThat(html).contains("<h2>Fee Note Cover Letter</h2>");
    // Customer greeting
    assertThat(html).contains("Dear ");
    assertThat(html).contains("Client Corp");
    // Static body text
    assertThat(html).contains("Please find attached the fee note for services rendered.");
    // Invoice number
    assertThat(html).contains("<strong>Fee Note Number:</strong>");
    assertThat(html).contains("INV-2026-001");
    // Total amount
    assertThat(html).contains("<strong>Total Amount:</strong>");
    assertThat(html).contains("R 15,000.00");
    // Closing text
    assertThat(html).contains("Thank you for your continued business.");
    // Footer
    assertThat(html).contains("Thank you for your business");
  }

  // --- Brand colour (Phase 88 626B, U8, ADR-353, §13.6.4) — HTML-level goldens (ruling R-6) ---

  private Map<String, Object> engagementContext(Map<String, Object> org) {
    var context = new HashMap<String, Object>();
    context.put("org", org);
    context.put("customer", Map.of("name", "Jane Smith"));
    context.put(
        "project",
        Map.of(
            "name", "Website Redesign",
            "status", "IN_PROGRESS",
            "description", "Full redesign of corporate website"));
    return context;
  }

  @Test
  void engagementLetter_carriesBrandRules() throws IOException {
    var doc = loadPackTemplate("engagement-letter");
    var org = new HashMap<String, Object>();
    org.put("name", "Acme Corp");
    org.put("brandColor", "#0f766e");

    String html = renderer.render(doc, engagementContext(org), Map.of(), null);

    assertThat(html)
        .contains("h1, h2, h3 { color: #0f766e; }")
        .contains("hr, .rule { border-color: #0f766e; }")
        .contains("hr { border-style: solid; }")
        .contains(".letterhead { border-bottom: 2px solid #0f766e; }")
        .contains("<h1>Acme Corp</h1>")
        .contains("<h2>Engagement Letter</h2>");
    // D5: table headers keep the neutral palette — no brand rule targets th/table.
    assertThat(html).doesNotContain("th { color: #0f766e").doesNotContain("th, ");
  }

  @Test
  void engagementLetter_defaultBrandWhenUnset() throws IOException {
    var doc = loadPackTemplate("engagement-letter");

    String html =
        renderer.render(doc, engagementContext(Map.of("name", "Acme Corp")), Map.of(), null);

    assertThat(html)
        .contains("h1, h2, h3 { color: #1a1a2e; }")
        .contains("hr, .rule { border-color: #1a1a2e; }")
        .contains(".letterhead { border-bottom: 2px solid #1a1a2e; }")
        .doesNotContain("#0f766e");
  }

  // --- legal-za letterhead / banking / signatory (Phase 88 626D, U8, ADR-353) — HTML goldens ---

  private static final Map<String, String> LEGAL_HINTS =
      Map.of(
          "org.logoUrl", "image",
          "invoice.issueDate", "date",
          "invoice.dueDate", "date");

  private static Map<String, Object> fullLetterheadOrg() {
    var org = new LinkedHashMap<String, Object>();
    org.put("name", "Ndlovu Attorneys Inc.");
    org.put("logoUrl", "https://cdn.test/logo.png");
    org.put("address", "88 Fredman Drive, Illovo Point, 3rd Floor, Sandown 2196, South Africa");
    org.put("phone", "+27 11 555 0199");
    org.put("email", "accounts@ndlovu-attorneys.co.za");
    org.put("website", "https://www.ndlovu-attorneys.co.za");
    org.put("taxRegistrationNumber", "4123456789");
    var banking = new LinkedHashMap<String, Object>();
    banking.put("bankName", "Standard Bank");
    banking.put("branchCode", "051001");
    banking.put("accountNumber", "0012345678");
    banking.put("accountType", "Cheque");
    banking.put("referenceNote", "Please use your fee note number as the payment reference.");
    org.put("bankingDetails", banking);
    org.put("signatory", Map.of("name", "Sipho Ndlovu", "designation", "Managing Partner"));
    org.put("documentFooterText", "Confidential");
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

  private static String beforeFirst(String html, String marker) {
    int at = html.indexOf(marker);
    assertThat(at).as("marker %s present", marker).isGreaterThanOrEqualTo(0);
    return html.substring(0, at);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "engagement-letter-general",
        "engagement-letter-litigation",
        "engagement-letter-conveyancing"
      })
  void legalEngagementLetter_fullLetterhead_andSignatory(String key) throws IOException {
    var context = engagementContext(fullLetterheadOrg());

    String html =
        renderer.render(loadPackTemplate("legal-za", key), context, Map.of(), null, LEGAL_HINTS);

    String letterhead = beforeFirst(html, "<h2>");
    assertThat(letterhead)
        .contains(
            "<p><img class=\"letterhead-logo\" src=\"https://cdn.test/logo.png\" alt=\"Logo\"/></p>")
        .contains("<h1>Ndlovu Attorneys Inc.</h1>")
        .contains("<p>88 Fredman Drive, Illovo Point, 3rd Floor, Sandown 2196, South Africa</p>")
        .contains("<p><strong>Tel:</strong> +27 11 555 0199</p>")
        .contains("<p><strong>Email:</strong> accounts@ndlovu-attorneys.co.za</p>")
        .contains("<p><strong>Web:</strong> https://www.ndlovu-attorneys.co.za</p>")
        .contains("<p><strong>VAT No:</strong> 4123456789</p>")
        .endsWith("<hr/>");
    assertThat(html)
        .contains(
            "<p>Yours faithfully,</p><p>Sipho Ndlovu</p><p>Managing Partner</p>"
                + "<p>Ndlovu Attorneys Inc.</p>");
    // D5: table headers keep the neutral style (no brand rule on th).
    assertThat(html).doesNotContain("th { color");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "engagement-letter-general",
        "engagement-letter-litigation",
        "engagement-letter-conveyancing"
      })
  void legalEngagementLetter_emptyLetterhead_hidesEveryOptionalLine(String key) throws IOException {
    var context = engagementContext(emptyLetterheadOrg());

    String html =
        renderer.render(loadPackTemplate("legal-za", key), context, Map.of(), null, LEGAL_HINTS);

    String letterhead = beforeFirst(html, "<h2>");
    assertThat(letterhead)
        .contains("<h1>Ndlovu Attorneys Inc.</h1>")
        .doesNotContain("<img")
        .doesNotContain("Tel:")
        .doesNotContain("Email:")
        .doesNotContain("Web:")
        .doesNotContain("VAT No")
        .doesNotContain("<p></p>")
        .endsWith("<hr/>");
    assertThat(html).contains("<p>Yours faithfully,</p><p>Ndlovu Attorneys Inc.</p>");
  }

  private static Map<String, Object> feeNoteContext(
      Map<String, Object> org, Map<String, Object> customer, Map<String, Object> invoice) {
    var context = new HashMap<String, Object>();
    context.put("org", org);
    context.put("customer", customer);
    context.put("invoice", invoice);
    context.put("lines", List.of());
    return context;
  }

  @Test
  void legalFeeNote_fullContext_rendersLetterheadClientAddressConditionalsAndBanking()
      throws IOException {
    var customer = new HashMap<String, Object>();
    customer.put("name", "Apex Holdings");
    customer.put("addressLine1", "12 Rivonia Road");
    customer.put("addressLine2", null);
    customer.put("city", "Sandton");
    customer.put("postalCode", "2196");
    customer.put("country", "ZA");
    var invoice =
        Map.<String, Object>of(
            "invoiceNumber", "INV-0001",
            "issueDate", "2026-10-27",
            "dueDate", "2026-11-26",
            "currency", "ZAR");
    var context = feeNoteContext(fullLetterheadOrg(), customer, invoice);
    context.put("customerVatNumber", "9012345678");
    context.put("project", Map.of("name", "Shareholders agreement", "referenceNumber", "MAT-0042"));

    String html =
        renderer.render(
            loadPackTemplate("legal-za", "fee-note-za"), context, Map.of(), null, LEGAL_HINTS);

    assertThat(beforeFirst(html, "<h2>FEE NOTE</h2>"))
        .contains("<img class=\"letterhead-logo\" src=\"https://cdn.test/logo.png\"")
        .contains("<h1>Ndlovu Attorneys Inc.</h1>")
        .contains("<p>88 Fredman Drive, Illovo Point, 3rd Floor, Sandown 2196, South Africa</p>")
        .contains("<p><strong>Tel:</strong> +27 11 555 0199</p>")
        .contains("<p><strong>Email:</strong> accounts@ndlovu-attorneys.co.za</p>")
        .contains("<p><strong>Web:</strong> https://www.ndlovu-attorneys.co.za</p>")
        .contains("<p><strong>VAT Registration No:</strong> 4123456789</p>")
        .endsWith("<hr/>");
    assertThat(html)
        .contains(
            "<p><strong>To:</strong> Apex Holdings</p><p>12 Rivonia Road</p><p>Sandton 2196</p>"
                + "<p>ZA</p>")
        .contains("<p><strong>Client VAT Number:</strong> 9012345678</p>")
        .contains("<p><strong>Matter:</strong> Shareholders agreement</p>")
        .contains("<p><strong>Matter Reference:</strong> MAT-0042</p>")
        .contains("<p><strong>Due Date:</strong> 26 November 2026</p>")
        .contains(
            "<h3>Banking details</h3>"
                + "<p><strong>Bank:</strong> Standard Bank</p>"
                + "<p><strong>Branch code:</strong> 051001</p>"
                + "<p><strong>Account number:</strong> 0012345678</p>"
                + "<p><strong>Account type:</strong> Cheque</p>"
                + "<p><strong>Payment reference:</strong> INV-0001</p>"
                + "<p>Please use your fee note number as the payment reference.</p>")
        .doesNotContain("available on request")
        .doesNotContain("bankName=");
  }

  @Test
  void legalFeeNote_emptyContext_hidesBankingVatMatterReferenceDueDateAndAddress()
      throws IOException {
    var invoice =
        Map.<String, Object>of(
            "invoiceNumber", "INV-0002", "issueDate", "2026-10-27", "currency", "ZAR");
    var context = feeNoteContext(emptyLetterheadOrg(), Map.of("name", "Apex Holdings"), invoice);

    String html =
        renderer.render(
            loadPackTemplate("legal-za", "fee-note-za"), context, Map.of(), null, LEGAL_HINTS);

    assertThat(html)
        .contains("<h1>Ndlovu Attorneys Inc.</h1>")
        .contains("<p><strong>To:</strong> Apex Holdings</p><hr/><p><strong>Fee Note Number")
        .contains("<p><strong>Fee Note Number:</strong> INV-0002</p>")
        .doesNotContain("<img")
        .doesNotContain("Web:")
        .doesNotContain("VAT Registration No")
        .doesNotContain("Client VAT Number")
        .doesNotContain("Matter:")
        .doesNotContain("Matter Reference")
        .doesNotContain("Due Date")
        .doesNotContain("Banking details")
        .doesNotContain("Bank:")
        .doesNotContain("Payment reference")
        .doesNotContain("available on request");
  }

  @Test
  void legalFeeNote_partialBanking_showsLabelAndOnlyTheSetLines() throws IOException {
    var org = emptyLetterheadOrg();
    var banking = new HashMap<String, Object>();
    banking.put("bankName", "FNB");
    banking.put("branchCode", null);
    banking.put("accountNumber", null);
    banking.put("accountType", null);
    banking.put("referenceNote", null);
    org.put("bankingDetails", banking);
    var context =
        feeNoteContext(
            org,
            Map.of("name", "Apex Holdings"),
            Map.of("invoiceNumber", "INV-0003", "currency", "ZAR"));

    String html =
        renderer.render(
            loadPackTemplate("legal-za", "fee-note-za"), context, Map.of(), null, LEGAL_HINTS);

    assertThat(html)
        .contains(
            "<h3>Banking details</h3><p><strong>Bank:</strong> FNB</p>"
                + "<p><strong>Payment reference:</strong> INV-0003</p>")
        .doesNotContain("Branch code")
        .doesNotContain("Account number")
        .doesNotContain("Account type");
  }

  @Test
  void legalStatementOfAccount_paymentInstructionsBindTheBankingLeaves() throws IOException {
    var context = new HashMap<String, Object>();
    context.put("org", fullLetterheadOrg());

    String html =
        renderer.render(
            loadPackTemplate("legal-za", "statement-of-account"),
            context,
            Map.of(),
            null,
            LEGAL_HINTS);

    assertThat(html)
        .contains(
            "<h3>Payment Instructions</h3>"
                + "<p><strong>Bank:</strong> Standard Bank</p>"
                + "<p><strong>Branch code:</strong> 051001</p>"
                + "<p><strong>Account number:</strong> 0012345678</p>"
                + "<p><strong>Account type:</strong> Cheque</p>"
                + "<p>Please use your fee note number as the payment reference.</p>")
        .doesNotContain("bankName=");

    context.put("org", emptyLetterheadOrg());
    String hidden =
        renderer.render(
            loadPackTemplate("legal-za", "statement-of-account"),
            context,
            Map.of(),
            null,
            LEGAL_HINTS);
    assertThat(hidden).doesNotContain("Payment Instructions").doesNotContain("Bank:");
  }

  // --- 626D review fixes F-6 (signatory lines gated per leaf) / F-7 (sibling address lines) ---

  private static Map<String, Object> customerAddress(
      String line1, String city, String postalCode, String country) {
    var customer = new HashMap<String, Object>();
    customer.put("name", "Apex Holdings");
    customer.put("addressLine1", line1);
    customer.put("addressLine2", null);
    customer.put("city", city);
    customer.put("postalCode", postalCode);
    customer.put("country", country);
    return customer;
  }

  private String renderFeeNote(Map<String, Object> customer) throws IOException {
    var context =
        feeNoteContext(
            emptyLetterheadOrg(), customer, Map.of("invoiceNumber", "INV-0004", "currency", "ZAR"));
    return renderer.render(
        loadPackTemplate("legal-za", "fee-note-za"), context, Map.of(), null, LEGAL_HINTS);
  }

  /** The client block: from the "To:" line up to the separator below it. */
  private static String clientBlock(String html) {
    int to = html.indexOf("<p><strong>To:</strong>");
    assertThat(to).isGreaterThanOrEqualTo(0);
    return html.substring(to, html.indexOf("<hr/>", to));
  }

  @Test
  void legalFeeNote_blankAddressLine1_stillRendersCityPostalCodeAndCountry() throws IOException {
    String block = clientBlock(renderFeeNote(customerAddress(null, "Sandton", "2196", "ZA")));

    assertThat(block)
        .isEqualTo("<p><strong>To:</strong> Apex Holdings</p><p>Sandton 2196</p><p>ZA</p>");
  }

  @Test
  void legalFeeNote_blankCity_stillRendersThePostalCodeAlone() throws IOException {
    String block = clientBlock(renderFeeNote(customerAddress("12 Rivonia Road", "", "2196", "ZA")));

    assertThat(block)
        .isEqualTo(
            "<p><strong>To:</strong> Apex Holdings</p><p>12 Rivonia Road</p><p>2196</p><p>ZA</p>");
  }

  @Test
  void legalFeeNote_blankAddress_rendersOnlyTheClientName() throws IOException {
    String block = clientBlock(renderFeeNote(customerAddress(null, null, null, null)));

    assertThat(block).isEqualTo("<p><strong>To:</strong> Apex Holdings</p>");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "engagement-letter-general",
        "engagement-letter-litigation",
        "engagement-letter-conveyancing",
        "matter-closure-letter",
        "deed-of-transfer",
        "bond-cancellation-instruction"
      })
  void legalSignatureBlock_gatesTheNameAndTheDesignationSeparately(String key) throws IOException {
    var designationOnly = emptyLetterheadOrg();
    var signatory = new HashMap<String, Object>();
    signatory.put("name", null);
    signatory.put("designation", "Managing Partner");
    designationOnly.put("signatory", signatory);
    String html =
        renderer.render(
            loadPackTemplate("legal-za", key),
            engagementContext(designationOnly),
            Map.of(),
            null,
            LEGAL_HINTS);
    assertThat(html)
        .contains("<p>Managing Partner</p><p>Ndlovu Attorneys Inc.</p>")
        .doesNotContain("<p></p><p>Managing Partner</p>");

    var nameOnly = emptyLetterheadOrg();
    var named = new HashMap<String, Object>();
    named.put("name", "Sipho Ndlovu");
    named.put("designation", null);
    nameOnly.put("signatory", named);
    String signed =
        renderer.render(
            loadPackTemplate("legal-za", key),
            engagementContext(nameOnly),
            Map.of(),
            null,
            LEGAL_HINTS);
    assertThat(signed)
        .contains("<p>Sipho Ndlovu</p><p>Ndlovu Attorneys Inc.</p>")
        .doesNotContain("<p>Sipho Ndlovu</p><p></p>");
  }

  /** Ruling R-2: the three letters that bound the deprecated alias sign with the signatory. */
  @ParameterizedTest
  @ValueSource(
      strings = {"matter-closure-letter", "deed-of-transfer", "bond-cancellation-instruction"})
  void legalSignedLetters_signWithTheSignatory_notTheDeprecatedAlias(String key)
      throws IOException {
    var resource = new ClassPathResource("template-packs/legal-za/" + key + ".json");
    String raw = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(raw).doesNotContain("org.principal_attorney");

    var context = new HashMap<String, Object>();
    context.put("org", fullLetterheadOrg());
    String html =
        renderer.render(loadPackTemplate("legal-za", key), context, Map.of(), null, LEGAL_HINTS);
    assertThat(html)
        .contains("<p>Sipho Ndlovu</p><p>Managing Partner</p><p>Ndlovu Attorneys Inc.</p>");

    context.put("org", emptyLetterheadOrg());
    String unsigned =
        renderer.render(loadPackTemplate("legal-za", key), context, Map.of(), null, LEGAL_HINTS);
    assertThat(unsigned).doesNotContain("Sipho").doesNotContain("Managing Partner");
  }
}
