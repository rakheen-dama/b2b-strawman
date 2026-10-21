package io.b2mash.b2b.b2bstrawman.project;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Batched reads behind {@code GET /api/matters} (Phase 87, Epic 615A, arch §12.3.4). One statement
 * per call regardless of page size. Tenant isolation = dedicated schema (search_path).
 *
 * <p>Definitions match the home reads (614A) so Home and the list agree: unbilled = {@code billable
 * AND invoice_id IS NULL}; value uses the per-entry {@code billing_rate_snapshot} and counts only
 * entries in {@code :currency} (the org default) — entries without a snapshot add minutes but no
 * value; disbursements = APPROVED + UNBILLED (same filter as {@code
 * DisbursementRepository.sumApprovedUnbilledAmount}); lead = oldest {@code project_role = 'lead'}
 * row (same rule as {@code DiaryService}); open tasks = status not DONE/CANCELLED.
 */
public interface MatterListRowRepository extends Repository<Project, UUID> {

  /**
   * Client name, lead fee earner, unbilled time (minutes + org-currency value), approved unbilled
   * disbursements and open-task count for every matter in {@code ids}, in ONE statement (lateral
   * joins). Disbursements are summed only when {@code includeDisbursements} (module enabled).
   */
  @Query(
      nativeQuery = true,
      value =
          """
      SELECT p.id AS matterId,
             c.name AS clientName,
             fe.member_id AS feeEarnerId,
             fe.name AS feeEarnerName,
             COALESCE(ut.minutes, 0) AS unbilledMinutes,
             COALESCE(ut.value, 0) AS unbilledTimeValue,
             COALESCE(ud.amount, 0) AS unbilledDisbursementAmount,
             COALESCE(ot.n, 0) AS openTaskCount
      FROM projects p
      LEFT JOIN customers c ON c.id = p.customer_id
      LEFT JOIN LATERAL (
          SELECT pm.member_id, m.name
          FROM project_members pm JOIN members m ON m.id = pm.member_id
          WHERE pm.project_id = p.id AND pm.project_role = 'lead'
          ORDER BY pm.created_at ASC
          LIMIT 1) fe ON true
      LEFT JOIN LATERAL (
          SELECT SUM(te.duration_minutes) AS minutes,
                 SUM(CASE WHEN te.billing_rate_currency = :currency
                          THEN te.duration_minutes / 60.0 * te.billing_rate_snapshot
                          ELSE 0 END) AS value
          FROM time_entries te JOIN tasks t ON t.id = te.task_id
          WHERE t.project_id = p.id AND te.billable = true AND te.invoice_id IS NULL) ut ON true
      LEFT JOIN LATERAL (
          SELECT SUM(ld.amount) AS amount
          FROM legal_disbursements ld
          WHERE :includeDisbursements = true
            AND ld.project_id = p.id
            AND ld.approval_status = 'APPROVED'
            AND ld.billing_status = 'UNBILLED') ud ON true
      LEFT JOIN LATERAL (
          SELECT COUNT(*) AS n
          FROM tasks t
          WHERE t.project_id = p.id AND t.status NOT IN ('DONE', 'CANCELLED')) ot ON true
      WHERE p.id IN (:ids)
      """)
  List<MatterListAggregateProjection> findAggregates(
      @Param("ids") Collection<UUID> ids,
      @Param("currency") String currency,
      @Param("includeDisbursements") boolean includeDisbursements);

  /**
   * Matters among {@code ids} with no activity on or after {@code since} (615A.2): the matter row
   * itself, every task on it, and the work date of every time entry on its tasks must all be
   * strictly before {@code since}.
   */
  @Query(
      nativeQuery = true,
      value =
          """
      SELECT p.id FROM projects p
      WHERE p.id IN (:ids)
        AND p.updated_at < CAST(:since AS date)
        AND NOT EXISTS (SELECT 1 FROM tasks t
                        WHERE t.project_id = p.id AND t.updated_at >= CAST(:since AS date))
        AND NOT EXISTS (SELECT 1 FROM time_entries te JOIN tasks t ON t.id = te.task_id
                        WHERE t.project_id = p.id AND te.date >= CAST(:since AS date))
      """)
  List<UUID> findInactiveSince(@Param("ids") Collection<UUID> ids, @Param("since") LocalDate since);
}
