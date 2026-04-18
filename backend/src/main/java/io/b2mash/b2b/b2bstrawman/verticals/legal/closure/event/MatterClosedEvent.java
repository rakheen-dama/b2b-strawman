package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a legal matter transitions to {@code CLOSED} (Phase 67, Epic 489B,
 * ADR-248). Consumed by {@link MatterClosureNotificationHandler} to fan out in-app notifications to
 * the matter owner and org admins.
 */
public record MatterClosedEvent(
    UUID projectId,
    UUID closureLogId,
    String reason,
    boolean override,
    UUID closedBy,
    Instant occurredAt) {

  public static MatterClosedEvent of(
      UUID projectId, UUID closureLogId, String reason, boolean override, UUID closedBy) {
    return new MatterClosedEvent(
        projectId, closureLogId, reason, override, closedBy, Instant.now());
  }
}
