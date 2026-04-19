package io.b2mash.b2b.b2bstrawman.verticals.legal.statement.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain event emitted when a Statement of Account is generated for a matter (Phase 67, Epic 491A,
 * architecture §67.4.3 / ADR-250). Published from {@code StatementService.generate(...)} after the
 * underlying {@code GeneratedDocument} is persisted.
 */
public record StatementOfAccountGeneratedEvent(
    UUID projectId,
    UUID generatedDocumentId,
    LocalDate periodStart,
    LocalDate periodEnd,
    UUID generatedBy,
    Instant occurredAt) {

  public static StatementOfAccountGeneratedEvent of(
      UUID projectId,
      UUID generatedDocumentId,
      LocalDate periodStart,
      LocalDate periodEnd,
      UUID generatedBy) {
    return new StatementOfAccountGeneratedEvent(
        projectId, generatedDocumentId, periodStart, periodEnd, generatedBy, Instant.now());
  }
}
