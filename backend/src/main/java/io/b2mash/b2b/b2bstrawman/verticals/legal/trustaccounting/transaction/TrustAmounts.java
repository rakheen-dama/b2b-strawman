package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.math.BigDecimal;

/**
 * The one amount rule for every trust write path — deposit, transfer, payment, fee transfer, refund
 * ({@link TrustTransactionService}) and investment placement ({@code
 * investment.TrustInvestmentService}) — run before any balance logic. Stateless, so a caller in
 * another package needs no dependency on {@link TrustTransactionService} (648A review cycle 4).
 */
public final class TrustAmounts {

  private TrustAmounts() {}

  /**
   * Refuses a null, zero or negative amount, and a sub-cent one. Amounts are stored at cents
   * ({@code numeric(15,2)}), so a sub-cent amount is refused rather than rounded on save or
   * compared unrounded against a balance (PR #1752 review). Trailing zeros ({@code 250.500}) are a
   * whole number of cents and pass.
   */
  public static void requireValid(BigDecimal amount) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new InvalidStateException(
          "Invalid amount", "Amount must be a positive value, got: " + amount);
    }
    if (amount.stripTrailingZeros().scale() > 2) {
      throw new InvalidStateException(
          "Invalid amount", "Amount must not have more than 2 decimal places");
    }
  }
}
