package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import java.math.BigDecimal;

/**
 * Non-recoverable bank charges debited to a trust account and the refunds from the business
 * account, as at a date (Phase 92 649C, {@code CHARGES_TO_REFUND}, architecture §16.3.9, E3): a row
 * whose reversal is posted on or before the date is excluded from both. A row of {@link
 * TrustLedgerQueryRepository#chargesToRefund}.
 */
public interface ChargesToRefundRow {

  BigDecimal getNonRecoverableCharges();

  BigDecimal getRefunds();
}
