package io.b2mash.b2b.b2bstrawman.customerbackend.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Portal read-model projection for a unified deadline row (Epic 497A, ADR-256). Populated by {@code
 * DeadlinePortalSyncService} from up to four firm-side event sources. Per ADR-253 this is a plain
 * record — not a JPA entity — read via {@code JdbcClient} against the {@code
 * portal.portal_deadline_view} table.
 *
 * <p>The composite key {@code (sourceEntity, id)} mirrors the firm-side source row so the portal
 * write path can {@code ON CONFLICT (source_entity, id) DO UPDATE} idempotently. The firm-side
 * {@code id}s are unique per source but not across sources.
 */
public record PortalDeadlineView(
    UUID id,
    String sourceEntity,
    UUID customerId,
    UUID matterId,
    String deadlineType,
    String label,
    LocalDate dueDate,
    String status,
    String descriptionSanitised,
    Instant lastSyncedAt) {}
