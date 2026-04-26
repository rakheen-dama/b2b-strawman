package io.b2mash.b2b.b2bstrawman.project;

import io.b2mash.b2b.b2bstrawman.deadline.DeadlineCalculationService;
import io.b2mash.b2b.b2bstrawman.deadline.DeadlineCalculationService.CalculatedDeadline;
import io.b2mash.b2b.b2bstrawman.deadline.DeadlineCalculationService.DeadlineFilters;
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
 * Aggregates upcoming deadlines for a single matter (project) by unioning two sources:
 *
 * <ul>
 *   <li>{@code court_dates} — judge-set hearings, mapped to {@code type = COURT}.
 *   <li>Regulatory deadlines — firm-set filing/compliance dates, mapped to {@code type =
 *       REGULATORY}. Computed via {@link DeadlineCalculationService} and filtered to the rows whose
 *       {@code linkedProjectId} matches this matter.
 * </ul>
 *
 * <p>Both sides are guarded by their respective vertical module ({@code court_calendar} for court
 * dates, {@code regulatory_deadlines} for filings). If a side's module is disabled for the tenant,
 * that side contributes zero rows; this is intentional — a legal-only org will only surface court
 * dates, an accounting-only org only regulatories. Mixed verticals get both.
 *
 * <p>Slice 14/26 of QA Cycle 2026-04-25 (GAP-L-58 / E9.3).
 */
@Service
public class ProjectUpcomingDeadlinesService {

  /** Lookahead window for regulatory deadlines. One year covers all currently-defined ZA cycles. */
  private static final int REGULATORY_LOOKAHEAD_DAYS = 365;

  private final ProjectRepository projectRepository;
  private final ProjectAccessService projectAccessService;
  private final CourtDateRepository courtDateRepository;
  private final DeadlineCalculationService deadlineCalculationService;
  private final VerticalModuleGuard moduleGuard;
  private final Clock clock;

  public ProjectUpcomingDeadlinesService(
      ProjectRepository projectRepository,
      ProjectAccessService projectAccessService,
      CourtDateRepository courtDateRepository,
      DeadlineCalculationService deadlineCalculationService,
      VerticalModuleGuard moduleGuard,
      Clock clock) {
    this.projectRepository = projectRepository;
    this.projectAccessService = projectAccessService;
    this.courtDateRepository = courtDateRepository;
    this.deadlineCalculationService = deadlineCalculationService;
    this.moduleGuard = moduleGuard;
    this.clock = clock;
  }

  /**
   * Returns the union of court dates + regulatory deadlines for a matter, future-or-today only,
   * sorted by date ASC. Caller must have view access to the project (enforced via {@link
   * ProjectAccessService#requireViewAccess}).
   */
  @Transactional(readOnly = true)
  public List<ProjectUpcomingDeadline> getUpcomingDeadlines(UUID projectId, ActorContext actor) {
    var project =
        projectRepository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    projectAccessService.requireViewAccess(projectId, actor);

    LocalDate today = LocalDate.now(clock);
    List<ProjectUpcomingDeadline> rows = new ArrayList<>();

    // --- Court dates (legal vertical) ---
    if (moduleGuard.isModuleEnabled("court_calendar")) {
      List<CourtDate> courtDates =
          courtDateRepository.findByProjectIdOrderByScheduledDateAsc(projectId);
      for (CourtDate cd : courtDates) {
        if (cd.getScheduledDate().isBefore(today)) {
          continue;
        }
        rows.add(
            new ProjectUpcomingDeadline(
                ProjectUpcomingDeadline.TYPE_COURT,
                cd.getScheduledDate(),
                describeCourtDate(cd),
                cd.getStatus()));
      }
    }

    // --- Regulatory deadlines (accounting vertical) ---
    // Computed deadlines are customer-scoped; filter to rows whose linkedProjectId matches.
    if (moduleGuard.isModuleEnabled("regulatory_deadlines") && project.getCustomerId() != null) {
      LocalDate windowEnd = today.plusDays(REGULATORY_LOOKAHEAD_DAYS);
      List<CalculatedDeadline> regs =
          deadlineCalculationService.calculateDeadlines(
              today, windowEnd, new DeadlineFilters(null, null, project.getCustomerId()));
      for (CalculatedDeadline d : regs) {
        if (!projectId.equals(d.linkedProjectId())) {
          continue;
        }
        rows.add(
            new ProjectUpcomingDeadline(
                ProjectUpcomingDeadline.TYPE_REGULATORY,
                d.dueDate(),
                d.deadlineTypeName(),
                d.status()));
      }
    }

    rows.sort(Comparator.comparing(ProjectUpcomingDeadline::date));
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
