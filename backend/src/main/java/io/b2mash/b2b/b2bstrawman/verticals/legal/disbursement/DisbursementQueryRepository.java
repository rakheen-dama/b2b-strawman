package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Query-only companion repository for the disbursement list endpoint. Kept separate from {@code
 * DisbursementRepository} (owned by slice 486A) so that 486B introduces no changes to 486A's API
 * surface.
 *
 * <p>All filter parameters are optional — pass {@code null} to disable the corresponding predicate.
 */
public interface DisbursementQueryRepository extends JpaRepository<LegalDisbursement, UUID> {

  @Query(
      """
      SELECT d FROM LegalDisbursement d
      WHERE (:projectId IS NULL OR d.projectId = :projectId)
        AND (:approvalStatus IS NULL OR d.approvalStatus = :approvalStatus)
        AND (:billingStatus IS NULL OR d.billingStatus = :billingStatus)
        AND (:category IS NULL OR d.category = :category)
      """)
  Page<LegalDisbursement> findFiltered(
      @Param("projectId") UUID projectId,
      @Param("approvalStatus") String approvalStatus,
      @Param("billingStatus") String billingStatus,
      @Param("category") String category,
      Pageable pageable);
}
