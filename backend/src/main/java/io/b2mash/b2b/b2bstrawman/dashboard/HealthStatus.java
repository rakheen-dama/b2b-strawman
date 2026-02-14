package io.b2mash.b2b.b2bstrawman.dashboard;

/** Health status for a project, ordered by severity for escalation comparisons. */
public enum HealthStatus {
  UNKNOWN,
  HEALTHY,
  AT_RISK,
  CRITICAL;

  /**
   * Returns a severity score for escalation comparisons. Higher values indicate worse health.
   *
   * @return severity score: UNKNOWN=0, HEALTHY=1, AT_RISK=2, CRITICAL=3
   */
  public int severity() {
    return switch (this) {
      case UNKNOWN -> 0;
      case HEALTHY -> 1;
      case AT_RISK -> 2;
      case CRITICAL -> 3;
    };
  }
}
