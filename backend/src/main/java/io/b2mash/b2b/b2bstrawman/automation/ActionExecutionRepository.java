package io.b2mash.b2b.b2bstrawman.automation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActionExecutionRepository extends JpaRepository<ActionExecution, UUID> {
  List<ActionExecution> findByExecutionId(UUID executionId);

  List<ActionExecution> findByStatusAndScheduledForBefore(
      ActionExecutionStatus status, Instant before);
}
