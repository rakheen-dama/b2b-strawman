package io.b2mash.b2b.b2bstrawman.customerbackend.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Portal read-model projection for a single trust ledger transaction as surfaced to portal
 * contacts. The {@code description} and {@code reference} are already sanitised by {@code
 * PortalTrustDescriptionSanitiser} before persistence — the portal never stores the firm-side raw
 * description. {@code transactionType} is the firm's type code (for logic, never rendered); {@code
 * ledger} the ledger the row moved — {@code CLIENT} or {@code CLIENT_INVESTMENT} (Phase 92 648D) —
 * and so which balance {@code runningBalance} is.
 */
public record PortalTrustTransactionView(
    UUID id,
    UUID customerId,
    UUID matterId,
    String transactionType,
    String ledger,
    BigDecimal amount,
    BigDecimal runningBalance,
    LocalDate transactionDate,
    Instant recordedAt,
    Instant occurredAt,
    String description,
    String reference,
    Instant lastSyncedAt) {}
