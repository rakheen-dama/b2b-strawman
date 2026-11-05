package io.b2mash.b2b.b2bstrawman.customerbackend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-matter balance snapshot surfaced by {@code GET /portal/trust/summary}. {@code matterName} is
 * the client's portal matter name — {@code null} when no {@code portal_projects} row exists for
 * this client (Phase 89 633B, G88-F31).
 */
public record PortalTrustMatterSummary(
    UUID matterId,
    BigDecimal currentBalance,
    Instant lastTransactionAt,
    Instant lastSyncedAt,
    String matterName) {}
