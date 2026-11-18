package io.b2mash.b2b.b2bstrawman.customerbackend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-matter balance snapshot surfaced by {@code GET /portal/trust/summary}. {@code matterName} is
 * the client's portal matter name — {@code null} when no {@code portal_projects} row exists for
 * this client (Phase 89 633B, G88-F31). {@code currentBalance} is the general ({@code CLIENT})
 * balance; {@code investedBalance} is held for the client in section 86(4) investments on the
 * matter (Phase 92 648D, §16.4.7) — zero when none. The client's total held in trust is their sum
 * (ADR-379: a placement does not move it).
 */
public record PortalTrustMatterSummary(
    UUID matterId,
    BigDecimal currentBalance,
    BigDecimal investedBalance,
    Instant lastTransactionAt,
    Instant lastSyncedAt,
    String matterName) {}
