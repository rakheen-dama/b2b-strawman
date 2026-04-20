package io.b2mash.b2b.b2bstrawman.customerbackend.repository;

import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalRetainerSummaryView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Portal read-model repository for retainer usage summaries (Epic 496A). Per ADR-253 this uses
 * {@link JdbcClient} against the shared portal schema — there are NO JPA entities on the portal
 * side. All writes are idempotent upserts driven by {@code RetainerPortalSyncService}.
 *
 * <p>The summary's primary-key column {@code id} mirrors the firm-side {@code
 * retainer_agreements.id} — one portal summary row per retainer agreement, enabling {@code ON
 * CONFLICT (id) DO UPDATE} semantics.
 */
@Repository
public class PortalRetainerSummaryRepository {

  private final JdbcClient jdbc;

  public PortalRetainerSummaryRepository(@Qualifier("portalJdbcClient") JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Idempotent upsert for a retainer usage summary. Overwrites every mutable field on conflict so
   * repeated calls converge on the firm-side state.
   */
  public void upsert(PortalRetainerSummaryView view) {
    jdbc.sql(
            """
            INSERT INTO portal.portal_retainer_summary
                (id, customer_id, name, period_type, hours_allotted, hours_consumed,
                 hours_remaining, period_start, period_end, rollover_hours,
                 next_renewal_date, status, last_synced_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (id) DO UPDATE SET
                customer_id = EXCLUDED.customer_id,
                name = EXCLUDED.name,
                period_type = EXCLUDED.period_type,
                hours_allotted = EXCLUDED.hours_allotted,
                hours_consumed = EXCLUDED.hours_consumed,
                hours_remaining = EXCLUDED.hours_remaining,
                period_start = EXCLUDED.period_start,
                period_end = EXCLUDED.period_end,
                rollover_hours = EXCLUDED.rollover_hours,
                next_renewal_date = EXCLUDED.next_renewal_date,
                status = EXCLUDED.status,
                last_synced_at = now()
            """)
        .params(
            view.id(),
            view.customerId(),
            view.name(),
            view.periodType(),
            view.hoursAllotted(),
            view.hoursConsumed(),
            view.hoursRemaining(),
            view.periodStart(),
            view.periodEnd(),
            view.rolloverHours(),
            view.nextRenewalDate(),
            view.status())
        .update();
  }

  /**
   * Returns every retainer summary visible to the given customer, ordered most-recently-synced
   * first. Used by the portal list endpoint.
   */
  public List<PortalRetainerSummaryView> findByCustomerId(UUID customerId) {
    return jdbc.sql(
            """
            SELECT id, customer_id, name, period_type, hours_allotted, hours_consumed,
                   hours_remaining, period_start, period_end, rollover_hours,
                   next_renewal_date, status, last_synced_at
            FROM portal.portal_retainer_summary
            WHERE customer_id = ?
            ORDER BY last_synced_at DESC
            """)
        .params(customerId)
        .query(PortalRetainerSummaryView.class)
        .list();
  }

  /**
   * Returns a specific retainer summary scoped to the given customer. Scoping prevents portal
   * contacts from reading another tenant's retainer data in the shared portal schema.
   */
  public Optional<PortalRetainerSummaryView> findByCustomerIdAndRetainerId(
      UUID customerId, UUID retainerId) {
    return jdbc.sql(
            """
            SELECT id, customer_id, name, period_type, hours_allotted, hours_consumed,
                   hours_remaining, period_start, period_end, rollover_hours,
                   next_renewal_date, status, last_synced_at
            FROM portal.portal_retainer_summary
            WHERE customer_id = ? AND id = ?
            """)
        .params(customerId, retainerId)
        .query(PortalRetainerSummaryView.class)
        .optional();
  }

  /**
   * Atomically adjusts the summary's consumption counters by {@code deltaHours}. Positive values
   * model new consumption (increment consumed, decrement remaining); negative values model a
   * reversal (e.g. a deleted time entry). Remaining is clamped to zero so a reversal of more hours
   * than consumed never drives the counter negative.
   */
  public void decrementHoursConsumed(UUID customerId, UUID retainerId, BigDecimal deltaHours) {
    jdbc.sql(
            """
            UPDATE portal.portal_retainer_summary
            SET hours_consumed  = hours_consumed + ?,
                hours_remaining = GREATEST(COALESCE(hours_remaining, 0) - ?, 0),
                last_synced_at  = now()
            WHERE customer_id = ? AND id = ?
            """)
        .params(deltaHours, deltaHours, customerId, retainerId)
        .update();
  }

  /**
   * Applies a period rollover to an existing summary row: rolls the period bounds forward, stamps
   * the new allotment and carry-over, and zeroes consumption counters so the new period starts
   * fresh.
   */
  public void updatePeriodRollover(
      UUID customerId,
      UUID retainerId,
      LocalDate newStart,
      LocalDate newEnd,
      BigDecimal rolloverHours,
      BigDecimal newAllotted) {
    BigDecimal safeRollover = rolloverHours != null ? rolloverHours : BigDecimal.ZERO;
    BigDecimal safeAllotted = newAllotted != null ? newAllotted : BigDecimal.ZERO;
    // hours_remaining starts at (newAllotted + rolloverHours) — the fresh period's opening balance.
    BigDecimal openingRemaining = safeAllotted.add(safeRollover);
    jdbc.sql(
            """
            UPDATE portal.portal_retainer_summary
            SET period_start     = ?,
                period_end       = ?,
                hours_allotted   = ?,
                hours_consumed   = 0,
                hours_remaining  = ?,
                rollover_hours   = ?,
                next_renewal_date = ?,
                last_synced_at   = now()
            WHERE customer_id = ? AND id = ?
            """)
        .params(
            newStart,
            newEnd,
            newAllotted,
            openingRemaining,
            safeRollover,
            newEnd,
            customerId,
            retainerId)
        .update();
  }
}
