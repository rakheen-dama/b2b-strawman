package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

/**
 * The creditor ledger a posted trust transaction moves (Phase 92, ADR-378, architecture §16.2.1).
 * Stored in {@code trust_transactions.ledger}. 646A posts only {@link #CLIENT}; the other three are
 * declared so the column's vocabulary is fixed from the first migration (investment ledgers 648A,
 * non-client creditor accounts 646B, inter-account transfers 647).
 */
public enum TrustLedger {
  CLIENT,
  CLIENT_INVESTMENT,
  NON_CLIENT,
  INTER_ACCOUNT
}
