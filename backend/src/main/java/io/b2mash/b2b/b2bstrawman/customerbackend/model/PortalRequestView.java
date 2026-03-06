package io.b2mash.b2b.b2bstrawman.customerbackend.model;

import java.time.Instant;
import java.util.UUID;

public record PortalRequestView(
    UUID id,
    String requestNumber,
    UUID customerId,
    UUID portalContactId,
    UUID projectId,
    String projectName,
    String orgId,
    String status,
    int totalItems,
    int submittedItems,
    int acceptedItems,
    int rejectedItems,
    Instant sentAt,
    Instant completedAt,
    Instant syncedAt) {}
