package io.b2mash.b2b.b2bstrawman.invoice.dto;

import java.math.BigDecimal;

/**
 * {@code GET /api/invoices/outstanding-summary} (Phase 88 U3, §13.4.3 — field spellings are
 * contractual, 620C binds to them). Computed by {@code CollectionsReadService.outstandingSummary()}
 * with the debtor-book definition ({@code CollectionsSql}); amounts are in {@code currency}, the
 * org default currency — fee notes in other currencies are excluded.
 */
public record OutstandingSummaryResponse(
    String currency,
    BigDecimal outstandingTotal,
    int outstandingCount,
    BigDecimal overdueTotal,
    int overdueCount,
    BigDecimal approvedUnsentTotal,
    int approvedUnsentCount) {}
