package io.b2mash.b2b.b2bstrawman.mcp.dto;

import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.TrustTransactionResponse;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One trust ledger transaction for {@code list_trust_transactions} (Epic 564A). Money is carried as
 * minor units (long) + currency — the source {@link TrustTransactionResponse#amount()} is {@link
 * java.math.BigDecimal} major units with no currency of its own, so {@code currency} is supplied
 * from the firm's settings by the tool.
 *
 * @param id the transaction id
 * @param date the transaction date
 * @param type transaction type (e.g. DEPOSIT, PAYMENT)
 * @param amountMinor amount in minor units
 * @param currency 3-letter currency code (firm default)
 * @param reference caller-supplied reference
 * @param status transaction status
 */
public record McpTrustTransactionItem(
    UUID id,
    LocalDate date,
    String type,
    long amountMinor,
    String currency,
    String reference,
    String status) {

  /**
   * Projects a firm-side {@link TrustTransactionResponse} into the MCP item, attaching currency.
   */
  public static McpTrustTransactionItem from(TrustTransactionResponse tx, String currency) {
    return new McpTrustTransactionItem(
        tx.id(),
        tx.transactionDate(),
        tx.transactionType(),
        tx.amount().movePointRight(2).longValueExact(),
        currency,
        tx.reference(),
        tx.status());
  }
}
