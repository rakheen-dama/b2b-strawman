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
 * Account is still a statement) or on the slug — the base slug or a {@code <slug>-*} clone slug,
 * the rule {@code StatementService} applies to statement templates.
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

  /** The dedicated flow {@code template} belongs to, if any. */
  public static Optional<DedicatedFlowTemplate> of(DocumentTemplate template) {
    return Arrays.stream(values())
        .filter(
            flow ->
                flow.key.equals(template.getPackTemplateKey())
                    || flow.key.equals(template.getSlug())
                    || (template.getSlug() != null
                        && template.getSlug().startsWith(flow.key + "-")))
        .findFirst();
  }
}
