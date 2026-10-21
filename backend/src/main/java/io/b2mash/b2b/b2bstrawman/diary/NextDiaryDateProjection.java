package io.b2mash.b2b.b2bstrawman.diary;

import java.time.LocalDate;
import java.util.UUID;

/** Native projection for {@link NextDiaryDateRepository#findNextByMatterIdIn}. */
public interface NextDiaryDateProjection {

  UUID getMatterId();

  LocalDate getNextDate();

  /** A {@link DiaryKind} name: TASK_DUE, MATTER_DUE, COURT_DATE or PRESCRIPTION. */
  String getNextKind();
}
