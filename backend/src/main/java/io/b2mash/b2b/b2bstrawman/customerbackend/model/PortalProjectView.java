package io.b2mash.b2b.b2bstrawman.customerbackend.model;

import java.time.Instant;
import java.util.UUID;

public record PortalProjectView(
    UUID id,
    String orgId,
    UUID customerId,
    String name,
    String status,
    String description,
    int documentCount,
    int commentCount,
    Instant createdAt,
    Instant updatedAt) {}
