package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One unidentified deposit of a trust account with its balance as at a date (Phase 92 649C, EQ-4,
 * architecture §16.1.2, §16.3.9): the sum of {@code ledger_effect} over the item's own posted
 * {@code NON_CLIENT} rows dated on or before the date. The item is open as at the date exactly when
 * that balance equals its amount. A row of {@link
 * TrustLedgerQueryRepository#unidentifiedItemBalances}.
 */
public interface UnidentifiedItemBalanceRow {

  UUID getId();

  BigDecimal getAmount();

  String getStatus();

  LocalDate getReceivedDate();

  String getBankDescription();

  BigDecimal getBalance();
}
