package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.gates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.task.TaskStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AllTasksResolvedGateTest {

  private static final UUID PROJECT_ID = UUID.randomUUID();
  private static final List<TaskStatus> TERMINAL = List.of(TaskStatus.DONE, TaskStatus.CANCELLED);

  @Mock private TaskRepository repo;
  @Mock private Project project;

  private AllTasksResolvedGate gate;

  @BeforeEach
  void setUp() {
    gate = new AllTasksResolvedGate(repo);
    org.mockito.Mockito.lenient().when(project.getId()).thenReturn(PROJECT_ID);
  }

  @Test
  void passesWhenNoOpenTasks() {
    when(repo.countByProjectIdAndStatusNotIn(PROJECT_ID, TERMINAL)).thenReturn(0L);

    var result = gate.evaluate(project);

    assertThat(result.passed()).isTrue();
    assertThat(result.code()).isEqualTo("ALL_TASKS_RESOLVED");
  }

  @Test
  void failsAndInterpolatesCountWhenTasksOpen() {
    when(repo.countByProjectIdAndStatusNotIn(PROJECT_ID, TERMINAL)).thenReturn(7L);

    var result = gate.evaluate(project);

    assertThat(result.passed()).isFalse();
    assertThat(result.message()).contains("7 tasks remain open");
    assertThat(result.detail()).containsEntry("count", 7L);
  }

  @Test
  void orderIsSeven() {
    assertThat(gate.order()).isEqualTo(7);
  }
}
