package io.b2mash.b2b.b2bstrawman.customerbackend.repository;

import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalRetainerConsumptionEntryView;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Portal read-model repository for per-time-entry retainer consumption rows (Epic 496A). Per
 * ADR-253 this uses {@link JdbcClient} against the shared portal schema — there are NO JPA entities
 * on the portal side. All writes are idempotent upserts driven by {@code
 * RetainerPortalSyncService}.
 *
 * <p>The entry's primary-key column {@code id} mirrors the firm-side {@code time_entries.id} — one
 * portal row per firm-side time entry — so {@code ON CONFLICT (id) DO UPDATE} handles time-entry
 * edits cleanly.
 */
@Repository
public class PortalRetainerConsumptionEntryRepository {

  private final JdbcClient jdbc;

  public PortalRetainerConsumptionEntryRepository(@Qualifier("portalJdbcClient") JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Idempotent upsert for a single consumption entry. Overwrites every mutable field on conflict so
   * a firm-side time-entry edit (hours, description, member) converges on the portal side on the
   * next sync.
   */
  public void upsert(PortalRetainerConsumptionEntryView view) {
    jdbc.sql(
            """
            INSERT INTO portal.portal_retainer_consumption_entry
                (id, retainer_id, customer_id, occurred_at, hours, description,
                 member_display_name, last_synced_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (id) DO UPDATE SET
                retainer_id = EXCLUDED.retainer_id,
                customer_id = EXCLUDED.customer_id,
                occurred_at = EXCLUDED.occurred_at,
                hours = EXCLUDED.hours,
                description = EXCLUDED.description,
                member_display_name = EXCLUDED.member_display_name,
                last_synced_at = now()
            """)
        .params(
            view.id(),
            view.retainerId(),
            view.customerId(),
            view.occurredAt(),
            view.hours(),
            view.description(),
            view.memberDisplayName())
        .update();
  }

  /**
   * Returns portal consumption entries for a specific retainer, scoped by customer (to prevent
   * cross-tenant reads in the shared portal schema), ordered newest-first. Both {@code from} and
   * {@code to} are optional — null bounds are treated as open-ended using the {@code CAST(? AS
   * DATE) IS NULL} guard pattern that Postgres requires when parameter type inference is ambiguous.
   */
  public List<PortalRetainerConsumptionEntryView> findByRetainerIdAndEntryDateRange(
      UUID customerId, UUID retainerId, LocalDate from, LocalDate to) {
    return jdbc.sql(
            """
            SELECT id, retainer_id, customer_id, occurred_at, hours, description,
                   member_display_name, last_synced_at
            FROM portal.portal_retainer_consumption_entry
            WHERE customer_id = ?
              AND retainer_id = ?
              AND (CAST(? AS DATE) IS NULL OR occurred_at >= CAST(? AS DATE))
              AND (CAST(? AS DATE) IS NULL OR occurred_at <= CAST(? AS DATE))
            ORDER BY occurred_at DESC, id DESC
            """)
        .params(customerId, retainerId, from, from, to, to)
        .query(PortalRetainerConsumptionEntryView.class)
        .list();
  }

  /**
   * Deletes a single consumption row by its firm-side time-entry id, scoped to the given customer.
   * Called when a firm-side time entry is deleted so the portal reflects the removal.
   */
  public void deleteByTimeEntryId(UUID customerId, UUID timeEntryId) {
    jdbc.sql(
            """
            DELETE FROM portal.portal_retainer_consumption_entry
            WHERE customer_id = ? AND id = ?
            """)
        .params(customerId, timeEntryId)
        .update();
  }
}
