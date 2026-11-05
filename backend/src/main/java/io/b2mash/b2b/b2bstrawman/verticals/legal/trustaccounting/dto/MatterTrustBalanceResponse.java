package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code GET /api/matters/{matterId}/trust-balance} (Phase 89 §14.2.6 / §14.3.18, G88-M13): the
 * matter's trust balance — the sum of its RECORDED/APPROVED trust transactions with the
 * closure-gate sign rules ({@code TrustTransactionRepository.calculateBalanceByProjectId}) — in
 * {@code currency}, the org default currency (trust accounts carry no currency of their own; the
 * matter list's trust column uses the same source).
 */
public record MatterTrustBalanceResponse(UUID matterId, BigDecimal balance, String currency) {}
