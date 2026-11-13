package io.b2mash.b2b.b2bstrawman.dashboard.dto;

import io.b2mash.b2b.b2bstrawman.dashboard.dto.TrustPositionResponse.TrustAccountRef;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Month-end balances for one trust account — {@code GET /api/dashboard/trust-balance-trend} (Phase
 * 90 643A, ADR-372, arch §15.2.7). {@code months} runs oldest to newest, exactly the requested
 * number of SA calendar months, zero-filled; the opening balances are everything dated before the
 * first month. {@code currency} is the org default (trust accounts carry none); {@code asOf} is the
 * SA business date. {@code accounts} lists the active trust accounts, like {@link
 * TrustPositionResponse#accounts()}.
 */
public record TrustBalanceTrendResponse(
    UUID trustAccountId,
    String accountName,
    String currency,
    LocalDate asOf,
    BigDecimal openingCashbookBalance,
    BigDecimal openingClientLedgerTotal,
    List<TrustBalanceTrendPoint> months,
    List<TrustAccountRef> accounts) {}
