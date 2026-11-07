package io.b2mash.b2b.b2bstrawman.template;

import java.util.Arrays;
import java.util.Optional;

/**
 * Templates that render only inside their own flow (G89 632A(a), D11): the generic generate path
 * ({@code POST /api/templates/{id}/generate}) supplies only the matter / client / fee-note context,
 * so the flow-specific groups these bodies print ({@code statement.*} / {@code fees.*} / {@code
 * summary.*}, {@code closure.*}, {@code payment.*}, the deposit) would render hollow there. The
 * generic Generate menus hide them and the generic endpoint refuses them; their own flows use other
 * {@code GeneratedDocumentService} entry points and are unaffected.
 *
 * <p>Matched on the pack template key (clones keep it, so a firm's clone of the Statement of
 * Account is still a statement) or on the slug — the base slug, or a {@code <slug>-*} slug on a
 * clone or PLATFORM row only (a firm-authored template is never caught by its name; see {@link
 * #of}). {@code StatementService} keeps its own, separate statement-template slug rule.
 */
public enum DedicatedFlowTemplate {
  STATEMENT_OF_ACCOUNT(
      "statement-of-account",
      "The Statement of Account is generated from the matter's Statements tab."),
  MATTER_CLOSURE_LETTER(
      "matter-closure-letter", "The closure letter is generated when the matter is closed."),
  PAYMENT_RECEIPT(
      "payment-receipt-za",
      "A payment receipt is generated when a payment is recorded on the fee note."),
  TRUST_RECEIPT(
      "trust-receipt", "A trust receipt can only be issued from a recorded trust deposit.");

  /** ProblemDetail title of the generic endpoint's refusal. */
  public static final String REFUSAL_TITLE = "Generated from its own flow";

  private final String key;
  private final String flowHint;

  DedicatedFlowTemplate(String key, String flowHint) {
    this.key = key;
    this.flowHint = flowHint;
  }

  /** The pack template key (= base slug) of the template. */
  public String key() {
    return key;
  }

  /** Where the template is generated instead — the refusal's detail. */
  public String flowHint() {
    return flowHint;
  }

  /**
   * The dedicated flow {@code template} belongs to, if any: its pack template key or its exact slug
   * is the flow key, or — for a clone ({@code sourceTemplateId} set) or a PLATFORM row only — its
   * slug is a {@code <key>-*} variant. A firm-authored template whose slug merely starts with a key
   * ("Statement of Account Cover Letter") belongs to no flow and stays generatable (review, PR
   * #1749).
   */
  public static Optional<DedicatedFlowTemplate> of(DocumentTemplate template) {
    String slug = template.getSlug();
    boolean prefixApplies =
        slug != null
            && (template.getSourceTemplateId() != null
                || template.getSource() == TemplateSource.PLATFORM);
    return Arrays.stream(values())
        .filter(
            flow ->
                flow.key.equals(template.getPackTemplateKey())
                    || flow.key.equals(slug)
                    || (prefixApplies && slug.startsWith(flow.key + "-")))
        .findFirst();
  }
}
