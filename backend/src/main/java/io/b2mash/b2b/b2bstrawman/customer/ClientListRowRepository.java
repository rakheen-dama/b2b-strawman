package io.b2mash.b2b.b2bstrawman.customer;

import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.project.ProjectStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Batched reads behind {@code GET /api/clients} (Phase 87, Epic 615B, arch §12.3.4). One statement
 * per call regardless of page size. Tenant isolation = dedicated schema (search_path).
 *
 * <p>Outstanding fee notes = the debtor-book definition ({@code CollectionsReadService}): {@link
 * InvoiceStatus#SENT} with a due date, grouped by currency; the row reports ONE currency group —
 * the org default currency when present, else the largest outstanding group (ties by currency
 * code). Open matters = {@link ProjectStatus#ACTIVE} by {@code projects.customer_id}. Status values
 * are bound by {@link #findAggregates(Collection, String)} from their enums — the SQL carries no
 * status literals.
 */
public interface ClientListRowRepository extends Repository<Customer, UUID> {

  /**
   * Open-matter count and outstanding fee notes (one currency group) for every client in {@code
   * ids}, in ONE statement (lateral joins), with the status definitions bound from {@link
   * ProjectStatus} and {@link InvoiceStatus}. {@code currency} is the org default currency.
   */
  default List<ClientListAggregateProjection> findAggregates(
      Collection<UUID> ids, String currency) {
    return findAggregatesRaw(ids, currency, ProjectStatus.ACTIVE.name(), InvoiceStatus.SENT.name());
  }

  /** Raw statement — call {@link #findAggregates(Collection, String)}, which binds the statuses. */
  @Query(
      nativeQuery = true,
      value =
          """
      SELECT c.id AS clientId,
             COALESCE(om.n, 0) AS openMatterCount,
             fn.currency AS feeNoteCurrency,
             COALESCE(fn.total, 0) AS outstandingFeeNoteTotal,
             COALESCE(fn.n, 0) AS outstandingFeeNoteCount
      FROM customers c
      LEFT JOIN LATERAL (
          SELECT COUNT(*) AS n
          FROM projects p
          WHERE p.customer_id = c.id AND p.status = :openMatterStatus) om ON true
      LEFT JOIN LATERAL (
          SELECT i.currency AS currency, SUM(i.total) AS total, COUNT(*) AS n
          FROM invoices i
          WHERE i.customer_id = c.id
            AND i.status = :outstandingStatus
            AND i.due_date IS NOT NULL
          GROUP BY i.currency
          ORDER BY (i.currency = :currency) DESC, SUM(i.total) DESC, i.currency
          LIMIT 1) fn ON true
      WHERE c.id IN (:ids)
      """)
  List<ClientListAggregateProjection> findAggregatesRaw(
      @Param("ids") Collection<UUID> ids,
      @Param("currency") String currency,
      @Param("openMatterStatus") String openMatterStatus,
      @Param("outstandingStatus") String outstandingStatus);
}
