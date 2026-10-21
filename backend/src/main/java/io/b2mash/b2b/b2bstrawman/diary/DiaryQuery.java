package io.b2mash.b2b.b2bstrawman.diary;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Parsed {@code GET /api/diary} parameters (§12.3.2). {@code types} null/empty means all four
 * kinds; {@code feeEarnerId}/{@code matterId} null mean "no filter"; {@code includeOverdue}
 * defaults to true at the controller.
 */
public record DiaryQuery(
    LocalDate from,
    LocalDate to,
    Set<DiaryKind> types,
    UUID feeEarnerId,
    UUID matterId,
    boolean includeOverdue) {

  public DiaryQuery {
    types =
        Collections.unmodifiableSet(
            types == null || types.isEmpty()
                ? EnumSet.allOf(DiaryKind.class)
                : EnumSet.copyOf(types));
  }
}
