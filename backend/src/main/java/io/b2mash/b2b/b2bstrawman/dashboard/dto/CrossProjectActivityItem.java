package io.b2mash.b2b.b2bstrawman.dashboard.dto;

import java.time.Instant;
import java.util.UUID;

/** A single activity event from the cross-project activity feed. */
public record CrossProjectActivityItem(
    UUID eventId,
    String eventType,
    String description,
    String actorName,
    UUID projectId,
    String projectName,
    Instant occurredAt) {}
