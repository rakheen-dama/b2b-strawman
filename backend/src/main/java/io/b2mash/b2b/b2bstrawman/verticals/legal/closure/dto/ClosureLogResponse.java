package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto;

import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.MatterClosureLog;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Response row for {@code GET /api/matters/{projectId}/closure/log}.
 *
 * <p>LZKC-014: alongside the raw member ids ({@code closedBy}/{@code reopenedBy}) the response
 * carries display names ({@code closedByName}/{@code reopenedByName}) batch-resolved via {@code
 * MemberNameResolver} in {@code MatterClosureService#getLog}, so the closure-history UI never has
 * to render a bare UUID. A name is {@code null} when the member row no longer exists (or the matter
 * was never reopened).
 */
public record ClosureLogResponse(
    UUID id,
    UUID projectId,
    UUID closedBy,
    String closedByName,
    Instant closedAt,
    String reason,
    String notes,
    Map<String, Object> gateReport,
    boolean overrideUsed,
    String overrideJustification,
    UUID closureLetterDocumentId,
    Instant reopenedAt,
    UUID reopenedBy,
    String reopenedByName,
    String reopenNotes) {

  /**
   * Maps a log row to the response, looking up display names in {@code memberNames} (member id →
   * name, batch-resolved by the caller). Ids missing from the map yield {@code null} names.
   */
  public static ClosureLogResponse from(MatterClosureLog log, Map<UUID, String> memberNames) {
    return new ClosureLogResponse(
        log.getId(),
        log.getProjectId(),
        log.getClosedBy(),
        log.getClosedBy() == null ? null : memberNames.get(log.getClosedBy()),
        log.getClosedAt(),
        log.getReason(),
        log.getNotes(),
        // Defensive copy so response consumers cannot mutate the Hibernate-managed map.
        log.getGateReport() == null ? Map.of() : Map.copyOf(log.getGateReport()),
        log.isOverrideUsed(),
        log.getOverrideJustification(),
        log.getClosureLetterDocumentId(),
        log.getReopenedAt(),
        log.getReopenedBy(),
        log.getReopenedBy() == null ? null : memberNames.get(log.getReopenedBy()),
        log.getReopenNotes());
  }
}
