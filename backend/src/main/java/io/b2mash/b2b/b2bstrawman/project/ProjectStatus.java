package io.b2mash.b2b.b2bstrawman.project;

import java.util.Map;
import java.util.Set;

/** Project lifecycle status with validated transitions. */
public enum ProjectStatus {
  ACTIVE,
  COMPLETED,
  ARCHIVED,
  /**
   * Compliance-gated closed state for legal matters (Phase 67, ADR-248). Distinct from COMPLETED
   * (work-done) and ARCHIVED (read-only housekeeping): CLOSED is a legal-vertical compliance event
   * that anchors retention. Non-legal tenants never reach CLOSED because the only call sites are
   * module-guarded. CLOSED is NOT terminal — it permits {@code reopen} back to ACTIVE.
   */
  CLOSED;

  private static final Map<ProjectStatus, Set<ProjectStatus>> ALLOWED_TRANSITIONS =
      Map.of(
          ACTIVE, Set.of(COMPLETED, ARCHIVED, CLOSED),
          COMPLETED, Set.of(ARCHIVED, ACTIVE, CLOSED),
          ARCHIVED, Set.of(ACTIVE),
          CLOSED, Set.of(ACTIVE));

  /** Returns the set of statuses this status can transition to. */
  public Set<ProjectStatus> allowedTransitions() {
    return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of());
  }

  /** Returns true if transitioning from this status to the target is allowed. */
  public boolean canTransitionTo(ProjectStatus target) {
    return allowedTransitions().contains(target);
  }

  /**
   * Returns true if this is a terminal state (only ARCHIVED is terminal). CLOSED deliberately
   * returns false — the reopen path must stay open.
   */
  public boolean isTerminal() {
    return this == ARCHIVED;
  }
}
