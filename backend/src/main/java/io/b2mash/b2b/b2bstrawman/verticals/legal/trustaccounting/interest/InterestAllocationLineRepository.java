package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterestAllocationLineRepository
    extends JpaRepository<InterestAllocationLine, UUID> {

  List<InterestAllocationLine> findByInterestAllocationIdIn(Collection<UUID> allocationIds);

  /**
   * Deletes the lines of the given allocations — always before the allocations themselves (no ON
   * DELETE CASCADE by house rule).
   */
  @Modifying(flushAutomatically = true, clearAutomatically = false)
  @Query("DELETE FROM InterestAllocationLine l WHERE l.interestAllocationId IN :allocationIds")
  int deleteByInterestAllocationIdIn(@Param("allocationIds") Collection<UUID> allocationIds);
}
