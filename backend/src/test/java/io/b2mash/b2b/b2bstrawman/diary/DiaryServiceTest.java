package io.b2mash.b2b.b2bstrawman.diary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
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
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestIds;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtDate;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtDateRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTracker;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTrackerRepository;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class DiaryServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
  private static final UUID CREATOR = UUID.randomUUID();

  @Mock private TaskRepository taskRepository;
  @Mock private ProjectRepository projectRepository;
  @Mock private CourtDateRepository courtDateRepository;
  @Mock private PrescriptionTrackerRepository prescriptionTrackerRepository;
  @Mock private ProjectMemberRepository projectMemberRepository;
  @Mock private CustomerRepository customerRepository;
  @Mock private MemberRepository memberRepository;
  @Mock private ProjectAccessService projectAccessService;
  @Mock private VerticalModuleGuard moduleGuard;
  @Mock private CapabilityAuthorizationService capabilityAuthorizationService;

  private DiaryService service;
  private final ActorContext admin = new ActorContext(UUID.randomUUID(), "admin");
  private final ActorContext member = new ActorContext(UUID.randomUUID(), "member");

  /** Matters the test registered; served by the {@code projectRepository.findByIdIn} stub. */
  private final List<Project> knownMatters = new ArrayList<>();

  private final List<Customer> knownClients = new ArrayList<>();
  private final List<Member> knownPeople = new ArrayList<>();
  private final List<ProjectMember> knownLeads = new ArrayList<>();

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    service =
        new DiaryService(
            taskRepository,
            projectRepository,
            courtDateRepository,
            prescriptionTrackerRepository,
            projectMemberRepository,
            customerRepository,
            memberRepository,
            projectAccessService,
            moduleGuard,
            capabilityAuthorizationService,
            clock);
    lenient().when(moduleGuard.isModuleEnabled("court_calendar")).thenReturn(true);
    lenient().when(capabilityAuthorizationService.hasCapability("VIEW_LEGAL")).thenReturn(true);
    lenient()
        .when(taskRepository.findOpenDueBetweenForDiary(any(), any(), any(), any()))
        .thenReturn(List.of());
    lenient()
        .when(projectRepository.findActiveDueBetweenForDiary(any(), any(), any(), any()))
        .thenReturn(List.of());
    lenient()
        .when(courtDateRepository.findByStatusInAndScheduledDateBetween(any(), any(), any()))
        .thenReturn(List.of());
    lenient()
        .when(
            prescriptionTrackerRepository.findByStatusInAndPrescriptionDateBetween(
                any(), any(), any()))
        .thenReturn(List.of());
    lenient()
        .when(projectRepository.findByIdIn(anyCollection()))
        .thenAnswer(
            inv -> {
              Collection<UUID> ids = inv.getArgument(0);
              return knownMatters.stream().filter(p -> ids.contains(p.getId())).toList();
            });
    lenient()
        .when(customerRepository.findByIdIn(anyCollection()))
        .thenAnswer(
            inv -> {
              Collection<UUID> ids = inv.getArgument(0);
              return knownClients.stream().filter(c -> ids.contains(c.getId())).toList();
            });
    lenient()
        .when(projectMemberRepository.findLeadsByProjectIdIn(anyCollection()))
        .thenAnswer(
            inv -> {
              Collection<UUID> ids = inv.getArgument(0);
              return knownLeads.stream().filter(pm -> ids.contains(pm.getProjectId())).toList();
            });
    lenient()
        .when(memberRepository.findAllById(anyIterable()))
        .thenAnswer(
            inv -> {
              Iterable<UUID> ids = inv.getArgument(0);
              Set<UUID> wanted = new java.util.HashSet<>();
              ids.forEach(wanted::add);
              return knownPeople.stream().filter(m -> wanted.contains(m.getId())).toList();
            });
  }

  // ---------------------------------------------------------------- 1. ordering

  @Test
  void orders_byDate_thenTimeNullsLast_thenKindRank_thenTitleCaseInsensitive() {
    Project m = matter("Matter One", null);
    LocalDate day = TODAY.plusDays(2);
    m.setDueDate(day);
    Task earlier = task(m, "Earlier task", TODAY.plusDays(1), null);
    Task zulu = task(m, "Zulu", day, null);
    Task apple = task(m, "apple", day, null);
    CourtDate courtNoTime = courtDate(m, day, null, "Court B", null);
    CourtDate courtTen = courtDate(m, day, LocalTime.of(10, 0), "Court A", null);
    PrescriptionTracker rx = prescription(m, day, "GENERAL_3Y");

    when(taskRepository.findOpenDueBetweenForDiary(any(), any(), any(), any()))
        .thenReturn(List.of(zulu, earlier, apple));
    when(projectRepository.findActiveDueBetweenForDiary(any(), any(), any(), any()))
        .thenReturn(List.of(m));
    when(courtDateRepository.findByStatusInAndScheduledDateBetween(any(), any(), any()))
        .thenReturn(List.of(courtNoTime, courtTen));
    when(prescriptionTrackerRepository.findByStatusInAndPrescriptionDateBetween(
            any(), any(), any()))
        .thenReturn(List.of(rx));

    var result = service.getEvents(admin, week());

    assertThat(result.events())
        .extracting(DiaryEventResponse::id)
        .containsExactly(
            earlier.getId(),
            courtTen.getId(),
            courtNoTime.getId(),
            rx.getId(),
            m.getId(),
            apple.getId(),
            zulu.getId());
  }

  // ---------------------------------------------------------------- 2/3. fee earner

  @Test
  void feeEarner_isAssignee_elseMatterLead_andNullWhenNeither() {
    Member lead = person("Lead Attorney");
    Member assignee = person("Assigned Clerk");
    Project led = matter("Led Matter", null);
    led.setDueDate(TODAY.plusDays(1));
    Project leadless = matter("Leadless Matter", null);
    lead(led, lead.getId());

    Task assigned = task(led, "Assigned", TODAY.plusDays(1), assignee.getId());
    Task unassigned = task(led, "Unassigned", TODAY.plusDays(1), null);
    Task orphan = task(leadless, "Orphan", TODAY.plusDays(1), null);
    CourtDate cd = courtDate(led, TODAY.plusDays(2), null, "High Court", null);
    PrescriptionTracker rx = prescription(led, TODAY.plusDays(3), "DEBT_6Y");
    CourtDate leadlessCd = courtDate(leadless, TODAY.plusDays(2), null, "Magistrate", null);

    when(taskRepository.findOpenDueBetweenForDiary(any(), any(), any(), any()))
        .thenReturn(List.of(assigned, unassigned, orphan));
    when(projectRepository.findActiveDueBetweenForDiary(any(), any(), any(), any()))
        .thenReturn(List.of(led));
    when(courtDateRepository.findByStatusInAndScheduledDateBetween(any(), any(), any()))
        .thenReturn(List.of(cd, leadlessCd));
    when(prescriptionTrackerRepository.findByStatusInAndPrescriptionDateBetween(
            any(), any(), any()))
        .thenReturn(List.of(rx));

    var events = service.getEvents(admin, week()).events();

    assertThat(byId(events, assigned.getId()).feeEarnerId()).isEqualTo(assignee.getId());
    assertThat(byId(events, assigned.getId()).feeEarnerName()).isEqualTo("Assigned Clerk");
    assertThat(byId(events, unassigned.getId()).feeEarnerId()).isEqualTo(lead.getId());
    assertThat(byId(events, unassigned.getId()).feeEarnerName()).isEqualTo("Lead Attorney");
    assertThat(byId(events, cd.getId()).feeEarnerId()).isEqualTo(lead.getId());
    assertThat(byId(events, rx.getId()).feeEarnerId()).isEqualTo(lead.getId());
    assertThat(byId(events, led.getId()).feeEarnerId()).isEqualTo(lead.getId());
    assertThat(byId(events, orphan.getId()).feeEarnerId()).isNull();
    assertThat(byId(events, orphan.getId()).feeEarnerName()).isNull();
    assertThat(byId(events, leadlessCd.getId()).feeEarnerId()).isNull();
  }

  @Test
  void feeEarner_oldestLeadWins_whenMatterHasTwoLeadRows() {
    Member first = person("First Lead");
    Member second = person("Second Lead");
    Project m = matter("Two Leads", null);
    lead(m, first.getId());
    lead(m, second.getId());
    Task t = task(m, "Unassigned", TODAY.plusDays(1), null);
    when(taskRepository.findOpenDueBetweenForDiary(any(), any(), any(), any()))
        .thenReturn(List.of(t));

    var events = service.getEvents(admin, week()).events();

    assertThat(byId(events, t.getId()).feeEarnerId()).isEqualTo(first.getId());
  }

  @Test
  void feeEarnerFilter_matchesAssigneeForTasks_andLeadForEverythingElse() {
    Member lead = person("Lead Attorney");
    Member other = person("Someone Else");
    Project led = matter("Led Matter", null);
    led.setDueDate(TODAY.plusDays(1));
    Project leadless = matter("Leadless Matter", null);
    leadless.setDueDate(TODAY.plusDays(1));
    lead(led, lead.getId());

    Task assignedElsewhere = task(led, "Someone else's", TODAY.plusDays(1), other.getId());
    Task unassigned = task(led, "Unassigned", TODAY.plusDays(1), null);
    Task leadlessTask = task(leadless, "Leadless task", TODAY.plusDays(1), null);
    CourtDate cd = courtDate(led, TODAY.plusDays(2), null, "High Court", null);
    CourtDate leadlessCd = courtDate(leadless, TODAY.plusDays(2), null, "Magistrate", null);
    PrescriptionTracker rx = prescription(led, TODAY.plusDays(3), "GENERAL_3Y");

    when(taskRepository.findOpenDueBetweenForDiary(any(), any(), any(), any()))
        .thenReturn(List.of(assignedElsewhere, unassigned, leadlessTask));
    when(projectRepository.findActiveDueBetweenForDiary(any(), any(), any(), any()))
        .thenReturn(List.of(led, leadless));
    when(courtDateRepository.findByStatusInAndScheduledDateBetween(any(), any(), any()))
        .thenReturn(List.of(cd, leadlessCd));
    when(prescriptionTrackerRepository.findByStatusInAndPrescriptionDateBetween(
            any(), any(), any()))
        .thenReturn(List.of(rx));

    var q = new DiaryQuery(TODAY.minusDays(2), TODAY.plusDays(4), null, lead.getId(), null, true);
    var ids = service.getEvents(admin, q).events().stream().map(DiaryEventResponse::id).toList();

    assertThat(ids)
        .containsExactlyInAnyOrder(unassigned.getId(), cd.getId(), rx.getId(), led.getId());
  }

  // ---------------------------------------------------------------- 4/5. overdue

  @Test
  void overdueFlag_isDateBeforeToday() {
    Project m = matter("Matter", null);
    Task yesterday = task(m, "Yesterday", TODAY.minusDays(1), null);
    Task today = task(m, "Today", TODAY, null);
    Task tomorrow = task(m, "Tomorrow", TODAY.plusDays(1), null);
    when(taskRepository.findOpenDueBetweenForDiary(any(), any(), any(), any()))
        .thenReturn(List.of(yesterday, today, tomorrow));

    var events = service.getEvents(admin, week()).events();

    assertThat(byId(events, yesterday.getId()).overdue()).isTrue();
    assertThat(byId(events, today.getId()).overdue()).isFalse();
    assertThat(byId(events, tomorrow.getId()).overdue()).isFalse();
  }

  @Test
  void query_dropsNullTypeElements_andDefaultsToAllWhenOnlyNulls() {
    var withNull = new HashSet<>(Arrays.asList(DiaryKind.TASK_DUE, null));
    assertThat(new DiaryQuery(TODAY, TODAY, withNull, null, null, true).types())
        .containsExactly(DiaryKind.TASK_DUE);

    var onlyNull = new HashSet<DiaryKind>(Collections.singleton(null));
    assertThat(new DiaryQuery(TODAY, TODAY, onlyNull, null, null, true).types())
        .containsExactlyElementsOf(EnumSet.allOf(DiaryKind.class));
  }

  @Test
  void overdueFlag_isFalseForTerminalStatus() {
    assertThat(DiaryEventResponse.isOverdue(TODAY.minusDays(3), "HEARD", TODAY)).isFalse();
    assertThat(DiaryEventResponse.isOverdue(TODAY.minusDays(3), "DONE", TODAY)).isFalse();
    assertThat(DiaryEventResponse.isOverdue(TODAY.minusDays(3), "SCHEDULED", TODAY)).isTrue();
  }

  @Test
  void overdueBand_appendsOpenItemsBeforeFrom_andQueriesFromTheFloor() {
    Project m = matter("Matter", null);
    Task old = task(m, "Old", TODAY.minusDays(30), null);
    when(taskRepository.findOpenDueBetweenForDiary(any(), any(), any(), any()))
        .thenReturn(List.of(old));
    var q = new DiaryQuery(TODAY, TODAY.plusDays(6), null, null, null, true);

    var events = service.getEvents(admin, q).events();

    assertThat(byId(events, old.getId()).overdue()).isTrue();
    verify(taskRepository)
        .findOpenDueBetweenForDiary(
            eq(DiaryService.OVERDUE_FLOOR), eq(TODAY.plusDays(6)), isNull(), isNull());
  }

  @Test
  void overdueBand_disabled_queriesFromFrom() {
    var q = new DiaryQuery(TODAY, TODAY.plusDays(6), null, null, null, false);

    service.getEvents(admin, q);

    verify(taskRepository)
        .findOpenDueBetweenForDiary(eq(TODAY), eq(TODAY.plusDays(6)), isNull(), isNull());
    verify(courtDateRepository)
        .findByStatusInAndScheduledDateBetween(
            eq(List.of("SCHEDULED", "POSTPONED")), eq(TODAY), eq(TODAY.plusDays(6)));
  }

  @Test
  void overdueBand_futureWindow_dropsNotYetDueItemsBetweenTodayAndFrom() {
    Project m = matter("Matter", null);
    Task notYetDue = task(m, "Not yet due", TODAY.plusDays(3), null);
    Task overdue = task(m, "Overdue", TODAY.minusDays(3), null);
    when(taskRepository.findOpenDueBetweenForDiary(any(), any(), any(), any()))
        .thenReturn(List.of(notYetDue, overdue));
    var q = new DiaryQuery(TODAY.plusDays(7), TODAY.plusDays(13), null, null, null, true);

    var ids = service.getEvents(admin, q).events().stream().map(DiaryEventResponse::id).toList();

    assertThat(ids).contains(overdue.getId()).doesNotContain(notYetDue.getId());
  }

  // ---------------------------------------------------------------- 6. window cap

  @Test
  void windowOf92DaysIsAllowed() {
    var q = new DiaryQuery(TODAY, TODAY.plusDays(92), null, null, null, true);

    assertThat(service.getEvents(admin, q).events()).isEmpty();
  }

  @Test
  void windowOver92Days_throwsTypedBadRequest() {
    var q = new DiaryQuery(TODAY, TODAY.plusDays(93), null, null, null, true);

    assertThatThrownBy(() -> service.getEvents(admin, q))
        .isInstanceOfSatisfying(
            InvalidStateException.class,
            ex -> {
              assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(ex.getBody().getType()).isEqualTo(URI.create("diary.window-too-large"));
              assertThat(ex.getBody().getTitle()).isEqualTo("Diary window too large");
            });
    verifyNoInteractions(taskRepository, projectRepository, courtDateRepository);
  }

  @Test
  void fromAfterTo_throwsBadRequest() {
    var q = new DiaryQuery(TODAY, TODAY.minusDays(1), null, null, null, true);

    assertThatThrownBy(() -> service.getEvents(admin, q))
        .isInstanceOfSatisfying(
            InvalidStateException.class,
            ex -> assertThat(ex.getBody().getTitle()).isEqualTo("Invalid date range"));
  }

  // ---------------------------------------------------------------- 7. types gate

  @Test
  void typesGate_moduleOff_omitsLegalKinds_andDoesNotQueryThem() {
    when(moduleGuard.isModuleEnabled("court_calendar")).thenReturn(false);

    var result = service.getEvents(admin, week());

    assertThat(result.typesServed()).containsExactly(DiaryKind.TASK_DUE, DiaryKind.MATTER_DUE);
    assertThat(result.typesHeader()).isEqualTo("TASK_DUE,MATTER_DUE");
    verifyNoInteractions(courtDateRepository, prescriptionTrackerRepository);
  }

  @Test
  void typesGate_withoutViewLegal_omitsLegalKinds() {
    when(capabilityAuthorizationService.hasCapability("VIEW_LEGAL")).thenReturn(false);

    var result = service.getEvents(admin, week());

    assertThat(result.typesHeader()).isEqualTo("TASK_DUE,MATTER_DUE");
    verifyNoInteractions(courtDateRepository, prescriptionTrackerRepository);
  }

  @Test
  void typesGate_onlyLegalRequested_andNotAllowed_servesNothing() {
    when(moduleGuard.isModuleEnabled("court_calendar")).thenReturn(false);
    var q =
        new DiaryQuery(TODAY, TODAY.plusDays(6), Set.of(DiaryKind.COURT_DATE), null, null, true);

    var result = service.getEvents(admin, q);

    assertThat(result.events()).isEmpty();
    assertThat(result.typesHeader()).isEmpty();
    verifyNoInteractions(taskRepository, projectRepository, courtDateRepository);
  }

  @Test
  void typesGate_nonLegalRequest_neverConsultsModuleGuard() {
    var q = new DiaryQuery(TODAY, TODAY.plusDays(6), Set.of(DiaryKind.TASK_DUE), null, null, true);

    var result = service.getEvents(admin, q);

    assertThat(result.typesHeader()).isEqualTo("TASK_DUE");
    verify(moduleGuard, never()).isModuleEnabled(any());
    verifyNoInteractions(projectRepository, courtDateRepository, prescriptionTrackerRepository);
  }

  @Test
  void typesHeader_listsAllFourInDeclarationOrder() {
    var result = service.getEvents(admin, week());

    assertThat(result.typesServed()).isEqualTo(EnumSet.allOf(DiaryKind.class));
    assertThat(result.typesHeader()).isEqualTo("TASK_DUE,MATTER_DUE,COURT_DATE,PRESCRIPTION");
  }

  // ---------------------------------------------------------------- 8. member scoping

  @Test
  void member_seesCourtDatesOnlyOnOwnMatters_andFindersAreMemberScoped() {
    Project mine = matter("Mine", null);
    Project theirs = matter("Theirs", null);
    when(projectMemberRepository.findByMemberId(member.memberId()))
        .thenReturn(List.of(new ProjectMember(mine.getId(), member.memberId(), "member", null)));
    CourtDate onMine = courtDate(mine, TODAY.plusDays(1), null, "Court", null);
    CourtDate onTheirs = courtDate(theirs, TODAY.plusDays(1), null, "Court", null);
    PrescriptionTracker rxTheirs = prescription(theirs, TODAY.plusDays(2), "GENERAL_3Y");
    when(courtDateRepository.findByStatusInAndScheduledDateBetween(any(), any(), any()))
        .thenReturn(List.of(onMine, onTheirs));
    when(prescriptionTrackerRepository.findByStatusInAndPrescriptionDateBetween(
            any(), any(), any()))
        .thenReturn(List.of(rxTheirs));

    var ids =
        service.getEvents(member, week()).events().stream().map(DiaryEventResponse::id).toList();

    assertThat(ids).contains(onMine.getId()).doesNotContain(onTheirs.getId(), rxTheirs.getId());
    verify(taskRepository)
        .findOpenDueBetweenForDiary(any(), any(), isNull(), eq(member.memberId()));
    verify(projectRepository)
        .findActiveDueBetweenForDiary(any(), any(), isNull(), eq(member.memberId()));
  }

  @Test
  void admin_findersAreUnscoped() {
    service.getEvents(admin, week());

    verify(taskRepository).findOpenDueBetweenForDiary(any(), any(), isNull(), isNull());
    verify(projectRepository).findActiveDueBetweenForDiary(any(), any(), isNull(), isNull());
    verify(projectMemberRepository, never()).findByMemberId(any());
  }

  // ---------------------------------------------------------------- 9. matterId

  @Test
  void matterId_inaccessible_propagates404() {
    UUID matterId = UUID.randomUUID();
    when(projectAccessService.requireViewAccess(matterId, member))
        .thenThrow(new ResourceNotFoundException("Project", matterId));
    var q = new DiaryQuery(TODAY, TODAY.plusDays(6), null, null, matterId, true);

    assertThatThrownBy(() -> service.getEvents(member, q))
        .isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(taskRepository, courtDateRepository);
  }

  @Test
  void matterId_narrowsEverySource() {
    Project a = matter("A", null);
    Project b = matter("B", null);
    CourtDate onA = courtDate(a, TODAY.plusDays(1), null, "Court", null);
    CourtDate onB = courtDate(b, TODAY.plusDays(1), null, "Court", null);
    when(courtDateRepository.findByStatusInAndScheduledDateBetween(any(), any(), any()))
        .thenReturn(List.of(onA, onB));
    var q = new DiaryQuery(TODAY, TODAY.plusDays(6), null, null, a.getId(), true);

    var ids = service.getEvents(member, q).events().stream().map(DiaryEventResponse::id).toList();

    assertThat(ids).contains(onA.getId()).doesNotContain(onB.getId());
    verify(projectAccessService).requireViewAccess(a.getId(), member);
    verify(taskRepository)
        .findOpenDueBetweenForDiary(any(), any(), eq(a.getId()), eq(member.memberId()));
    verify(projectRepository)
        .findActiveDueBetweenForDiary(any(), any(), eq(a.getId()), eq(member.memberId()));
  }

  // ---------------------------------------------------------------- 10. shape

  @Test
  void shape_hrefsTitlesSubtypesAndClient() {
    Customer client = client("T. Dlamini");
    Project m = matter("Dlamini v RAF", client.getId());
    m.setReferenceNumber("RAF-2026-001");
    m.setDueDate(TODAY.plusDays(4));
    Task t = task(m, "File heads", TODAY.plusDays(1), null);
    CourtDate withDesc =
        courtDate(m, TODAY.plusDays(1), LocalTime.of(9, 30), "Johannesburg High Court", "Rule 43");
    CourtDate blankDesc = courtDate(m, TODAY.plusDays(2), null, "Pretoria High Court", "  ");
    PrescriptionTracker rx = prescription(m, TODAY.plusDays(3), "GENERAL_3Y");
    when(taskRepository.findOpenDueBetweenForDiary(any(), any(), any(), any()))
        .thenReturn(List.of(t));
    when(projectRepository.findActiveDueBetweenForDiary(any(), any(), any(), any()))
        .thenReturn(List.of(m));
    when(courtDateRepository.findByStatusInAndScheduledDateBetween(any(), any(), any()))
        .thenReturn(List.of(withDesc, blankDesc));
    when(prescriptionTrackerRepository.findByStatusInAndPrescriptionDateBetween(
            any(), any(), any()))
        .thenReturn(List.of(rx));

    var events = service.getEvents(admin, week()).events();

    var task = byId(events, t.getId());
    assertThat(task.kind()).isEqualTo(DiaryKind.TASK_DUE);
    assertThat(task.href()).isEqualTo("/matters/" + m.getId() + "?taskId=" + t.getId());
    assertThat(task.status()).isEqualTo("OPEN");
    assertThat(task.matterName()).isEqualTo("Dlamini v RAF");
    assertThat(task.matterReference()).isEqualTo("RAF-2026-001");
    assertThat(task.clientId()).isEqualTo(client.getId());
    assertThat(task.clientName()).isEqualTo("T. Dlamini");
    assertThat(task.subtype()).isNull();
    assertThat(task.time()).isNull();

    var due = byId(events, m.getId());
    assertThat(due.kind()).isEqualTo(DiaryKind.MATTER_DUE);
    assertThat(due.href()).isEqualTo("/matters/" + m.getId());
    assertThat(due.status()).isEqualTo("ACTIVE");

    var court = byId(events, withDesc.getId());
    assertThat(court.title()).isEqualTo("Johannesburg High Court — Rule 43");
    assertThat(court.subtype()).isEqualTo("HEARING");
    assertThat(court.time()).isEqualTo(LocalTime.of(9, 30));
    assertThat(court.status()).isEqualTo("SCHEDULED");
    assertThat(court.href())
        .isEqualTo("/matters/" + m.getId() + "?tab=court-dates&courtDateId=" + withDesc.getId());
    assertThat(byId(events, blankDesc.getId()).title()).isEqualTo("Pretoria High Court");

    var presc = byId(events, rx.getId());
    assertThat(presc.title()).isEqualTo("Prescription — General (3 years)");
    assertThat(presc.subtype()).isEqualTo("GENERAL_3Y");
    assertThat(presc.status()).isEqualTo("RUNNING");
    assertThat(presc.href())
        .isEqualTo("/matters/" + m.getId() + "?tab=court-dates&prescriptionId=" + rx.getId());
  }

  // ---------------------------------------------------------------- helpers

  private DiaryQuery week() {
    return new DiaryQuery(TODAY.minusDays(2), TODAY.plusDays(4), null, null, null, true);
  }

  private static DiaryEventResponse byId(List<DiaryEventResponse> events, UUID id) {
    return events.stream()
        .filter(e -> e.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no event with id " + id + " in " + events));
  }

  private Project matter(String name, UUID clientId) {
    Project p = TestIds.withId(new Project(name, "d", CREATOR), UUID.randomUUID());
    p.setCustomerId(clientId);
    knownMatters.add(p);
    return p;
  }

  private Customer client(String name) {
    Customer c =
        TestIds.withId(
            TestCustomerFactory.createActiveCustomer(name, "c@test.com", CREATOR),
            UUID.randomUUID());
    knownClients.add(c);
    return c;
  }

  private Member person(String name) {
    Member m =
        TestIds.withId(
            new Member("clerk_" + UUID.randomUUID(), "p@test.com", name, null, null),
            UUID.randomUUID());
    knownPeople.add(m);
    return m;
  }

  private void lead(Project matter, UUID memberId) {
    knownLeads.add(new ProjectMember(matter.getId(), memberId, "lead", null));
  }

  private static Task task(Project matter, String title, LocalDate due, UUID assigneeId) {
    Task t =
        TestIds.withId(
            new Task(matter.getId(), title, null, "MEDIUM", null, due, CREATOR), UUID.randomUUID());
    if (assigneeId != null) {
      TestIds.withField(t, "assigneeId", assigneeId);
    }
    return t;
  }

  private static CourtDate courtDate(
      Project matter, LocalDate date, LocalTime time, String court, String description) {
    return TestIds.withId(
        new CourtDate(
            matter.getId(),
            matter.getCustomerId(),
            "HEARING",
            date,
            time,
            court,
            null,
            null,
            description,
            7,
            CREATOR),
        UUID.randomUUID());
  }

  private static PrescriptionTracker prescription(Project matter, LocalDate date, String type) {
    return TestIds.withId(
        new PrescriptionTracker(
            matter.getId(),
            matter.getCustomerId(),
            date.minusYears(3),
            type,
            null,
            date,
            null,
            CREATOR),
        UUID.randomUUID());
  }
}
