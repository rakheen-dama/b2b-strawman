package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.gates;

import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.task.TaskStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.ClosureGate;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.GateResult;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Gate 7 — all tasks on the matter must be in a terminal status (DONE or CANCELLED) (Phase 67
 * §67.3.4 gate 7).
 *
 * <p>Implementation note: the architecture enumerates {@code IN_PROGRESS, BLOCKED} as the "still
 * open" set. The actual {@link TaskStatus} enum uses {@code OPEN, IN_PROGRESS} (no BLOCKED), so we
 * treat those two as the open-task set. Terminal statuses are DONE + CANCELLED, matching {@link
 * TaskStatus#isTerminal()}.
 */
@Component
public class AllTasksResolvedGate implements ClosureGate {

  static final String CODE = "ALL_TASKS_RESOLVED";
  private static final List<TaskStatus> OPEN_STATUSES =
      List.of(TaskStatus.OPEN, TaskStatus.IN_PROGRESS);

  private final TaskRepository taskRepository;

  public AllTasksResolvedGate(TaskRepository taskRepository) {
    this.taskRepository = taskRepository;
  }

  @Override
  public String code() {
    return CODE;
  }

  @Override
  public int order() {
    return 7;
  }

  @Override
  public GateResult evaluate(Project project) {
    // The repository exposes countByProjectIdAndStatusNotIn — invert to count "open" as
    // "not in terminal". TERMINAL = DONE, CANCELLED. So we count !(terminal) which === open.
    long count =
        taskRepository.countByProjectIdAndStatusNotIn(
            project.getId(), List.of(TaskStatus.DONE, TaskStatus.CANCELLED));
    if (count == 0) {
      return new GateResult(true, CODE, "All tasks resolved.", Map.of());
    }
    return new GateResult(
        false, CODE, "%d tasks remain open.".formatted(count), Map.of("count", count));
  }
}
