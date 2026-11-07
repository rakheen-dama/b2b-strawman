package io.b2mash.b2b.b2bstrawman.customerbackend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Row shape for {@code GET /portal/trust/matters/{matterId}/transactions}. {@code transactionDate}
 * is the transaction's SA business date ({@code "2026-10-01"}) — the date the client sees
 * (G89-F39); {@code occurredAt} is the approval / recorded instant.
 */
public record PortalTrustTransactionResponse(
    UUID id,
    String transactionType,
    BigDecimal amount,
    BigDecimal runningBalance,
    LocalDate transactionDate,
    Instant occurredAt,
    String description,
    String reference) {}
