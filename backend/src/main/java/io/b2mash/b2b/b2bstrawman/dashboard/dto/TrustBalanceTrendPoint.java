package io.b2mash.b2b.b2bstrawman.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One month of {@code GET /api/dashboard/trust-balance-trend} (Phase 90 643A, arch §15.2.7): the
 * running cashbook and trust-creditor balances at the end of {@code month} ({@code "2026-06"}).
 * {@code periodEnd} is the last day of the month, or the as-of date for the current month, which is
 * the only {@code partial} point.
 *
 * <p>{@code clientLedgerTotal} carries the sum of every trust creditor (client ledgers plus the
 * non-client creditors — the trust position's {@code creditorTotal}) since 646D, not the client
 * ledgers alone; the name is kept for wire compatibility (ruling D9).
 */
public record TrustBalanceTrendPoint(
    String month,
    LocalDate periodEnd,
    BigDecimal cashbookBalance,
    BigDecimal clientLedgerTotal,
    boolean partial) {}
