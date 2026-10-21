package io.b2mash.b2b.b2bstrawman.diary;

import io.b2mash.b2b.b2bstrawman.project.Project;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Next diary date per matter for the Matters list (Phase 87, Epic 615A, arch §12.3.4) — one native
 * UNION ALL over the four One Diary sources, using EXACTLY {@link DiaryService}'s event
 * definitions: open tasks (not DONE/CANCELLED), ACTIVE matter due dates, SCHEDULED/POSTPONED court
 * dates ({@code DiaryService.OPEN_COURT_STATUSES}), RUNNING/WARNED prescriptions ({@code
 * DiaryService.OPEN_PRESCRIPTION_STATUSES}). Keep the literals here in sync with those constants.
 *
 * <p>Court/prescription rows only when {@code includeLegal} (court_calendar module + VIEW_LEGAL —
 * the {@code DiaryService.servedKinds} gate). "Next" = earliest date {@code >= today}; same-day
 * ties broken by {@link DiaryKind#rank()} (court 0, prescription 1, matter 2, task 3). Matters with
 * no upcoming item return no row.
 */
public interface NextDiaryDateRepository extends Repository<Project, UUID> {

  @Query(
      nativeQuery = true,
      value =
          """
      SELECT DISTINCT ON (u.project_id)
             u.project_id AS matterId, u.d AS nextDate, u.kind AS nextKind
      FROM (
          SELECT t.project_id, t.due_date AS d, 'TASK_DUE' AS kind, 3 AS kind_rank
          FROM tasks t
          WHERE t.project_id IN (:ids) AND t.due_date >= :today
            AND t.status NOT IN ('DONE', 'CANCELLED')
          UNION ALL
          SELECT p.id, p.due_date, 'MATTER_DUE', 2
          FROM projects p
          WHERE p.id IN (:ids) AND p.due_date >= :today AND p.status = 'ACTIVE'
          UNION ALL
          SELECT cd.project_id, cd.scheduled_date, 'COURT_DATE', 0
          FROM court_dates cd
          WHERE :includeLegal = true AND cd.project_id IN (:ids)
            AND cd.scheduled_date >= :today AND cd.status IN ('SCHEDULED', 'POSTPONED')
          UNION ALL
          SELECT pt.project_id, pt.prescription_date, 'PRESCRIPTION', 1
          FROM prescription_trackers pt
          WHERE :includeLegal = true AND pt.project_id IN (:ids)
            AND pt.prescription_date >= :today AND pt.status IN ('RUNNING', 'WARNED')
      ) u
      ORDER BY u.project_id, u.d, u.kind_rank
      """)
  List<NextDiaryDateProjection> findNextByMatterIdIn(
      @Param("ids") Collection<UUID> ids,
      @Param("today") LocalDate today,
      @Param("includeLegal") boolean includeLegal);
}
