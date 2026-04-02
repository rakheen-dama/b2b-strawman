package io.b2mash.b2b.b2bstrawman.provisioning;

/**
 * Plan limit constants. Previously Tier-based; now returns a flat default since the Tier model was
 * removed in Epic 419A. Full subscription-aware limits will be implemented in Epic 420.
 */
public final class PlanLimits {

  public static final int DEFAULT_MAX_MEMBERS = 10;

  private PlanLimits() {}

  /** Returns the member limit. Tier parameter is ignored (retained for source compatibility). */
  public static int maxMembers() {
    return DEFAULT_MAX_MEMBERS;
  }
}
