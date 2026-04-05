package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterestAllocationRepository extends JpaRepository<InterestAllocation, UUID> {

  List<InterestAllocation> findByInterestRunId(UUID interestRunId);
}
