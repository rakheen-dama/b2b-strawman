package io.b2mash.b2b.b2bstrawman.customerbackend.repository;

import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalDeadlineView;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Portal read-model repository for the unified deadline view (Epic 497A, ADR-256). Per ADR-253 this
 * uses {@link JdbcClient} against the shared portal schema — there are NO JPA entities on the
 * portal side. All writes are idempotent upserts driven by {@code DeadlinePortalSyncService}.
 *
 * <p>The table is polymorphic: the composite primary key {@code (source_entity, id)} reflects that
 * firm-side ids are unique per source (FilingSchedule, CourtDate, PrescriptionTracker,
 * FieldDefinition) but not across sources. Upsert uses {@code ON CONFLICT (source_entity, id)}.
 */
@Repository
public class PortalDeadlineViewRepository {

  private final JdbcClient jdbc;

  public PortalDeadlineViewRepository(@Qualifier("portalJdbcClient") JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Idempotent upsert for a deadline row. Overwrites every mutable field on conflict so repeated
   * syncs converge on the firm-side state. The composite {@code (source_entity, id)} PK is the
   * conflict target.
   */
  public void upsert(PortalDeadlineView view) {
    jdbc.sql(
            """
            INSERT INTO portal.portal_deadline_view
                (id, source_entity, customer_id, matter_id, deadline_type, label,
                 due_date, status, description_sanitised, last_synced_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (source_entity, id) DO UPDATE SET
                customer_id = EXCLUDED.customer_id,
                matter_id = EXCLUDED.matter_id,
                deadline_type = EXCLUDED.deadline_type,
                label = EXCLUDED.label,
                due_date = EXCLUDED.due_date,
                status = EXCLUDED.status,
                description_sanitised = EXCLUDED.description_sanitised,
                last_synced_at = now()
            """)
        .params(
            view.id(),
            view.sourceEntity(),
            view.customerId(),
            view.matterId(),
            view.deadlineType(),
            view.label(),
            view.dueDate(),
            view.status(),
            view.descriptionSanitised())
        .update();
  }

  /**
   * Returns deadline rows for the given customer filtered by optional {@code from}/{@code to}
   * bounds (inclusive) and an optional {@code statusFilter}. Null filters are treated as open-ended
   * — the {@code CAST(? AS ...) IS NULL} guard is required because Postgres parameter type
   * inference is ambiguous for untyped NULLs inside comparison predicates. Ordered by {@code
   * due_date ASC} with a stable tiebreak on {@code source_entity, id}.
   */
  public List<PortalDeadlineView> findByCustomer(
      UUID customerId, LocalDate from, LocalDate to, String statusFilter) {
    return jdbc.sql(
            """
            SELECT id, source_entity, customer_id, matter_id, deadline_type, label,
                   due_date, status, description_sanitised, last_synced_at
            FROM portal.portal_deadline_view
            WHERE customer_id = ?
              AND (CAST(? AS DATE) IS NULL OR due_date >= CAST(? AS DATE))
              AND (CAST(? AS DATE) IS NULL OR due_date <= CAST(? AS DATE))
              AND (CAST(? AS VARCHAR) IS NULL OR status = CAST(? AS VARCHAR))
            ORDER BY due_date ASC, source_entity ASC, id ASC
            """)
        .params(customerId, from, from, to, to, statusFilter, statusFilter)
        .query(PortalDeadlineView.class)
        .list();
  }

  /**
   * Returns a specific deadline row scoped to the given customer. Scoping prevents portal contacts
   * from reading another tenant's rows in the shared portal schema even when they guess a valid
   * {@code (sourceEntity, id)} pair.
   */
  public Optional<PortalDeadlineView> findByCustomerIdAndSourceEntityAndId(
      UUID customerId, String sourceEntity, UUID id) {
    return jdbc.sql(
            """
            SELECT id, source_entity, customer_id, matter_id, deadline_type, label,
                   due_date, status, description_sanitised, last_synced_at
            FROM portal.portal_deadline_view
            WHERE customer_id = ? AND source_entity = ? AND id = ?
            """)
        .params(customerId, sourceEntity, id)
        .query(PortalDeadlineView.class)
        .optional();
  }

  /**
   * Deletes a single deadline row identified by its source entity + id. Called by the sync service
   * for cancellation paths (e.g. a filing schedule deleted, a court date cancelled and firm decides
   * not to retain the portal row). Callers that must preserve cancelled rows for audit can instead
   * upsert with {@code status=CANCELLED}.
   */
  public void deleteBySourceEntityAndId(String sourceEntity, UUID id) {
    jdbc.sql(
            """
            DELETE FROM portal.portal_deadline_view
            WHERE source_entity = ? AND id = ?
            """)
        .params(sourceEntity, id)
        .update();
  }
}
