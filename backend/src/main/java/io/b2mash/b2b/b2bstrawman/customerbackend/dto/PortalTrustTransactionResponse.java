package io.b2mash.b2b.b2bstrawman.customerbackend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Row shape for {@code GET /portal/trust/matters/{matterId}/transactions}. */
public record PortalTrustTransactionResponse(
    UUID id,
    String transactionType,
    BigDecimal amount,
    BigDecimal runningBalance,
    Instant occurredAt,
    String description,
    String reference) {}
