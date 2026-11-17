package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor;

/**
 * Where an unidentified deposit stands now (Phase 92, ADR-376, §16.2.5; CHECK {@code
 * chk_unidentified_deposit_status}). Describes the present only — whether an item was open as at a
 * past date is derived from its own ledger rows (EQ-4), never from this. {@code REVERSED} is a
 * fourth status beyond the requirements' three: the statement line that opened the item was
 * unmatched.
 */
public enum UnidentifiedDepositStatus {
  HELD,
  ALLOCATED,
  PAID_TO_FUND,
  REVERSED
}
