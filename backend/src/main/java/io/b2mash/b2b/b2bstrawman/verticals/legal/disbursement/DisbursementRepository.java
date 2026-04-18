package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DisbursementRepository extends JpaRepository<LegalDisbursement, UUID> {

  /** Returns all disbursements on a project whose approval status is in the given set. */
  List<LegalDisbursement> findByProjectIdAndApprovalStatusIn(
      UUID projectId, Collection<String> statuses);

  /** Counts disbursements on a project with the given billing status. */
  long countByProjectIdAndBillingStatus(UUID projectId, String billingStatus);

  /** Counts disbursements on a project whose approval status is in the given set. */
  long countByProjectIdAndApprovalStatusIn(UUID projectId, Collection<String> statuses);

  /**
   * Returns all APPROVED disbursements on a project within the given incurred-date range, ordered
   * by category then date. Used to build the Statement of Account (§67.4 / Epic 491).
   */
  @Query(
      """
      SELECT d FROM LegalDisbursement d
      WHERE d.projectId = :projectId
        AND d.incurredDate >= :from
        AND d.incurredDate <= :to
        AND d.approvalStatus = 'APPROVED'
      ORDER BY d.category ASC, d.incurredDate ASC
      """)
  List<LegalDisbursement> findForStatement(
      @Param("projectId") UUID projectId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
