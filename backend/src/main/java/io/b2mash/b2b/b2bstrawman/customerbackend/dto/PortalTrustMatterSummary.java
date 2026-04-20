package io.b2mash.b2b.b2bstrawman.customerbackend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Per-matter balance snapshot surfaced by {@code GET /portal/trust/summary}. */
public record PortalTrustMatterSummary(
    UUID matterId, BigDecimal currentBalance, Instant lastTransactionAt, Instant lastSyncedAt) {}
