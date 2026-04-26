package io.b2mash.b2b.b2bstrawman.project;

import java.time.LocalDate;

/**
 * Union row for the matter Overview "Upcoming Deadlines" tile (GAP-L-58 / E9.3).
 *
 * <p>A matter has two sources of deadlines:
 *
 * <ul>
 *   <li>Court dates set by the judge (legal vertical, {@code court_calendar} module).
 *   <li>Regulatory deadlines set by the firm (accounting vertical, {@code regulatory_deadlines}
 *       module).
 * </ul>
 *
 * <p>This DTO is the union shape returned by {@code GET /api/projects/{id}/upcoming-deadlines}.
 * Order is date ASC; {@code type} disambiguates the source so the frontend can render a small
 * badge.
 *
 * @param type either {@code "COURT"} or {@code "REGULATORY"}
 * @param date the deadline date (always future-or-today)
 * @param description short label (court name + judge for COURT, deadline-type name for REGULATORY)
 * @param status pass-through from source (e.g. {@code SCHEDULED}, {@code POSTPONED}, {@code
 *     pending}, {@code overdue}, {@code filed}); nullable
 */
public record ProjectUpcomingDeadline(
    String type, LocalDate date, String description, String status) {

  public static final String TYPE_COURT = "COURT";
  public static final String TYPE_REGULATORY = "REGULATORY";
}
