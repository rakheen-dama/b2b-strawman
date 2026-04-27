package io.b2mash.b2b.b2bstrawman.proposal;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.template.TiptapRenderer;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

/**
 * Unit tests for {@link ProposalContentSeeder}. Pure pojo logic — no Spring context, no DB. The
 * renderer round-trip uses {@link TiptapRenderer}'s package-private constructor.
 */
class ProposalContentSeederTest {

  private ProposalContentSeeder seeder;
  private TiptapRenderer renderer;

  @BeforeEach
  void setUp() throws Exception {
    seeder = new ProposalContentSeeder();
    renderer = new TiptapRenderer(new ByteArrayResource("body { font-size: 11pt; }".getBytes()));
  }

  @Test
  void hourly_withRateNote_emitsRateParagraph() {
    var doc =
        seeder.buildDefaultContent(
            "Engagement Letter for Sipho",
            FeeModel.HOURLY,
            "R850/hr per LSSA 2024/2025",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    assertContentRoot(doc);
    String html = render(doc, "Sipho Dlamini");
    assertThat(html).contains("<h2>Engagement Letter for Sipho</h2>");
    assertThat(html).contains("Dear Sipho Dlamini,");
    assertThat(html).contains("<h3>Fee Arrangement</h3>");
    assertThat(html).contains("Fees will be charged on an hourly basis.");
    assertThat(html).contains("Rate: R850/hr per LSSA 2024/2025");
    assertThat(html).contains("standard terms and conditions");
  }

  @Test
  void fixed_withAmountAndCurrency_includesFeeLine() {
    var doc =
        seeder.buildDefaultContent(
            "Fixed Fee Engagement",
            FeeModel.FIXED,
            null,
            new BigDecimal("5000.00"),
            "ZAR",
            null,
            null,
            null,
            null,
            null,
            null);

    assertContentRoot(doc);
    String html = render(doc, "Acme Corp");
    assertThat(html).contains("Fixed fee: ZAR 5000.00");
    assertThat(html).doesNotContain("Rate:");
    assertThat(html).doesNotContain("Hours included:");
  }

  @Test
  void fixed_withNoCurrency_defaultsToZAR() {
    var doc =
        seeder.buildDefaultContent(
            "Fixed Fee No Currency",
            FeeModel.FIXED,
            null,
            new BigDecimal("1234.56"),
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    String html = render(doc, "Test Customer");
    assertThat(html).contains("Fixed fee: ZAR 1234.56");
  }

  @Test
  void retainer_withoutHoursIncluded_omitsHoursParagraph() {
    var doc =
        seeder.buildDefaultContent(
            "Monthly Retainer",
            FeeModel.RETAINER,
            null,
            null,
            null,
            new BigDecimal("8000.00"),
            "ZAR",
            null,
            null,
            null,
            null);

    String html = render(doc, "Test Customer");
    assertThat(html).contains("Monthly retainer: ZAR 8000.00");
    assertThat(html).doesNotContain("Hours included:");
  }

  @Test
  void retainer_withHoursIncluded_includesHoursParagraph() {
    var doc =
        seeder.buildDefaultContent(
            "Monthly Retainer",
            FeeModel.RETAINER,
            null,
            null,
            null,
            new BigDecimal("8000.00"),
            "ZAR",
            new BigDecimal("40.0"),
            null,
            null,
            null);

    String html = render(doc, "Test Customer");
    assertThat(html).contains("Monthly retainer: ZAR 8000.00");
    assertThat(html).contains("Hours included: 40.0");
  }

  @Test
  void contingency_withDescription_includesDescriptionParagraph() {
    var doc =
        seeder.buildDefaultContent(
            "Contingency Engagement",
            FeeModel.CONTINGENCY,
            null,
            null,
            null,
            null,
            null,
            null,
            new BigDecimal("20.00"),
            "Subject to LPC Rule 59 cap.",
            null);

    String html = render(doc, "Plaintiff Inc.");
    assertThat(html).contains("Contingency: 20.00%");
    assertThat(html).contains("Subject to LPC Rule 59 cap.");
  }

  @Test
  void noExpiresAt_omitsExpiryParagraph() {
    var doc =
        seeder.buildDefaultContent(
            "No Expiry", FeeModel.HOURLY, null, null, null, null, null, null, null, null, null);

    String html = render(doc, "Test Customer");
    assertThat(html).doesNotContain("This proposal expires on");
  }

  @Test
  void withExpiresAt_emitsExpiryParagraph() {
    Instant expiresAt = Instant.parse("2026-12-31T23:59:59Z").truncatedTo(ChronoUnit.SECONDS);
    var doc =
        seeder.buildDefaultContent(
            "With Expiry",
            FeeModel.HOURLY,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            expiresAt);

    String html = render(doc, "Test Customer");
    assertThat(html).contains("This proposal expires on 2026-12-31.");
  }

  @Test
  void hourly_allFeeFieldsNull_stillProducesSendableDoc() {
    var doc =
        seeder.buildDefaultContent(
            "Bare Hourly", FeeModel.HOURLY, null, null, null, null, null, null, null, null, null);

    assertContentRoot(doc);
    @SuppressWarnings("unchecked")
    var content = (List<Map<String, Object>>) doc.get("content");
    // heading + greeting + fee heading + fee paragraph + terms paragraph = 5
    assertThat(content).hasSizeGreaterThanOrEqualTo(4);
    String html = render(doc, "Test Customer");
    assertThat(html).contains("<h2>Bare Hourly</h2>");
    assertThat(html).contains("Fees will be charged on an hourly basis.");
    assertThat(html).contains("standard terms and conditions");
  }

  @Test
  void blankTitle_fallsBackToDefaultHeading() {
    var doc =
        seeder.buildDefaultContent(
            "  ", FeeModel.HOURLY, null, null, null, null, null, null, null, null, null);

    String html = render(doc, "Test Customer");
    assertThat(html).contains("<h2>Engagement Letter</h2>");
  }

  @Test
  void rootMapAndChildList_areMutable() {
    var doc =
        seeder.buildDefaultContent(
            "Mutable", FeeModel.HOURLY, null, null, null, null, null, null, null, null, null);

    assertThat(doc.get("type")).isEqualTo("doc");
    @SuppressWarnings("unchecked")
    var content = (List<Map<String, Object>>) doc.get("content");
    int sizeBefore = content.size();
    // Should not throw — the seeder must return mutable collections so downstream callers can
    // append nodes (e.g., a future template-merge step) without re-shaping the tree.
    content.add(Map.of("type", "horizontalRule"));
    assertThat(content).hasSize(sizeBefore + 1);
  }

  // --- Helpers ---

  private void assertContentRoot(Map<String, Object> doc) {
    assertThat(doc.get("type")).isEqualTo("doc");
    assertThat(doc).containsKey("content");
    @SuppressWarnings("unchecked")
    var content = (List<Map<String, Object>>) doc.get("content");
    assertThat(content).isNotEmpty();
  }

  private String render(Map<String, Object> doc, String clientName) {
    var ctx = new HashMap<String, Object>();
    ctx.put("client_name", clientName);
    return renderer.render(doc, ctx, Map.<UUID, io.b2mash.b2b.b2bstrawman.clause.Clause>of(), null);
  }
}
