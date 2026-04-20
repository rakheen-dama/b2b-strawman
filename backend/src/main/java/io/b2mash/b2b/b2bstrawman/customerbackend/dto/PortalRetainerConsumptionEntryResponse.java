package io.b2mash.b2b.b2bstrawman.customerbackend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Portal response shape for a single retainer consumption entry (one time-entry projected for the
 * customer portal). Emitted by {@code PortalRetainerService.consumption(...)}. Per ADR-254, the
 * {@code description} has been sanitised at sync time — raw firm-side description never reaches the
 * portal.
 */
public record PortalRetainerConsumptionEntryResponse(
    UUID id,
    LocalDate occurredAt,
    BigDecimal hours,
    String description,
    String memberDisplayName) {}
