package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for {@link LegalDisbursement}.
 *
 * <p>Exposes project-scoped queries (486A) plus the customer-scoped {@code
 * findUnbilledBillableByCustomerId} query used by the invoice-picker read model (487A).
 */
public interface DisbursementRepository extends JpaRepository<LegalDisbursement, UUID> {

  /**
   * Finds disbursements on the given project whose approval status is in the given set. Used by the
   * pending-approval queue and by the billable-disbursement query.
   */
  List<LegalDisbursement> findByProjectIdAndApprovalStatusIn(
      UUID projectId, Collection<String> statuses);

  /**
   * Returns APPROVED + UNBILLED disbursements for the given customer, optionally narrowed to a
   * single project. Ordered by {@code incurredDate} ASC then {@code createdAt} ASC for
   * deterministic invoice-picker display. Used by the {@code /api/legal/disbursements/unbilled}
   * endpoint and by {@code UnbilledTimeService} when the {@code disbursements} module is enabled
   * for the current tenant.
   *
   * <p>Filters on the denormalised {@code customer_id} column on {@link LegalDisbursement} — no
   * join against {@code customer_projects} needed (cf. {@code
   * ExpenseRepository.findUnbilledBillableByCustomerId}, which must join because {@code Expense}
   * has no direct customer FK).
   */
  @Query(
      """
      SELECT d FROM LegalDisbursement d
      WHERE d.customerId = :customerId
        AND d.approvalStatus = 'APPROVED'
        AND d.billingStatus = 'UNBILLED'
        AND (:projectId IS NULL OR d.projectId = :projectId)
      ORDER BY d.incurredDate ASC, d.createdAt ASC
      """)
  List<LegalDisbursement> findUnbilledBillableByCustomerId(
      @Param("customerId") UUID customerId, @Param("projectId") UUID projectId);

  /**
   * Finds disbursements on the given project filtered by both approval status and billing status.
   * Used by the unbilled-per-project read-side projection — pushes the filter to the database
   * rather than filtering in-memory.
   */
  List<LegalDisbursement> findByProjectIdAndApprovalStatusAndBillingStatus(
      UUID projectId, String approvalStatus, String billingStatus);

  /**
   * Counts disbursements on a project that have the given billing status (e.g. {@code "UNBILLED"},
   * {@code "BILLED"}, {@code "WRITTEN_OFF"}).
   */
  long countByProjectIdAndBillingStatus(UUID projectId, String billingStatus);

  /**
   * Counts disbursements on a project whose approval status is in the given set. Used by matter
   * closure gates.
   */
  long countByProjectIdAndApprovalStatusIn(UUID projectId, Collection<String> statuses);

  /**
   * Counts disbursements on a project matching both approval and billing status (e.g. APPROVED +
   * UNBILLED → the "approved-but-unbilled" backlog blocking closure). Used by matter closure gate
   * {@code ALL_DISBURSEMENTS_SETTLED}.
   */
  long countByProjectIdAndApprovalStatusAndBillingStatus(
      UUID projectId, String approvalStatus, String billingStatus);

  /**
   * Returns all disbursements on a project with {@code incurredDate} within the given inclusive
   * range, ordered by category then incurred date. Used for Statement of Account generation.
   */
  @Query(
      """
      SELECT d FROM LegalDisbursement d
      WHERE d.projectId = :projectId
        AND d.incurredDate BETWEEN :from AND :to
      ORDER BY d.category, d.incurredDate
      """)
  List<LegalDisbursement> findForStatement(
      @Param("projectId") UUID projectId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
