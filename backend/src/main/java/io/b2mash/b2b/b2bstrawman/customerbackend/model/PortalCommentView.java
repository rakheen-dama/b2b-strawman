package io.b2mash.b2b.b2bstrawman.customerbackend.model;

import java.time.Instant;
import java.util.UUID;

public record PortalCommentView(
    UUID id,
    String orgId,
    UUID portalProjectId,
    String authorName,
    String content,
    Instant createdAt,
    Instant syncedAt) {}
