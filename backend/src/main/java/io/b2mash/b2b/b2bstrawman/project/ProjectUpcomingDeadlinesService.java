package io.b2mash.b2b.b2bstrawman.project;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtDate;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtDateRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates upcoming deadlines for a single matter (project): its future-or-today court dates
 * ({@code court_dates}), mapped to {@code type = COURT}.
 *
 * <p>Guarded by the {@code court_calendar} vertical module — if it is disabled for the tenant the
 * result is empty. The firm-side regulatory deadlines source ({@code regulatory_deadlines}) was
 * removed in Phase 86 (Epic 604A); a legal-only org surfaces only court dates.
 *
 * <p>Slice 14/26 of QA Cycle 2026-04-25 (GAP-L-58 / E9.3).
 */
@Service
public class ProjectUpcomingDeadlinesService {

  private final ProjectRepository projectRepository;
  private final ProjectAccessService projectAccessService;
  private final CourtDateRepository courtDateRepository;
  private final VerticalModuleGuard moduleGuard;
  private final Clock clock;

  public ProjectUpcomingDeadlinesService(
      ProjectRepository projectRepository,
      ProjectAccessService projectAccessService,
      CourtDateRepository courtDateRepository,
      VerticalModuleGuard moduleGuard,
      Clock clock) {
    this.projectRepository = projectRepository;
    this.projectAccessService = projectAccessService;
    this.courtDateRepository = courtDateRepository;
    this.moduleGuard = moduleGuard;
    this.clock = clock;
  }

  /**
   * Returns the court dates for a matter, future-or-today only, sorted by date ASC. Caller must
   * have view access to the project (enforced via {@link ProjectAccessService#requireViewAccess}).
   */
  @Transactional(readOnly = true)
  public List<MatterUpcomingDeadline> getUpcomingDeadlines(UUID projectId, ActorContext actor) {
    projectRepository
        .findById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    projectAccessService.requireViewAccess(projectId, actor);

    LocalDate today = LocalDate.now(clock);
    List<MatterUpcomingDeadline> rows = new ArrayList<>();

    // --- Court dates (legal vertical) ---
    if (moduleGuard.isModuleEnabled("court_calendar")) {
      List<CourtDate> courtDates =
          courtDateRepository.findByProjectIdOrderByScheduledDateAsc(projectId);
      for (CourtDate cd : courtDates) {
        if (cd.getScheduledDate().isBefore(today)) {
          continue;
        }
        rows.add(
            new MatterUpcomingDeadline(
                MatterUpcomingDeadline.TYPE_COURT,
                cd.getScheduledDate(),
                describeCourtDate(cd),
                cd.getStatus()));
      }
    }

    rows.sort(Comparator.comparing(MatterUpcomingDeadline::date));
    return rows;
  }

  private static String describeCourtDate(CourtDate cd) {
    String name = cd.getCourtName();
    if (cd.getJudgeMagistrate() != null && !cd.getJudgeMagistrate().isBlank()) {
      return name + " — " + cd.getJudgeMagistrate();
    }
    return name;
  }
}
