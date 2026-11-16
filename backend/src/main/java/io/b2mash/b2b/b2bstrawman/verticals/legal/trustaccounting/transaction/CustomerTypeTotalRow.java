package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The posted {@code CLIENT}-ledger effect of one transaction type for one client on one trust
 * account — a row of {@link TrustLedgerQueryRepository#clientTotalsByType}. {@code total} is signed
 * (the {@code ledger_effect} sum), not the type's amount.
 */
public interface CustomerTypeTotalRow {

  UUID getCustomerId();

  String getTransactionType();

  BigDecimal getTotal();
}
