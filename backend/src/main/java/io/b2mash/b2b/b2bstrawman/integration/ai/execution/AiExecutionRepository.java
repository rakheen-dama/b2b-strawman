package io.b2mash.b2b.b2bstrawman.integration.ai.execution;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiExecutionRepository extends JpaRepository<AiExecution, UUID> {

  @Query(
      "SELECT COALESCE(SUM(e.costCents), 0) FROM AiExecution e "
          + "WHERE e.createdAt >= :monthStart")
  long sumCostCentsForCurrentMonth(@Param("monthStart") Instant monthStart);

  @Query("SELECT COUNT(e) FROM AiExecution e WHERE e.createdAt >= :monthStart")
  int countForCurrentMonth(@Param("monthStart") Instant monthStart);

  Page<AiExecution> findBySkillIdAndStatusOrderByCreatedAtDesc(
      String skillId, String status, Pageable pageable);

  Page<AiExecution> findBySkillIdOrderByCreatedAtDesc(String skillId, Pageable pageable);

  Page<AiExecution> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

  Page<AiExecution> findAllByOrderByCreatedAtDesc(Pageable pageable);

  List<AiExecution> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
      String entityType, UUID entityId);
}
