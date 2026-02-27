package io.b2mash.b2b.b2bstrawman.task;

import java.util.Map;
import java.util.Set;

/** Task lifecycle status with validated transitions. */
public enum TaskStatus {
  OPEN,
  IN_PROGRESS,
  DONE,
  CANCELLED;

  private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_TRANSITIONS =
      Map.of(
          OPEN, Set.of(IN_PROGRESS, CANCELLED),
          IN_PROGRESS, Set.of(DONE, OPEN, CANCELLED),
          DONE, Set.of(OPEN),
          CANCELLED, Set.of(OPEN));

  /** Returns the set of statuses this status can transition to. */
  public Set<TaskStatus> allowedTransitions() {
    return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of());
  }

  /** Returns true if transitioning from this status to the target is allowed. */
  public boolean canTransitionTo(TaskStatus target) {
    return allowedTransitions().contains(target);
  }

  /** Returns true if this is a terminal state (DONE or CANCELLED). */
  public boolean isTerminal() {
    return this == DONE || this == CANCELLED;
  }
}
