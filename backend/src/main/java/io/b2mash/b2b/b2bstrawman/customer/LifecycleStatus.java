package io.b2mash.b2b.b2bstrawman.customer;

import java.util.Map;
import java.util.Set;

/** Customer lifecycle status with validated transitions. */
public enum LifecycleStatus {
  PROSPECT,
  ONBOARDING,
  ACTIVE,
  DORMANT,
  OFFBOARDING,
  OFFBOARDED;

  private static final Map<LifecycleStatus, Set<LifecycleStatus>> ALLOWED_TRANSITIONS =
      Map.of(
          PROSPECT, Set.of(ONBOARDING),
          ONBOARDING, Set.of(ACTIVE, OFFBOARDING),
          ACTIVE, Set.of(DORMANT, OFFBOARDING),
          DORMANT, Set.of(ACTIVE, OFFBOARDING),
          OFFBOARDING, Set.of(OFFBOARDED),
          OFFBOARDED, Set.of(ACTIVE));

  /** Returns true if transitioning from this status to the target is allowed. */
  public boolean canTransitionTo(LifecycleStatus target) {
    return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
  }

  /**
   * Parses a string to a LifecycleStatus.
   *
   * @throws IllegalArgumentException if the value is not a valid lifecycle status
   */
  public static LifecycleStatus from(String value) {
    try {
      return valueOf(value);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new IllegalArgumentException(
          "Invalid lifecycle status: '"
              + value
              + "'. Valid values: PROSPECT, ONBOARDING, ACTIVE, DORMANT, OFFBOARDING, OFFBOARDED");
    }
  }
}
