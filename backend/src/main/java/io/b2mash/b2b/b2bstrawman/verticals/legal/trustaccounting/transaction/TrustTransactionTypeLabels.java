package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import java.util.Locale;
import java.util.function.Function;

/**
 * Labels for a trust transaction type, read from the registry ({@link TrustTransactionType}, Phase
 * 92 646E) — no map of its own. Two audiences (649B privacy rule): {@link #label} is the client's
 * copy ({@link TrustTransactionType#clientLabel()}) for every client surface — the portal trust
 * ledger, the trust-activity email (633C D6), the Statement of Account (G89 W2-c3) and the Client
 * Trust Statement; {@link #firmLabel} is the firm's ({@link TrustTransactionType#label()}) for the
 * transaction DTOs, MCP and the firm reports. Neither throws: a code the registry does not know (a
 * retired type on a historic row or event) falls back to sentence case, never the raw enum.
 */
public final class TrustTransactionTypeLabels {

  private TrustTransactionTypeLabels() {}

  /**
   * The client-facing label for {@code transactionType}: the registry's {@code clientLabel}; an
   * unknown value falls back to sentence case ({@code "SOME_TYPE"} → {@code "Some type"}).
   * Null/blank stays null so a template can print its own placeholder.
   */
  public static String label(String transactionType) {
    return labelOf(transactionType, TrustTransactionType::clientLabel);
  }

  /** The firm label for {@code transactionType} (the registry's {@code label}); same fallbacks. */
  public static String firmLabel(String transactionType) {
    return labelOf(transactionType, TrustTransactionType::label);
  }

  private static String labelOf(
      String transactionType, Function<TrustTransactionType, String> registryLabel) {
    if (transactionType == null || transactionType.isBlank()) {
      return null;
    }
    return TrustTransactionType.find(transactionType)
        .map(registryLabel)
        .orElseGet(() -> sentenceCase(transactionType));
  }

  private static String sentenceCase(String code) {
    String text = code.trim().replace('_', ' ').toLowerCase(Locale.ROOT);
    return Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }

  /**
   * The description a client reads for a trust ledger row — the one rule for every client surface
   * (the portal trust ledger, the Statement of Account, the Client Trust Statement). A {@code
   * REVERSAL} row's own description is the firm user's free-text reversal reason, which never
   * reaches the client (Phase 92 649B, PR #1789 review): it reads "{@code Reversal — <reference>}"
   * ({@code Reversal} alone without a reference). Every other type keeps its own description.
   * Firm-side readers (the client-ledger API, firm reports) print the row's own description.
   */
  public static String clientDescription(
      String transactionType, String description, String reference) {
    if (!"REVERSAL".equals(transactionType)) {
      return description;
    }
    String label = label(transactionType);
    String ref = reference == null ? "" : reference.strip();
    return ref.isEmpty() ? label : label + " — " + ref;
  }
}
