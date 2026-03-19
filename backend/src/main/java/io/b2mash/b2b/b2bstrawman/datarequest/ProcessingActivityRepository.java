package io.b2mash.b2b.b2bstrawman.datarequest;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessingActivityRepository extends JpaRepository<ProcessingActivity, UUID> {
  boolean existsByCategory(String category);
}
