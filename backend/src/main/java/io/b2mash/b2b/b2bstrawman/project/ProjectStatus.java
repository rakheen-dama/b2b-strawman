package io.b2mash.b2b.b2bstrawman.project;

import java.util.Map;
import java.util.Set;

/** Project lifecycle status with validated transitions. */
public enum ProjectStatus {
  ACTIVE,
  COMPLETED,
  ARCHIVED;

  private static final Map<ProjectStatus, Set<ProjectStatus>> ALLOWED_TRANSITIONS =
      Map.of(
          ACTIVE, Set.of(COMPLETED, ARCHIVED),
          COMPLETED, Set.of(ARCHIVED, ACTIVE),
          ARCHIVED, Set.of(ACTIVE));

  /** Returns the set of statuses this status can transition to. */
  public Set<ProjectStatus> allowedTransitions() {
    return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of());
  }

  /** Returns true if transitioning from this status to the target is allowed. */
  public boolean canTransitionTo(ProjectStatus target) {
    return allowedTransitions().contains(target);
  }

  /** Returns true if this is a terminal state (only ARCHIVED is terminal). */
  public boolean isTerminal() {
    return this == ARCHIVED;
  }
}
