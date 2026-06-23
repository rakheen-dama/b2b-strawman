package io.b2mash.b2b.b2bstrawman.crm;

/**
 * Spring Data projection for the win-rate window native query (Epic 578A). Returns WON/LOST counts
 * over the window and the average days-to-close for WON deals (nullable when no WON deals fall in
 * the window — {@code AVG} over an empty set is {@code NULL}).
 */
public interface WinRateProjection {
  long getWonCount();

  long getLostCount();

  /** Mean {@code (won_at - created_at)} in days over WON deals in the window; null when none. */
  Double getAvgDaysToClose();
}
