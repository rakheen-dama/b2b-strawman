package io.b2mash.b2b.b2bstrawman.provisioning;

/** Tier-based plan limit constants. Consumed by plan enforcement services. */
public final class PlanLimits {

  public static final int STARTER_MAX_MEMBERS = 2;
  public static final int PRO_MAX_MEMBERS = 10;

  private PlanLimits() {}

  public static int maxMembers(Tier tier) {
    return switch (tier) {
      case STARTER -> STARTER_MAX_MEMBERS;
      case PRO -> PRO_MAX_MEMBERS;
    };
  }
}
