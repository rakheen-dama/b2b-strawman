package io.b2mash.b2b.b2bstrawman.customer;

import io.b2mash.b2b.b2bstrawman.collections.CollectionsSql;
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
 * <p>Outstanding fee notes = the debtor-book definition, {@link CollectionsSql} (Phase 88 620B —
 * the shared predicate and amount; the invoice status lives in that constant, not a bound
 * parameter), grouped by currency; the row reports ONE currency group — the org default currency
 * when present, else the largest outstanding group (ties by currency code). Open matters = {@link
 * ProjectStatus#ACTIVE} by {@code projects.customer_id}, bound by {@link
 * #findAggregates(Collection, String)} from the enum.
 */
public interface ClientListRowRepository extends Repository<Customer, UUID> {

  /**
   * Open-matter count and outstanding fee notes (one currency group) for every client in {@code
   * ids}, in ONE statement (lateral joins). {@code currency} is the org default currency.
   */
  default List<ClientListAggregateProjection> findAggregates(
      Collection<UUID> ids, String currency) {
    return findAggregatesRaw(ids, currency, ProjectStatus.ACTIVE.name());
  }

  /** Raw statement — call {@link #findAggregates(Collection, String)}, which binds the status. */
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
          SELECT i.currency AS currency, SUM(\s"""
              + CollectionsSql.OUTSTANDING_AMOUNT
              + """
      ) AS total, COUNT(*) AS n
          FROM invoices i
          WHERE i.customer_id = c.id
            AND\s"""
              + CollectionsSql.OUTSTANDING_PREDICATE
              + """

          GROUP BY i.currency
          ORDER BY (i.currency = :currency) DESC, SUM(\s"""
              + CollectionsSql.OUTSTANDING_AMOUNT
              + """
      ) DESC, i.currency
          LIMIT 1) fn ON true
      WHERE c.id IN (:ids)
      """)
  List<ClientListAggregateProjection> findAggregatesRaw(
      @Param("ids") Collection<UUID> ids,
      @Param("currency") String currency,
      @Param("openMatterStatus") String openMatterStatus);
}
