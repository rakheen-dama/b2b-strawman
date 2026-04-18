package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.gates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtDateRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NoOpenCourtDatesGateTest {

  private static final UUID PROJECT_ID = UUID.randomUUID();
  private static final List<String> OPEN = List.of("SCHEDULED", "POSTPONED");

  @Mock private CourtDateRepository repo;
  @Mock private Project project;

  private NoOpenCourtDatesGate gate;

  @BeforeEach
  void setUp() {
    gate = new NoOpenCourtDatesGate(repo, Clock.systemDefaultZone());
    org.mockito.Mockito.lenient().when(project.getId()).thenReturn(PROJECT_ID);
  }

  @Test
  void passesWhenNoFutureCourtDates() {
    when(repo.countByProjectIdAndStatusInAndScheduledDateGreaterThanEqual(
            eq(PROJECT_ID), eq(OPEN), any(LocalDate.class)))
        .thenReturn(0L);

    var result = gate.evaluate(project);

    assertThat(result.passed()).isTrue();
    assertThat(result.code()).isEqualTo("NO_OPEN_COURT_DATES");
  }

  @Test
  void failsAndInterpolatesCountWhenFutureCourtDatesExist() {
    when(repo.countByProjectIdAndStatusInAndScheduledDateGreaterThanEqual(
            eq(PROJECT_ID), eq(OPEN), any(LocalDate.class)))
        .thenReturn(2L);

    var result = gate.evaluate(project);

    assertThat(result.passed()).isFalse();
    assertThat(result.message()).contains("2 future court dates scheduled");
    assertThat(result.detail()).containsEntry("count", 2L);
  }

  @Test
  void orderIsFive() {
    assertThat(gate.order()).isEqualTo(5);
  }
}
