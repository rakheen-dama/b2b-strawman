package io.b2mash.b2b.b2bstrawman.crm;

import java.util.Set;

/** Lifecycle status of a {@link Deal}. OPEN deals are in progress; WON/LOST are terminal. */
public enum DealStatus {
  OPEN,
  WON,
  LOST;

  /** The terminal statuses — a deal in one of these is closed. */
  public static final Set<DealStatus> TERMINAL_STATUSES = Set.of(WON, LOST);
}
