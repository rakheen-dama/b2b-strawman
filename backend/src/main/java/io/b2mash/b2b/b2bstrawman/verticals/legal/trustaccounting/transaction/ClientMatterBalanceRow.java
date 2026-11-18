package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One client's general ({@code CLIENT}) balance on one matter of a trust account as at a date — a
 * row of {@link TrustLedgerQueryRepository#clientMatterBalancesOnAccount} (Phase 92 650A, the
 * per-matter breakdown of the Rule 54.15 list). {@code projectId} is null for the client's
 * unmattered rows.
 */
public interface ClientMatterBalanceRow {

  UUID getCustomerId();

  UUID getProjectId();

  BigDecimal getBalance();
}
