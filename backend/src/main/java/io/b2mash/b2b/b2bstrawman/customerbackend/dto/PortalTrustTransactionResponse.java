package io.b2mash.b2b.b2bstrawman.customerbackend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Row shape for {@code GET /portal/trust/matters/{matterId}/transactions}. {@code transactionDate}
 * is the transaction's SA business date ({@code "2026-10-01"}) — the date the client sees
 * (G89-F39); {@code occurredAt} is the approval / recorded instant.
 *
 * <p>Phase 92 648D (§16.4.7): {@code typeLabel} is the client label from the type registry ({@code
 * TrustTransactionTypeLabels.label}) — what the portal renders; {@code transactionType} stays as
 * the code, for logic only. {@code ledger} is the ledger the row moved ({@code CLIENT} or {@code
 * CLIENT_INVESTMENT}), so {@code runningBalance} is that ledger's balance — a section 86(4)
 * interest row carries the invested balance.
 */
public record PortalTrustTransactionResponse(
    UUID id,
    String transactionType,
    String typeLabel,
    String ledger,
    BigDecimal amount,
    BigDecimal runningBalance,
    LocalDate transactionDate,
    Instant occurredAt,
    String description,
    String reference) {}
