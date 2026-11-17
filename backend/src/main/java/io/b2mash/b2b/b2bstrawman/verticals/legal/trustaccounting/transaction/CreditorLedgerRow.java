package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import java.math.BigDecimal;

/**
 * Native projection for {@link TrustLedgerQueryRepository#creditorLedgerRows} (Phase 92 / 647C,
 * architecture §16.4.2): one row of a non-client creditor ledger with its server-computed running
 * balance (ADR-361 window over {@code ledger_effect}) and a bank charge's detail (ADR-381).
 *
 * <p>Unlike {@link ClientHistoryRow}, {@link #getRunningBalance()} is set on every POSTED row
 * ({@code posted_at IS NOT NULL}) — a REVERSED original and its REVERSAL contra included, each on
 * its own date — so the newest row's balance equals {@link
 * TrustLedgerQueryRepository#creditorAccountBalance}; it is null only on unposted rows (awaiting
 * approval, rejected).
 *
 * <p>An interface, not a record — {@code WireVocabularyContractTest} scans records whose name ends
 * in {@code Row}.
 */
public interface CreditorLedgerRow extends ClientHistoryRow {

  /** {@code BANK_CHARGE} only ({@code chk_trust_txn_charge}); null on every other row. */
  Boolean getChargeRecoverable();

  /** {@code BANK_CHARGE} only; null on every other row. */
  BigDecimal getChargeVatAmount();
}
