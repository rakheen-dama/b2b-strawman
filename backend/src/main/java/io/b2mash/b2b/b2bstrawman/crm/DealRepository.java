package io.b2mash.b2b.b2bstrawman.crm;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for {@link Deal}. Schema-per-tenant means {@code findById} is already tenant-isolated.
 *
 * <p>{@code findByLinkedProposalId} (correlated to {@code Proposal.dealId}) is intentionally NOT
 * defined here — it lands in 576A.
 */
public interface DealRepository extends JpaRepository<Deal, UUID> {

  /** Tenant-safe single fetch — throws {@link ResourceNotFoundException}, matching convention. */
  default Deal findOneById(UUID id) {
    return findById(id).orElseThrow(() -> new ResourceNotFoundException("Deal", id));
  }

  @Query("SELECT d FROM Deal d WHERE d.customerId = :customerId ORDER BY d.updatedAt DESC")
  List<Deal> findByCustomerId(UUID customerId);

  /** Used by {@code PipelineStageService} to block deleting a stage that still has deals. */
  boolean existsByStageId(UUID stageId);

  /**
   * Paged, nullable-guarded filtered list (Phase 80, §11.3d). Each filter is ignored when its
   * parameter is null. The {@code fromDate}/{@code toDate} window applies to {@code
   * expectedCloseDate}. Ordered by most-recently-updated first.
   */
  @Query(
      """
      SELECT d FROM Deal d
      WHERE (:stageId    IS NULL OR d.stageId = :stageId)
        AND (:ownerId    IS NULL OR d.ownerId = :ownerId)
        AND (:customerId IS NULL OR d.customerId = :customerId)
        AND (:status     IS NULL OR d.status = :status)
        AND (:source     IS NULL OR d.source = :source)
        AND (:fromDate   IS NULL OR d.expectedCloseDate >= :fromDate)
        AND (:toDate     IS NULL OR d.expectedCloseDate <= :toDate)
      ORDER BY d.updatedAt DESC
      """)
  Page<Deal> findFiltered(
      @Param("stageId") UUID stageId,
      @Param("ownerId") UUID ownerId,
      @Param("customerId") UUID customerId,
      @Param("status") DealStatus status,
      @Param("source") String source,
      @Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate,
      Pageable pageable);
}
