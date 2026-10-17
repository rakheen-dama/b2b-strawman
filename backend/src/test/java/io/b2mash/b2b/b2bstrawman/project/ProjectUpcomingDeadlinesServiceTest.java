package io.b2mash.b2b.b2bstrawman.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

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
 * <p>Asserts court dates are ordered by date ASC, tagged {@code COURT}, and that disabled-module /
 * past-date filtering work. The regulatory deadlines source was removed in Phase 86 (Epic 604A).
 */
@ExtendWith(MockitoExtension.class)
class ProjectUpcomingDeadlinesServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 4, 26);

  @Mock private ProjectRepository projectRepository;
  @Mock private ProjectAccessService projectAccessService;
  @Mock private CourtDateRepository courtDateRepository;
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
            projectRepository, projectAccessService, courtDateRepository, moduleGuard, fixedClock);
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
  void courtDatesOnly_orderedByDateAsc_taggedCourt() {
    when(moduleGuard.isModuleEnabled("court_calendar")).thenReturn(true);

    // Repository rows deliberately out of order — the service sorts by date ASC itself.
    CourtDate later =
        courtDate(LocalDate.of(2026, 7, 25), "Cape Town High Court", null, "POSTPONED");
    CourtDate earlier =
        courtDate(
            LocalDate.of(2026, 6, 10), "Johannesburg High Court", "Judge Mogoeng", "SCHEDULED");
    when(courtDateRepository.findByProjectIdOrderByScheduledDateAsc(projectId))
        .thenReturn(List.of(later, earlier));

    var rows = service.getUpcomingDeadlines(projectId, actor);

    assertThat(rows).hasSize(2);
    assertThat(rows).extracting(ProjectUpcomingDeadline::type).containsOnly("COURT");

    assertThat(rows.get(0).date()).isEqualTo(LocalDate.of(2026, 6, 10));
    assertThat(rows.get(0).description()).contains("Johannesburg High Court", "Judge Mogoeng");
    assertThat(rows.get(0).status()).isEqualTo("SCHEDULED");

    assertThat(rows.get(1).date()).isEqualTo(LocalDate.of(2026, 7, 25));
    assertThat(rows.get(1).description()).isEqualTo("Cape Town High Court");
    assertThat(rows.get(1).status()).isEqualTo("POSTPONED");
  }

  @Test
  void filtersOutPastCourtDates() {
    when(moduleGuard.isModuleEnabled("court_calendar")).thenReturn(true);

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
  void skipsDisabledModule_emptyResultWhenCourtCalendarDisabled() {
    when(moduleGuard.isModuleEnabled("court_calendar")).thenReturn(false);

    var rows = service.getUpcomingDeadlines(projectId, actor);

    assertThat(rows).isEmpty();
  }

  @Test
  void courtDateOnTodayIsIncluded() {
    when(moduleGuard.isModuleEnabled("court_calendar")).thenReturn(true);

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
