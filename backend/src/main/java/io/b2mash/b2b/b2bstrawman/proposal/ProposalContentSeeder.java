package io.b2mash.b2b.b2bstrawman.proposal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Builds a minimal Tiptap document tree to seed {@code proposal.content_json} when the create-time
 * caller did not supply one. Keeps {@code ProposalService.sendProposal()} gate-2 (empty-content
 * guard) honest while ensuring every proposal scaffolded via the matter-level "+ New Engagement
 * Letter" dialog is sendable from creation.
 *
 * <p>The seeded doc renders cleanly through {@code TiptapRenderer} (it only uses {@code doc},
 * {@code heading}, {@code paragraph}, {@code text}, and {@code variable} node types). The {@code
 * variable} node references {@code client_name}, which is populated by {@link
 * ProposalVariableResolver#buildContext}, so the rendered HTML interpolates the customer's name at
 * portal-sync time without the seeder needing a {@code Customer} lookup.
 *
 * <p>This seed is a convenience artefact only — it is intended to be replaced by clause-driven
 * authoring once GAP-L-49 (template/clause picker + Tiptap editor wiring) lands.
 */
@Service
public class ProposalContentSeeder {

  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

  /**
   * Builds a minimal Tiptap doc tree from the form fields collected by the matter-level "+ New
   * Engagement Letter" dialog. Always emits a heading, greeting, fee section, and standard terms
   * paragraph; the optional rate-note, hours-included, contingency-description, and expiry
   * paragraphs are appended only when their input is non-null/non-blank.
   *
   * <p>Returned maps and lists are mutable ({@link LinkedHashMap} / {@link ArrayList}) so any
   * downstream caller can append additional nodes without re-shaping the tree.
   *
   * @param title proposal title (rendered as the document heading)
   * @param feeModel fee model — drives the fee summary line shape
   * @param hourlyRateNote optional rate note (HOURLY only)
   * @param fixedFeeAmount fixed-fee amount (FIXED only)
   * @param fixedFeeCurrency fixed-fee currency code (FIXED only); defaults to "ZAR"
   * @param retainerAmount monthly retainer amount (RETAINER only)
   * @param retainerCurrency retainer currency code (RETAINER only); defaults to "ZAR"
   * @param retainerHoursIncluded optional hours included in the retainer
   * @param contingencyPercent contingency percentage (CONTINGENCY only)
   * @param contingencyDescription optional contingency description
   * @param expiresAt optional expiry timestamp
   * @return a Tiptap doc tree as a {@code Map<String, Object>}; never null, never empty
   */
  public Map<String, Object> buildDefaultContent(
      String title,
      FeeModel feeModel,
      String hourlyRateNote,
      BigDecimal fixedFeeAmount,
      String fixedFeeCurrency,
      BigDecimal retainerAmount,
      String retainerCurrency,
      BigDecimal retainerHoursIncluded,
      BigDecimal contingencyPercent,
      String contingencyDescription,
      Instant expiresAt) {

    var content = new ArrayList<Map<String, Object>>();

    // Heading (h2): proposal title
    String safeTitle = title != null && !title.isBlank() ? title : "Engagement Letter";
    content.add(heading(2, List.of(text(safeTitle))));

    // Greeting paragraph: "Dear {client_name},"
    content.add(paragraph(List.of(text("Dear "), variable("client_name"), text(","))));

    // Fee Arrangement heading + summary paragraph
    content.add(heading(3, List.of(text("Fee Arrangement"))));
    content.add(
        paragraph(
            List.of(
                text(
                    buildFeeSummary(
                        feeModel,
                        fixedFeeAmount,
                        fixedFeeCurrency,
                        retainerAmount,
                        retainerCurrency,
                        contingencyPercent)))));

    // Optional fee-detail paragraphs
    if (feeModel == FeeModel.HOURLY && hourlyRateNote != null && !hourlyRateNote.isBlank()) {
      content.add(paragraph(List.of(text("Rate: " + hourlyRateNote))));
    }
    if (feeModel == FeeModel.RETAINER && retainerHoursIncluded != null) {
      content.add(
          paragraph(List.of(text("Hours included: " + retainerHoursIncluded.toPlainString()))));
    }
    if (feeModel == FeeModel.CONTINGENCY
        && contingencyDescription != null
        && !contingencyDescription.isBlank()) {
      content.add(paragraph(List.of(text(contingencyDescription))));
    }

    // Optional expiry paragraph
    if (expiresAt != null) {
      content.add(
          paragraph(
              List.of(text("This proposal expires on " + DATE_FORMAT.format(expiresAt) + "."))));
    }

    // Standard terms paragraph (always)
    content.add(
        paragraph(
            List.of(
                text(
                    "This proposal is subject to our standard terms and conditions."
                        + " Please contact us if you have any questions."))));

    var doc = new LinkedHashMap<String, Object>();
    doc.put("type", "doc");
    doc.put("content", content);
    return doc;
  }

  // --- Tiptap node builders (LinkedHashMap so JSON order is preserved) ---

  private static Map<String, Object> heading(int level, List<Map<String, Object>> children) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "heading");
    var attrs = new LinkedHashMap<String, Object>();
    attrs.put("level", level);
    node.put("attrs", attrs);
    node.put("content", new ArrayList<>(children));
    return node;
  }

  private static Map<String, Object> paragraph(List<Map<String, Object>> children) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "paragraph");
    node.put("content", new ArrayList<>(children));
    return node;
  }

  private static Map<String, Object> text(String value) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "text");
    node.put("text", value);
    return node;
  }

  private static Map<String, Object> variable(String key) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "variable");
    var attrs = new LinkedHashMap<String, Object>();
    attrs.put("key", key);
    node.put("attrs", attrs);
    return node;
  }

  // --- Fee summary line ---

  private static String buildFeeSummary(
      FeeModel feeModel,
      BigDecimal fixedFeeAmount,
      String fixedFeeCurrency,
      BigDecimal retainerAmount,
      String retainerCurrency,
      BigDecimal contingencyPercent) {
    return switch (feeModel) {
      case HOURLY -> "Fees will be charged on an hourly basis.";
      case FIXED -> {
        String currency =
            fixedFeeCurrency != null && !fixedFeeCurrency.isBlank() ? fixedFeeCurrency : "ZAR";
        String amount = fixedFeeAmount != null ? fixedFeeAmount.toPlainString() : "TBD";
        yield "Fixed fee: " + currency + " " + amount;
      }
      case RETAINER -> {
        String currency =
            retainerCurrency != null && !retainerCurrency.isBlank() ? retainerCurrency : "ZAR";
        String amount = retainerAmount != null ? retainerAmount.toPlainString() : "TBD";
        yield "Monthly retainer: " + currency + " " + amount;
      }
      case CONTINGENCY -> {
        String pct = contingencyPercent != null ? contingencyPercent.toPlainString() : "TBD";
        yield "Contingency: " + pct + "%";
      }
    };
  }
}
