package io.b2mash.b2b.b2bstrawman.customerbackend.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Portal read-model projection for a single retainer consumption entry (one time-entry logged
 * against a retainer-backed project). Populated by {@code RetainerPortalSyncService} from firm-side
 * {@code TimeEntryChangedEvent}. Per ADR-253, this is a plain record — not a JPA entity — read via
 * {@code JdbcClient} against the {@code portal.portal_retainer_consumption_entry} table.
 *
 * <p>The {@code id} column mirrors the firm-side {@code time_entries.id} so the portal write path
 * can use {@code ON CONFLICT (id) DO UPDATE} for idempotent upserts across time-entry edits.
 */
public record PortalRetainerConsumptionEntryView(
    UUID id,
    UUID retainerId,
    UUID customerId,
    LocalDate occurredAt,
    BigDecimal hours,
    String description,
    String memberDisplayName,
    Instant lastSyncedAt) {}
