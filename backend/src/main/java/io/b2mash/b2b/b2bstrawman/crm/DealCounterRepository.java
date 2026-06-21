package io.b2mash.b2b.b2bstrawman.crm;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Repository for the singleton {@link DealCounter} row. Mirrors {@code ProposalCounterRepository}.
 */
public interface DealCounterRepository extends JpaRepository<DealCounter, UUID> {

  @Query("SELECT c FROM DealCounter c")
  Optional<DealCounter> findCounter();
}
