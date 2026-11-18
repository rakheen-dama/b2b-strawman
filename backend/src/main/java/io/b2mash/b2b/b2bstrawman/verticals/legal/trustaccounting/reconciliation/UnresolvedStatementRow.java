package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import java.util.UUID;

/**
 * An imported statement of a trust account, not yet reconciled, with lines that are neither
 * matched, recorded nor excluded (Phase 92 649C, {@code UNRESOLVED_STATEMENT_LINES} on the
 * dashboard, architecture §16.3.9). A row of {@link
 * BankStatementLineRepository#findUnresolvedInUnreconciledStatements}.
 */
public interface UnresolvedStatementRow {

  UUID getStatementId();

  String getFileName();

  long getUnresolvedCount();
}
