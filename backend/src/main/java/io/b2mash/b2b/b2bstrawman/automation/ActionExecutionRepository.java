package io.b2mash.b2b.b2bstrawman.automation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActionExecutionRepository extends JpaRepository<ActionExecution, UUID> {
  List<ActionExecution> findByExecutionId(UUID executionId);

  List<ActionExecution> findByExecutionIdIn(List<UUID> executionIds);

  List<ActionExecution> findByActionIdInAndStatus(
      List<UUID> actionIds, ActionExecutionStatus status);

  /**
   * Finds due scheduled action executions with row-level locking. Uses {@code FOR UPDATE SKIP
   * LOCKED} to prevent multiple scheduler instances from picking up the same action execution.
   */
  @Query(
      value =
          "SELECT * FROM action_executions WHERE status = :status AND scheduled_for < :before"
              + " FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  List<ActionExecution> findDueScheduledForUpdate(
      @Param("status") String status, @Param("before") Instant before);
}
