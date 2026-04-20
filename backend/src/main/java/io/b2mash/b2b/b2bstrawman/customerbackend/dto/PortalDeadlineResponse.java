package io.b2mash.b2b.b2bstrawman.customerbackend.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Portal response shape for a unified deadline row (Epic 497A, ADR-256). Emitted by {@code
 * PortalDeadlineService.listForContact(...)} and {@code .getForContact(...)}. Only portal-safe
 * fields are surfaced — firm-internal columns such as {@code customer_id} and {@code
 * last_synced_at} are withheld.
 */
public record PortalDeadlineResponse(
    UUID id,
    String sourceEntity,
    String deadlineType,
    String label,
    LocalDate dueDate,
    String status,
    String descriptionSanitised,
    UUID matterId) {}
