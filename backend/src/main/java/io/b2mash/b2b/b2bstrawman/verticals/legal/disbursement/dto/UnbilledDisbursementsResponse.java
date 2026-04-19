package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Response body for {@code GET /api/legal/disbursements/unbilled?projectId=}. Shape matches
 * architecture §67.4.1: the list of approved-unbilled disbursement items for the project plus
 * pre-computed totals. Currency is hardcoded {@code "ZAR"} for the legal-ZA profile (the only legal
 * profile today).
 */
public record UnbilledDisbursementsResponse(
    UUID projectId,
    String currency,
    List<UnbilledDisbursementDto> items,
    BigDecimal totalAmount,
    BigDecimal totalVat) {}
