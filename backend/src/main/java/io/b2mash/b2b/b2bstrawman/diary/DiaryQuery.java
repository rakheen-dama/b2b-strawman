package io.b2mash.b2b.b2bstrawman.diary;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Parsed {@code GET /api/diary} parameters (§12.3.2). {@code types} null/empty (or only empty
 * tokens) means all four kinds; {@code feeEarnerId}/{@code matterId} null mean "no filter"; {@code
 * includeOverdue} defaults to true at the controller.
 */
public record DiaryQuery(
    LocalDate from,
    LocalDate to,
    Set<DiaryKind> types,
    UUID feeEarnerId,
    UUID matterId,
    boolean includeOverdue) {

  public DiaryQuery {
    // "types=TASK_DUE," binds a null element (empty token) — drop it rather than NPE.
    EnumSet<DiaryKind> requested = EnumSet.noneOf(DiaryKind.class);
    if (types != null) {
      types.stream().filter(Objects::nonNull).forEach(requested::add);
    }
    types =
        Collections.unmodifiableSet(
            requested.isEmpty() ? EnumSet.allOf(DiaryKind.class) : requested);
  }
}
