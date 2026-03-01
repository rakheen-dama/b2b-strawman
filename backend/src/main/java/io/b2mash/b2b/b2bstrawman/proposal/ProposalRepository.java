package io.b2mash.b2b.b2bstrawman.proposal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProposalRepository extends JpaRepository<Proposal, UUID> {

  Page<Proposal> findByCustomerId(UUID customerId, Pageable pageable);

  List<Proposal> findByStatusAndExpiresAtBefore(ProposalStatus status, Instant now);

  long countByStatus(ProposalStatus status);

  @Query(
      """
      SELECT p FROM Proposal p
      WHERE (:customerId IS NULL OR p.customerId = :customerId)
        AND (:status IS NULL OR p.status = :status)
        AND (:feeModel IS NULL OR p.feeModel = :feeModel)
        AND (:createdById IS NULL OR p.createdById = :createdById)
      ORDER BY p.createdAt DESC
      """)
  Page<Proposal> findFiltered(
      @Param("customerId") UUID customerId,
      @Param("status") ProposalStatus status,
      @Param("feeModel") FeeModel feeModel,
      @Param("createdById") UUID createdById,
      Pageable pageable);
}
