package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One client's trust balance on one matter as of a date — a row of {@link
 * TrustTransactionRepository#calculateClientMatterBalancesAsOfDate}. {@code projectId} is null for
 * the client's unmattered (client-level) rows.
 */
public interface MatterBalanceRow {

  UUID getProjectId();

  BigDecimal getBalance();
}
