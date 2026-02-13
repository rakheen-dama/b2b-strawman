package io.b2mash.b2b.b2bstrawman.customerbackend.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PortalProjectSummaryView(
    UUID id,
    String orgId,
    UUID customerId,
    BigDecimal totalHours,
    BigDecimal billableHours,
    Instant lastActivityAt,
    Instant syncedAt) {}
