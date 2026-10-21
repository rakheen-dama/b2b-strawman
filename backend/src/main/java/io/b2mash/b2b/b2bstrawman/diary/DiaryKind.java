package io.b2mash.b2b.b2bstrawman.diary;

/**
 * Kinds of One Diary event (Phase 87, ADR-339). Declaration order is the canonical order of the
 * {@code X-Diary-Types} header; {@link #rank()} is the within-day sort order (§12.3.2): court dates
 * first (immovable), tasks last (most numerous, most movable).
 */
public enum DiaryKind {
  TASK_DUE(3),
  MATTER_DUE(2),
  COURT_DATE(0),
  PRESCRIPTION(1);

  private final int rank;

  DiaryKind(int rank) {
    this.rank = rank;
  }

  /** Within-day sort rank (lower sorts first). */
  public int rank() {
    return rank;
  }

  /** Served only when the court_calendar module is enabled and the caller has VIEW_LEGAL. */
  public boolean isLegal() {
    return this == COURT_DATE || this == PRESCRIPTION;
  }
}
