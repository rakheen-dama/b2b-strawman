package io.b2mash.b2b.b2bstrawman.customerbackend.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Portal read-model projection for a per-matter trust balance snapshot. Populated by {@code
 * TrustLedgerPortalSyncService} from firm-side trust events. Per ADR-253, this is a plain record —
 * not a JPA entity — read via {@code JdbcClient} against the {@code portal.portal_trust_balance}
 * table.
 */
public record PortalTrustBalanceView(
    UUID customerId,
    UUID matterId,
    BigDecimal currentBalance,
    Instant lastTransactionAt,
    Instant lastSyncedAt) {}
