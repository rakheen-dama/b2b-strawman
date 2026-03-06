package io.b2mash.b2b.b2bstrawman.customerbackend.model;

import java.time.Instant;
import java.util.UUID;

public record PortalRequestItemView(
    UUID id,
    UUID requestId,
    String name,
    String description,
    String responseType,
    boolean required,
    String fileTypeHints,
    int sortOrder,
    String status,
    String rejectionReason,
    UUID documentId,
    String textResponse,
    Instant syncedAt) {}
