package io.b2mash.b2b.b2bstrawman.customerbackend.model;

import java.time.LocalDate;

/**
 * The client-facing deadline status rule for {@code portal.portal_deadline_view} — the single
 * source of truth (G89-F71).
 *
 * <p>{@code UPCOMING} / {@code DUE_SOON} / {@code OVERDUE} are a function of the due date and
 * <i>today</i>, so they go stale the moment the calendar moves. They are therefore derived at
 * <b>read</b> time: {@code PortalDeadlineService} maps every response through {@link #effective},
 * and {@code PortalDeadlineViewRepository#findByCustomer} filters on the same rule in SQL, using
 * {@link #dueSoonCutoff} so the window lives only here. The value written at sync time is a
 * snapshot and is only authoritative when it is terminal ({@code COMPLETED} / {@code CANCELLED},
 * driven by firm-side state).
 */
public final class PortalDeadlineStatus {

  public static final String UPCOMING = "UPCOMING";
  public static final String DUE_SOON = "DUE_SOON";
  public static final String OVERDUE = "OVERDUE";
  public static final String COMPLETED = "COMPLETED";
  public static final String CANCELLED = "CANCELLED";

  /** A deadline due within this many days of today (inclusive) reads {@code DUE_SOON}. */
  public static final int DUE_SOON_DAYS = 7;

  private PortalDeadlineStatus() {}

  /**
   * Time-based status: past due → {@code OVERDUE}; due today up to {@link #dueSoonCutoff} inclusive
   * → {@code DUE_SOON}; otherwise {@code UPCOMING}.
   */
  public static String derive(LocalDate dueDate, LocalDate today) {
    if (dueDate.isBefore(today)) {
      return OVERDUE;
    }
    if (!dueDate.isAfter(dueSoonCutoff(today))) {
      return DUE_SOON;
    }
    return UPCOMING;
  }

  /**
   * The status a reader must show: a stored terminal status wins, anything else is re-derived from
   * the due date and {@code today}.
   */
  public static String effective(String storedStatus, LocalDate dueDate, LocalDate today) {
    return isTerminal(storedStatus) ? storedStatus : derive(dueDate, today);
  }

  public static boolean isTerminal(String status) {
    return COMPLETED.equals(status) || CANCELLED.equals(status);
  }

  /** The last due date (inclusive) that reads {@code DUE_SOON} on {@code today}. */
  public static LocalDate dueSoonCutoff(LocalDate today) {
    return today.plusDays(DUE_SOON_DAYS);
  }
}
