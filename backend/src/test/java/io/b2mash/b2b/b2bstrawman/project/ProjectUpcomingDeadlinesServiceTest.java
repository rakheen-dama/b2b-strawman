package io.b2mash.b2b.b2bstrawman.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.deadline.DeadlineCalculationService;
import io.b2mash.b2b.b2bstrawman.deadline.DeadlineCalculationService.CalculatedDeadline;
import io.b2mash.b2b.b2bstrawman.deadline.DeadlineCalculationService.DeadlineFilters;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccess;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtDate;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtDateRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ProjectUpcomingDeadlinesService} (GAP-L-58 / E9.3).
 *
 * <p>Asserts the union of {@code court_dates} + regulatory deadlines is correctly merged, ordered
 * by date ASC, tagged with the right type, and that disabled-module / past-date filtering work.
 */
@ExtendWith(MockitoExtension.class)
class ProjectUpcomingDeadlinesServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 4, 26);

  @Mock private ProjectRepository projectRepository;
  @Mock private ProjectAccessService projectAccessService;
  @Mock private CourtDateRepository courtDateRepository;
  @Mock private DeadlineCalculationService deadlineCalculationService;
  @Mock private VerticalModuleGuard moduleGuard;

  private ProjectUpcomingDeadlinesService service;
  private UUID projectId;
  private UUID customerId;
  private Project project;
  private final ActorContext actor = new ActorContext(UUID.randomUUID(), "owner");

  @BeforeEach
  void setUp() {
    Clock fixedClock = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    service =
        new ProjectUpcomingDeadlinesService(
            projectRepository,
            projectAccessService,
            courtDateRepository,
            deadlineCalculationService,
            moduleGuard,
            fixedClock);
    projectId = UUID.randomUUID();
    customerId = UUID.randomUUID();
    project = new Project("Smith v Jones", "Test matter", UUID.randomUUID());
    project.setCustomerId(customerId);
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    lenient()
        .when(projectAccessService.requireViewAccess(eq(projectId), any()))
        .thenReturn(new ProjectAccess(true, true, true, true, "OWNER"));
  }

  @Test
  void unionsBothSources_orderedByDateAsc_taggedByType() {
    when(moduleGuard.isModuleEnabled("court_calendar")).thenReturn(true);
    when(moduleGuard.isModuleEnabled("regulatory_deadlines")).thenReturn(true);

    // 1 court date in the middle
    CourtDate cd =
        courtDate(
            LocalDate.of(2026, 6, 10), "Johannesburg High Court", "Judge Mogoeng", "SCHEDULED");
    when(courtDateRepository.findByProjectIdOrderByScheduledDateAsc(projectId))
        .thenReturn(List.of(cd));

    // 2 regulatory deadlines — one before, one after the court date.
    // findLinkedProject is already filtered server-side; we mimic both
    // linked-to-project and not-linked rows to assert the filter.
    CalculatedDeadline regBefore =
        new CalculatedDeadline(
            customerId,
            "Smith Corp",
            "sars_provisional_1",
            "SARS Provisional Tax 1",
            "tax",
            LocalDate.of(2026, 5, 1),
            "pending",
            projectId,
            null);
    CalculatedDeadline regAfter =
        new CalculatedDeadline(
            customerId,
            "Smith Corp",
            "sars_vat_return",
            "SARS VAT Return",
            "vat",
            LocalDate.of(2026, 7, 25),
            "pending",
            projectId,
            null);
    CalculatedDeadline regOtherProject =
        new CalculatedDeadline(
            customerId,
            "Smith Corp",
            "sars_paye_monthly",
            "PAYE Monthly",
            "payroll",
            LocalDate.of(2026, 5, 15),
            "pending",
            UUID.randomUUID(), // different project — must be filtered out
            null);

    when(deadlineCalculationService.calculateDeadlines(
            eq(TODAY), any(LocalDate.class), any(DeadlineFilters.class)))
        .thenReturn(List.of(regBefore, regAfter, regOtherProject));

    var rows = service.getUpcomingDeadlines(projectId, actor);

    assertThat(rows).hasSize(3);
    // Ordered ASC: 2026-05-01 (REG), 2026-06-10 (COURT), 2026-07-25 (REG)
    assertThat(rows.get(0).date()).isEqualTo(LocalDate.of(2026, 5, 1));
    assertThat(rows.get(0).type()).isEqualTo("REGULATORY");
    assertThat(rows.get(0).description()).isEqualTo("SARS Provisional Tax 1");
    assertThat(rows.get(0).status()).isEqualTo("pending");

    assertThat(rows.get(1).date()).isEqualTo(LocalDate.of(2026, 6, 10));
    assertThat(rows.get(1).type()).isEqualTo("COURT");
    assertThat(rows.get(1).description()).contains("Johannesburg High Court", "Judge Mogoeng");
    assertThat(rows.get(1).status()).isEqualTo("SCHEDULED");

    assertThat(rows.get(2).date()).isEqualTo(LocalDate.of(2026, 7, 25));
    assertThat(rows.get(2).type()).isEqualTo("REGULATORY");
    assertThat(rows.get(2).description()).isEqualTo("SARS VAT Return");
  }

  @Test
  void filtersOutPastCourtDates() {
    when(moduleGuard.isModuleEnabled("court_calendar")).thenReturn(true);
    when(moduleGuard.isModuleEnabled("regulatory_deadlines")).thenReturn(false);

    CourtDate past = courtDate(LocalDate.of(2026, 1, 10), "Pretoria High Court", null, "SCHEDULED");
    CourtDate future =
        courtDate(LocalDate.of(2026, 12, 1), "Cape Town High Court", null, "SCHEDULED");
    when(courtDateRepository.findByProjectIdOrderByScheduledDateAsc(projectId))
        .thenReturn(List.of(past, future));

    var rows = service.getUpcomingDeadlines(projectId, actor);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).date()).isEqualTo(LocalDate.of(2026, 12, 1));
    assertThat(rows.get(0).type()).isEqualTo("COURT");
  }

  @Test
  void skipsDisabledModules_emptyResultWhenBothDisabled() {
    when(moduleGuard.isModuleEnabled("court_calendar")).thenReturn(false);
    when(moduleGuard.isModuleEnabled("regulatory_deadlines")).thenReturn(false);

    var rows = service.getUpcomingDeadlines(projectId, actor);

    assertThat(rows).isEmpty();
  }

  @Test
  void courtDateOnTodayIsIncluded() {
    when(moduleGuard.isModuleEnabled("court_calendar")).thenReturn(true);
    when(moduleGuard.isModuleEnabled("regulatory_deadlines")).thenReturn(false);

    CourtDate today = courtDate(TODAY, "Today Court", "Judge Today", "SCHEDULED");
    when(courtDateRepository.findByProjectIdOrderByScheduledDateAsc(projectId))
        .thenReturn(List.of(today));

    var rows = service.getUpcomingDeadlines(projectId, actor);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).date()).isEqualTo(TODAY);
  }

  // --- Helpers ---

  private CourtDate courtDate(LocalDate date, String courtName, String judge, String status) {
    var cd =
        new CourtDate(
            projectId,
            customerId,
            "HEARING",
            date,
            LocalTime.of(10, 0),
            courtName,
            null,
            judge,
            "Test description",
            7,
            UUID.randomUUID());
    cd.setStatus(status);
    setId(cd, UUID.randomUUID());
    return cd;
  }

  private static void setId(Object entity, UUID id) {
    try {
      Field idField = entity.getClass().getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(entity, id);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
