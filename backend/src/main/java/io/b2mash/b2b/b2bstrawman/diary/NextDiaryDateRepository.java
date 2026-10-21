package io.b2mash.b2b.b2bstrawman.diary;

import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectStatus;
import io.b2mash.b2b.b2bstrawman.task.TaskStatus;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Next diary date per matter for the Matters list (Phase 87, Epic 615A, arch §12.3.4) — one native
 * UNION ALL over the four One Diary sources, using EXACTLY {@link DiaryService}'s event
 * definitions. The SQL carries no status, kind or rank literals: {@link #findNextByMatterIdIn}
 * binds them from their single sources of truth — open tasks (not {@link TaskStatus#isTerminal()}),
 * {@link ProjectStatus#ACTIVE} matter due dates, {@link DiaryService#OPEN_COURT_STATUSES} court
 * dates, {@link DiaryService#OPEN_PRESCRIPTION_STATUSES} prescriptions, and the {@link DiaryKind}
 * names and {@link DiaryKind#rank()} values.
 *
 * <p>Court/prescription rows only when {@code includeLegal} (court_calendar module + VIEW_LEGAL —
 * the {@code DiaryService.servedKinds} gate). "Next" = earliest date {@code >= today}; same-day
 * ties broken by {@link DiaryKind#rank()}. Matters with no upcoming item return no row.
 */
public interface NextDiaryDateRepository extends Repository<Project, UUID> {

  /**
   * Next diary date/kind per matter in {@code ids}, with the event definitions bound from {@link
   * DiaryService}, {@link TaskStatus}, {@link ProjectStatus} and {@link DiaryKind}.
   */
  default List<NextDiaryDateProjection> findNextByMatterIdIn(
      Collection<UUID> ids, LocalDate today, boolean includeLegal) {
    return findNext(
        ids,
        today,
        includeLegal,
        terminalTaskStatuses(),
        ProjectStatus.ACTIVE.name(),
        DiaryService.OPEN_COURT_STATUSES,
        DiaryService.OPEN_PRESCRIPTION_STATUSES,
        DiaryKind.TASK_DUE.name(),
        DiaryKind.TASK_DUE.rank(),
        DiaryKind.MATTER_DUE.name(),
        DiaryKind.MATTER_DUE.rank(),
        DiaryKind.COURT_DATE.name(),
        DiaryKind.COURT_DATE.rank(),
        DiaryKind.PRESCRIPTION.name(),
        DiaryKind.PRESCRIPTION.rank());
  }

  /** Raw statement — call {@link #findNextByMatterIdIn}, which supplies the definitions. */
  @Query(
      nativeQuery = true,
      value =
          """
      SELECT DISTINCT ON (u.project_id)
             u.project_id AS matterId, u.d AS nextDate, u.kind AS nextKind
      FROM (
          SELECT t.project_id, t.due_date AS d,
                 CAST(:taskKind AS text) AS kind, CAST(:taskRank AS integer) AS kind_rank
          FROM tasks t
          WHERE t.project_id IN (:ids) AND t.due_date >= :today
            AND t.status NOT IN (:taskTerminalStatuses)
          UNION ALL
          SELECT p.id, p.due_date, CAST(:matterKind AS text), CAST(:matterRank AS integer)
          FROM projects p
          WHERE p.id IN (:ids) AND p.due_date >= :today AND p.status = :matterDueStatus
          UNION ALL
          SELECT cd.project_id, cd.scheduled_date,
                 CAST(:courtKind AS text), CAST(:courtRank AS integer)
          FROM court_dates cd
          WHERE :includeLegal = true AND cd.project_id IN (:ids)
            AND cd.scheduled_date >= :today AND cd.status IN (:courtStatuses)
          UNION ALL
          SELECT pt.project_id, pt.prescription_date,
                 CAST(:prescriptionKind AS text), CAST(:prescriptionRank AS integer)
          FROM prescription_trackers pt
          WHERE :includeLegal = true AND pt.project_id IN (:ids)
            AND pt.prescription_date >= :today AND pt.status IN (:prescriptionStatuses)
      ) u
      ORDER BY u.project_id, u.d, u.kind_rank
      """)
  List<NextDiaryDateProjection> findNext(
      @Param("ids") Collection<UUID> ids,
      @Param("today") LocalDate today,
      @Param("includeLegal") boolean includeLegal,
      @Param("taskTerminalStatuses") Collection<String> taskTerminalStatuses,
      @Param("matterDueStatus") String matterDueStatus,
      @Param("courtStatuses") Collection<String> courtStatuses,
      @Param("prescriptionStatuses") Collection<String> prescriptionStatuses,
      @Param("taskKind") String taskKind,
      @Param("taskRank") int taskRank,
      @Param("matterKind") String matterKind,
      @Param("matterRank") int matterRank,
      @Param("courtKind") String courtKind,
      @Param("courtRank") int courtRank,
      @Param("prescriptionKind") String prescriptionKind,
      @Param("prescriptionRank") int prescriptionRank);

  private static List<String> terminalTaskStatuses() {
    return Arrays.stream(TaskStatus.values())
        .filter(TaskStatus::isTerminal)
        .map(Enum::name)
        .toList();
  }
}
