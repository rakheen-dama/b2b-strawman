package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.gates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTrackerRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NoOpenPrescriptionsGateTest {

  private static final UUID PROJECT_ID = UUID.randomUUID();
  private static final List<String> OPEN = List.of("RUNNING", "WARNED");

  @Mock private PrescriptionTrackerRepository repo;
  @Mock private Project project;

  private NoOpenPrescriptionsGate gate;

  @BeforeEach
  void setUp() {
    gate = new NoOpenPrescriptionsGate(repo);
    org.mockito.Mockito.lenient().when(project.getId()).thenReturn(PROJECT_ID);
  }

  @Test
  void passesWhenNoRunningOrWarnedPrescriptions() {
    when(repo.countByProjectIdAndStatusIn(PROJECT_ID, OPEN)).thenReturn(0L);

    var result = gate.evaluate(project);

    assertThat(result.passed()).isTrue();
    assertThat(result.code()).isEqualTo("NO_OPEN_PRESCRIPTIONS");
  }

  @Test
  void failsAndInterpolatesCountWhenPrescriptionsRunning() {
    when(repo.countByProjectIdAndStatusIn(PROJECT_ID, OPEN)).thenReturn(4L);

    var result = gate.evaluate(project);

    assertThat(result.passed()).isFalse();
    assertThat(result.message()).contains("4 prescription timers still running");
    assertThat(result.detail()).containsEntry("count", 4L);
  }

  @Test
  void orderIsSix() {
    assertThat(gate.order()).isEqualTo(6);
  }
}
