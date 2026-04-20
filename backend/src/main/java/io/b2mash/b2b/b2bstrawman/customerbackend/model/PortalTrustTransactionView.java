package io.b2mash.b2b.b2bstrawman.customerbackend.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Portal read-model projection for a single trust ledger transaction as surfaced to portal
 * contacts. The {@code description} and {@code reference} are already sanitised by {@code
 * PortalTrustDescriptionSanitiser} before persistence — the portal never stores the firm-side raw
 * description.
 */
public record PortalTrustTransactionView(
    UUID id,
    UUID customerId,
    UUID matterId,
    String transactionType,
    BigDecimal amount,
    BigDecimal runningBalance,
    Instant occurredAt,
    String description,
    String reference,
    Instant lastSyncedAt) {}
