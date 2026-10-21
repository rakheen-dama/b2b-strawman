package io.b2mash.b2b.b2bstrawman.diary;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.diary.DiaryEventResponse.FeeEarnerRef;
import io.b2mash.b2b.b2bstrawman.diary.DiaryEventResponse.MatterRef;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.member.ProjectMember;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.orgrole.CapabilityAuthorizationService;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtDate;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtDateRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTracker;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTrackerRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One Diary (Phase 87, ADR-339, architecture §12.3.2): a read-only, server-side union of task due
 * dates, matter due dates, court dates and prescription dates for a window of at most 92 days. Owns
 * no table. Visibility is exactly {@code /api/calendar}'s — owner/admin see every matter, members
 * the matters they belong to — and court dates / prescriptions are additionally gated by the {@code
 * court_calendar} module and the {@code VIEW_LEGAL} capability that gate {@code
 * CourtCalendarController}, never wider.
 */
@Service
public class DiaryService {

  public static final String TYPES_HEADER = "X-Diary-Types";
  static final int MAX_WINDOW_DAYS = 92;
  static final String WINDOW_TOO_LARGE = "diary.window-too-large";
  private static final String COURT_CALENDAR_MODULE = "court_calendar";
  private static final String VIEW_LEGAL = "VIEW_LEGAL";

  /** Lower bound of the overdue band: effectively unbounded, Postgres-date safe. */
  static final LocalDate OVERDUE_FLOOR = LocalDate.of(1900, 1, 1);

  private static final List<String> OPEN_COURT_STATUSES = List.of("SCHEDULED", "POSTPONED");
  private static final List<String> OPEN_PRESCRIPTION_STATUSES = List.of("RUNNING", "WARNED");

  /** §12.3.2 ordering: date, time (nulls last), kind rank, title; id for determinism. */
  static final Comparator<DiaryEventResponse> ORDER =
      Comparator.comparing(DiaryEventResponse::date)
          .thenComparing(DiaryEventResponse::time, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparingInt(e -> e.kind().rank())
          .thenComparing(DiaryEventResponse::title, String.CASE_INSENSITIVE_ORDER)
          .thenComparing(DiaryEventResponse::id);

  /** Events plus the kinds actually served (for the {@code X-Diary-Types} header). */
  public record DiaryResult(List<DiaryEventResponse> events, Set<DiaryKind> typesServed) {

    /** Served kinds as CSV in {@link DiaryKind} declaration order; empty when none served. */
    public String typesHeader() {
      return typesServed.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }
  }

  private final TaskRepository taskRepository;
  private final ProjectRepository projectRepository;
  private final CourtDateRepository courtDateRepository;
  private final PrescriptionTrackerRepository prescriptionTrackerRepository;
  private final ProjectMemberRepository projectMemberRepository;
  private final CustomerRepository customerRepository;
  private final MemberRepository memberRepository;
  private final ProjectAccessService projectAccessService;
  private final VerticalModuleGuard moduleGuard;
  private final CapabilityAuthorizationService capabilityAuthorizationService;
  private final Clock clock;

  public DiaryService(
      TaskRepository taskRepository,
      ProjectRepository projectRepository,
      CourtDateRepository courtDateRepository,
      PrescriptionTrackerRepository prescriptionTrackerRepository,
      ProjectMemberRepository projectMemberRepository,
      CustomerRepository customerRepository,
      MemberRepository memberRepository,
      ProjectAccessService projectAccessService,
      VerticalModuleGuard moduleGuard,
      CapabilityAuthorizationService capabilityAuthorizationService,
      Clock clock) {
    this.taskRepository = taskRepository;
    this.projectRepository = projectRepository;
    this.courtDateRepository = courtDateRepository;
    this.prescriptionTrackerRepository = prescriptionTrackerRepository;
    this.projectMemberRepository = projectMemberRepository;
    this.customerRepository = customerRepository;
    this.memberRepository = memberRepository;
    this.projectAccessService = projectAccessService;
    this.moduleGuard = moduleGuard;
    this.capabilityAuthorizationService = capabilityAuthorizationService;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public DiaryResult getEvents(ActorContext actor, DiaryQuery q) {
    validateWindow(q.from(), q.to());
    if (q.matterId() != null) {
      projectAccessService.requireViewAccess(q.matterId(), actor); // 404 — same as matter detail
    }
    Set<DiaryKind> served = servedKinds(q.types());
    LocalDate today = LocalDate.now(clock);
    Window window = Window.of(q, today);
    UUID memberScope = actor.isOwnerOrAdmin() ? null : actor.memberId();

    List<Task> tasks =
        served.contains(DiaryKind.TASK_DUE)
            ? taskRepository
                .findOpenDueBetweenForDiary(window.lower(), q.to(), q.matterId(), memberScope)
                .stream()
                .filter(t -> window.includes(t.getDueDate()))
                .toList()
            : List.of();
    List<Project> dueMatters =
        served.contains(DiaryKind.MATTER_DUE)
            ? projectRepository
                .findActiveDueBetweenForDiary(window.lower(), q.to(), q.matterId(), memberScope)
                .stream()
                .filter(p -> window.includes(p.getDueDate()))
                .toList()
            : List.of();

    List<CourtDate> courtDates = List.of();
    List<PrescriptionTracker> prescriptions = List.of();
    if (served.contains(DiaryKind.COURT_DATE) || served.contains(DiaryKind.PRESCRIPTION)) {
      Predicate<UUID> visible = visibleMatters(actor, q.matterId());
      if (served.contains(DiaryKind.COURT_DATE)) {
        courtDates =
            courtDateRepository
                .findByStatusInAndScheduledDateBetween(OPEN_COURT_STATUSES, window.lower(), q.to())
                .stream()
                .filter(cd -> visible.test(cd.getProjectId()))
                .filter(cd -> window.includes(cd.getScheduledDate()))
                .toList();
      }
      if (served.contains(DiaryKind.PRESCRIPTION)) {
        prescriptions =
            prescriptionTrackerRepository
                .findByStatusInAndPrescriptionDateBetween(
                    OPEN_PRESCRIPTION_STATUSES, window.lower(), q.to())
                .stream()
                .filter(pt -> visible.test(pt.getProjectId()))
                .filter(pt -> window.includes(pt.getPrescriptionDate()))
                .toList();
      }
    }

    // --- batched lookups (one IN query each, guarded for empty input) ---
    Map<UUID, Project> matters = new HashMap<>();
    dueMatters.forEach(p -> matters.put(p.getId(), p));
    Set<UUID> missing = new HashSet<>();
    tasks.forEach(t -> missing.add(t.getProjectId()));
    courtDates.forEach(cd -> missing.add(cd.getProjectId()));
    prescriptions.forEach(pt -> missing.add(pt.getProjectId()));
    missing.removeAll(matters.keySet());
    if (!missing.isEmpty()) {
      projectRepository.findByIdIn(missing).forEach(p -> matters.put(p.getId(), p));
    }
    Map<UUID, String> clientNames = clientNames(matters.values());
    Map<UUID, UUID> leads = leadsByMatter(matters.keySet());
    Set<UUID> feeEarnerIds = new HashSet<>(leads.values());
    tasks.stream().map(Task::getAssigneeId).filter(Objects::nonNull).forEach(feeEarnerIds::add);
    Map<UUID, String> memberNames = memberNames(feeEarnerIds);

    // --- map, fee-earner fallback (assignee, else matter lead), filter, order ---
    List<DiaryEventResponse> events = new ArrayList<>();
    for (Task t : tasks) {
      Project matter = matters.get(t.getProjectId());
      if (matter != null) {
        UUID fe = t.getAssigneeId() != null ? t.getAssigneeId() : leads.get(t.getProjectId());
        events.add(
            DiaryEventResponse.fromTask(t, ref(matter, clientNames), fee(fe, memberNames), today));
      }
    }
    for (Project p : dueMatters) {
      events.add(
          DiaryEventResponse.fromMatterDue(
              p, ref(p, clientNames), fee(leads.get(p.getId()), memberNames), today));
    }
    for (CourtDate cd : courtDates) {
      Project matter = matters.get(cd.getProjectId());
      if (matter != null) {
        events.add(
            DiaryEventResponse.fromCourtDate(
                cd,
                ref(matter, clientNames),
                fee(leads.get(cd.getProjectId()), memberNames),
                today));
      }
    }
    for (PrescriptionTracker pt : prescriptions) {
      Project matter = matters.get(pt.getProjectId());
      if (matter != null) {
        events.add(
            DiaryEventResponse.fromPrescription(
                pt,
                ref(matter, clientNames),
                fee(leads.get(pt.getProjectId()), memberNames),
                today));
      }
    }

    List<DiaryEventResponse> result =
        events.stream()
            .filter(e -> q.feeEarnerId() == null || q.feeEarnerId().equals(e.feeEarnerId()))
            .sorted(ORDER)
            .toList();
    return new DiaryResult(result, served);
  }

  // --- helpers ---

  private static void validateWindow(LocalDate from, LocalDate to) {
    if (from.isAfter(to)) {
      throw new InvalidStateException(
          "Invalid date range", "'from' date must be on or before 'to' date");
    }
    long days = ChronoUnit.DAYS.between(from, to);
    if (days > MAX_WINDOW_DAYS) {
      throw InvalidStateException.withType(
          WINDOW_TOO_LARGE,
          "Diary window too large",
          "The diary window must not exceed %d days (requested %d)."
              .formatted(MAX_WINDOW_DAYS, days));
    }
  }

  /** Requested kinds minus court/prescription kinds the caller may not see (never widened). */
  private Set<DiaryKind> servedKinds(Set<DiaryKind> requested) {
    boolean legalRequested = requested.stream().anyMatch(DiaryKind::isLegal);
    boolean legalAllowed =
        legalRequested
            && moduleGuard.isModuleEnabled(COURT_CALENDAR_MODULE)
            && capabilityAuthorizationService.hasCapability(VIEW_LEGAL);
    EnumSet<DiaryKind> served = EnumSet.noneOf(DiaryKind.class);
    requested.stream().filter(k -> !k.isLegal() || legalAllowed).forEach(served::add);
    return Collections.unmodifiableSet(served);
  }

  /** Post-filter for the unscoped court/prescription finders — the /api/calendar access rule. */
  private Predicate<UUID> visibleMatters(ActorContext actor, UUID matterId) {
    if (matterId != null) {
      return matterId::equals; // access already verified by requireViewAccess
    }
    if (actor.isOwnerOrAdmin()) {
      return id -> true;
    }
    Set<UUID> mine =
        projectMemberRepository.findByMemberId(actor.memberId()).stream()
            .map(ProjectMember::getProjectId)
            .collect(Collectors.toSet());
    return mine::contains;
  }

  private Map<UUID, UUID> leadsByMatter(Set<UUID> matterIds) {
    if (matterIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, UUID> leads = new HashMap<>();
    for (ProjectMember pm : projectMemberRepository.findLeadsByProjectIdIn(matterIds)) {
      leads.putIfAbsent(pm.getProjectId(), pm.getMemberId()); // oldest lead wins
    }
    return leads;
  }

  private Map<UUID, String> clientNames(Collection<Project> matters) {
    Set<UUID> ids =
        matters.stream()
            .map(Project::getCustomerId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (ids.isEmpty()) {
      return Map.of();
    }
    Map<UUID, String> names = new HashMap<>();
    for (Customer c : customerRepository.findByIdIn(ids)) {
      names.put(c.getId(), c.getName());
    }
    return names;
  }

  private Map<UUID, String> memberNames(Set<UUID> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    Map<UUID, String> names = new HashMap<>();
    for (Member m : memberRepository.findAllById(ids)) {
      names.put(
          m.getId(), m.getName()); // HashMap tolerates a null name (Collectors.toMap does not)
    }
    return names;
  }

  private static MatterRef ref(Project p, Map<UUID, String> clientNames) {
    return new MatterRef(
        p.getId(),
        p.getName(),
        p.getReferenceNumber(),
        p.getCustomerId(),
        p.getCustomerId() == null ? null : clientNames.get(p.getCustomerId()));
  }

  private static FeeEarnerRef fee(UUID memberId, Map<UUID, String> names) {
    return memberId == null ? FeeEarnerRef.NONE : new FeeEarnerRef(memberId, names.get(memberId));
  }

  /**
   * The read window: {@code [from, to]} plus, when {@code includeOverdue}, open items dated before
   * {@code min(from, today)} — so every appended band item is genuinely overdue (§12.2.6), even
   * when the caller views a future week.
   */
  private record Window(LocalDate from, LocalDate lower, LocalDate overdueCutoff, boolean band) {

    static Window of(DiaryQuery q, LocalDate today) {
      LocalDate cutoff = q.from().isBefore(today) ? q.from() : today;
      return new Window(
          q.from(), q.includeOverdue() ? OVERDUE_FLOOR : q.from(), cutoff, q.includeOverdue());
    }

    boolean includes(LocalDate d) {
      return !d.isBefore(from) || (band && d.isBefore(overdueCutoff));
    }
  }
}
