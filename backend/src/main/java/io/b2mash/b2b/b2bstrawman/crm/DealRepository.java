package io.b2mash.b2b.b2bstrawman.crm;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Repository for {@link Deal}. Schema-per-tenant means {@code findById} is already tenant-isolated.
 *
 * <p>{@code findByLinkedProposalId} (correlated to {@code Proposal.dealId}) and {@code
 * findFiltered} (paged) are intentionally NOT defined here — they land in later epics (576A / 574A
 * respectively).
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
}
