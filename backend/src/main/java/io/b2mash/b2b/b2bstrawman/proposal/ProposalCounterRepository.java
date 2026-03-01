package io.b2mash.b2b.b2bstrawman.proposal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProposalCounterRepository extends JpaRepository<ProposalCounter, UUID> {

  @Query("SELECT c FROM ProposalCounter c")
  Optional<ProposalCounter> findCounter();
}
