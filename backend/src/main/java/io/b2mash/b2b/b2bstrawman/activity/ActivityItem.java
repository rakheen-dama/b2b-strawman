package io.b2mash.b2b.b2bstrawman.activity;

import java.time.Instant;
import java.util.UUID;

/**
 * Activity feed item representing a single human-readable audit event entry. Used as the response
 * DTO for the activity feed endpoint.
 */
public record ActivityItem(
    UUID id,
    String message,
    String actorName,
    String actorAvatarUrl,
    String entityType,
    UUID entityId,
    String entityName,
    String eventType,
    Instant occurredAt) {}
