package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto;

import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.MatterClosureLog;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Response row for {@code GET /api/matters/{projectId}/closure/log}. */
public record ClosureLogResponse(
    UUID id,
    UUID projectId,
    UUID closedBy,
    Instant closedAt,
    String reason,
    String notes,
    Map<String, Object> gateReport,
    boolean overrideUsed,
    String overrideJustification,
    UUID closureLetterDocumentId,
    Instant reopenedAt,
    UUID reopenedBy,
    String reopenNotes) {

  public static ClosureLogResponse from(MatterClosureLog log) {
    return new ClosureLogResponse(
        log.getId(),
        log.getProjectId(),
        log.getClosedBy(),
        log.getClosedAt(),
        log.getReason(),
        log.getNotes(),
        log.getGateReport(),
        log.isOverrideUsed(),
        log.getOverrideJustification(),
        log.getClosureLetterDocumentId(),
        log.getReopenedAt(),
        log.getReopenedBy(),
        log.getReopenNotes());
  }
}
