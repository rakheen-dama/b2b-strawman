package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import java.util.Locale;
import java.util.Map;

/**
 * Client-facing labels for a trust transaction type (tenant {@code chk_trust_txn_type}). Same copy
 * as the portal trust page ({@code portal/lib/labels.ts} TRUST_TRANSACTION_TYPE_LABELS), so the
 * client reads one vocabulary in the portal, the trust-activity email (633C D6) and the Statement
 * of Account's trust Payments table (G89 W2-c3).
 */
public final class TrustTransactionTypeLabels {

  private static final Map<String, String> LABELS =
      Map.of(
          "DEPOSIT", "Deposit",
          "PAYMENT", "Payment",
          "REFUND", "Refund",
          "FEE_TRANSFER", "Fee transfer",
          "INTEREST_CREDIT", "Interest credited",
          "INTEREST_LPFF", "Interest to LPFF",
          "TRANSFER_IN", "Transfer in",
          "TRANSFER_OUT", "Transfer out",
          "REVERSAL", "Reversal");

  private TrustTransactionTypeLabels() {}

  /**
   * The label for {@code transactionType}; an unmapped value falls back to sentence case ({@code
   * "SOME_TYPE"} → {@code "Some type"}), never the raw enum. Null/blank stays null so a template
   * can print its own placeholder.
   */
  public static String label(String transactionType) {
    if (transactionType == null || transactionType.isBlank()) {
      return null;
    }
    String label = LABELS.get(transactionType);
    if (label != null) {
      return label;
    }
    String text = transactionType.trim().replace('_', ' ').toLowerCase(Locale.ROOT);
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
