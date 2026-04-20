package io.b2mash.b2b.b2bstrawman.customerbackend.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Portal read-model projection for a retainer agreement's current-period usage snapshot (Epic 496A,
 * ADR-255). Populated by {@code RetainerPortalSyncService} from firm-side retainer events. Per the
 * portal read-model convention (ADR-253), this is a plain record — not a JPA entity — read via
 * {@code JdbcClient} against the {@code portal.portal_retainer_summary} table.
 *
 * <p>The record's {@code id} column mirrors the firm-side {@code retainer_agreements.id} so the
 * portal write path can use {@code ON CONFLICT (id) DO UPDATE} for idempotent upserts.
 */
public record PortalRetainerSummaryView(
    UUID id,
    UUID customerId,
    String name,
    String periodType,
    BigDecimal hoursAllotted,
    BigDecimal hoursConsumed,
    BigDecimal hoursRemaining,
    LocalDate periodStart,
    LocalDate periodEnd,
    BigDecimal rolloverHours,
    LocalDate nextRenewalDate,
    String status,
    Instant lastSyncedAt) {}
