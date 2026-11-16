package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One client's derived trust balance (ADR-378): the sum of {@code ledger_effect} over its posted
 * {@code CLIENT} rows, and the latest {@code transaction_date} among them. A row of {@link
 * TrustLedgerQueryRepository#clientBalances}, {@link TrustLedgerQueryRepository#clientBalancePage},
 * {@link TrustLedgerQueryRepository#clientSummary} and {@link
 * TrustLedgerQueryRepository#generalBalancesByClient}.
 */
public interface ClientBalanceRow {

  UUID getCustomerId();

  BigDecimal getBalance();

  LocalDate getLastTransactionDate();
}
