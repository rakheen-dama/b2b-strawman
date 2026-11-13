package io.b2mash.b2b.b2bstrawman.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One month of {@code GET /api/dashboard/trust-balance-trend} (Phase 90 643A, arch §15.2.7): the
 * running cashbook and client-ledger balances at the end of {@code month} ({@code "2026-06"}).
 * {@code periodEnd} is the last day of the month, or the as-of date for the current month, which is
 * the only {@code partial} point.
 */
public record TrustBalanceTrendPoint(
    String month,
    LocalDate periodEnd,
    BigDecimal cashbookBalance,
    BigDecimal clientLedgerTotal,
    boolean partial) {}
