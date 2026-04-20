package io.b2mash.b2b.b2bstrawman.customerbackend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Portal response shape for a retainer agreement's current-period usage snapshot (Epic 496A,
 * ADR-255). Emitted by {@code PortalRetainerService.listForContact()}. Contains only portal-safe
 * fields — firm-internal columns such as {@code last_synced_at} and {@code customer_id} are not
 * surfaced.
 */
public record PortalRetainerSummaryResponse(
    UUID id,
    String name,
    String periodType,
    BigDecimal hoursAllotted,
    BigDecimal hoursConsumed,
    BigDecimal hoursRemaining,
    LocalDate periodStart,
    LocalDate periodEnd,
    BigDecimal rolloverHours,
    LocalDate nextRenewalDate,
    String status) {}
