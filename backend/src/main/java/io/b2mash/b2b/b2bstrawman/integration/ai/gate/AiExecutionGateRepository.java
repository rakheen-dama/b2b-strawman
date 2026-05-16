package io.b2mash.b2b.b2bstrawman.integration.ai.gate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiExecutionGateRepository extends JpaRepository<AiExecutionGate, UUID> {

  @Query("SELECT g FROM AiExecutionGate g WHERE g.execution.id = :executionId")
  List<AiExecutionGate> findByExecutionId(@Param("executionId") UUID executionId);

  Page<AiExecutionGate> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

  @Query("SELECT g FROM AiExecutionGate g WHERE g.status = 'PENDING' AND g.expiresAt < :now")
  List<AiExecutionGate> findPendingExpiredBefore(@Param("now") Instant now);

  Page<AiExecutionGate> findByStatusAndGateTypeOrderByCreatedAtDesc(
      String status, String gateType, Pageable pageable);

  Page<AiExecutionGate> findByGateTypeOrderByCreatedAtDesc(String gateType, Pageable pageable);
}
