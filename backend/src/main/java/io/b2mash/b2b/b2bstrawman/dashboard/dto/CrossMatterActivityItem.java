package io.b2mash.b2b.b2bstrawman.dashboard.dto;

import java.time.Instant;
import java.util.UUID;

/** A single activity event from the cross-matter activity feed. */
public record CrossMatterActivityItem(
    UUID eventId,
    String eventType,
    String description,
    String actorName,
    UUID matterId,
    String matterName,
    Instant occurredAt) {}
