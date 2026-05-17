package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

/**
 * Decision carrier for the trust boundary guard. Used to communicate whether a sync entry is
 * allowed to proceed or should be blocked.
 */
public record TrustBoundaryDecision(boolean allowed, String reason) {

  public static TrustBoundaryDecision permit() {
    return new TrustBoundaryDecision(true, null);
  }

  public static TrustBoundaryDecision refused(String reason) {
    return new TrustBoundaryDecision(false, reason);
  }
}
