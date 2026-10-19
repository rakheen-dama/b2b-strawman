package io.b2mash.b2b.b2bstrawman.project;

import java.time.LocalDate;

/**
 * Row for the matter Overview "Upcoming Deadlines" tile (GAP-L-58 / E9.3).
 *
 * <p>A matter's upcoming deadlines are its court dates set by the judge (legal vertical, {@code
 * court_calendar} module). The firm-side regulatory deadlines source was removed in Phase 86 (Epic
 * 604A).
 *
 * <p>This DTO is the shape returned by {@code GET /api/matters/{matterId}/upcoming-deadlines}.
 * Order is date ASC; {@code type} identifies the source so the frontend can render a small badge.
 *
 * @param type always {@code "COURT"}
 * @param date the deadline date (always future-or-today)
 * @param description short label (court name + judge)
 * @param status pass-through from source (e.g. {@code SCHEDULED}, {@code POSTPONED}); nullable
 */
public record MatterUpcomingDeadline(
    String type, LocalDate date, String description, String status) {

  public static final String TYPE_COURT = "COURT";
}
