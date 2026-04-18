package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a legal matter is reopened from {@code CLOSED} back to {@code ACTIVE}
 * (Phase 67, Epic 489B, ADR-248).
 */
public record MatterReopenedEvent(
    UUID projectId, UUID reopenedBy, String notes, Instant occurredAt) {

  public static MatterReopenedEvent of(UUID projectId, UUID reopenedBy, String notes) {
    return new MatterReopenedEvent(projectId, reopenedBy, notes, Instant.now());
  }
}
