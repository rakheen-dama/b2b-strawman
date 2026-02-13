package io.b2mash.b2b.b2bstrawman.customerbackend.model;

import java.time.Instant;
import java.util.UUID;

public record PortalDocumentView(
    UUID id,
    String orgId,
    UUID customerId,
    UUID portalProjectId,
    String title,
    String contentType,
    Long size,
    String scope,
    String s3Key,
    Instant uploadedAt,
    Instant syncedAt) {}
