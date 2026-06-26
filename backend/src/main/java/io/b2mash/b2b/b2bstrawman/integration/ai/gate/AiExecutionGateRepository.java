package io.b2mash.b2b.b2bstrawman.integration.ai.gate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

  /**
   * Finds the most recent open (PENDING) gate of the given type whose {@code proposed_action} JSONB
   * carries the supplied {@code correspondence_id}. Backs the Epic 585 v1 open-gate dedupe guard:
   * one PENDING gate per {@code (correspondenceId, gateType)}. There is no FK from a gate to a
   * correspondence — the link lives only inside the {@code proposed_action} JSONB — so this is a
   * native query using PostgreSQL's {@code ->>} operator. The payload stores {@code
   * correspondence_id} as a string ({@code correspondenceId.toString()}), so the comparison is
   * against the string form. Tenant isolation is automatic via the schema {@code search_path}.
   */
  @Query(
      value =
          "SELECT g.id FROM ai_execution_gates g "
              + "WHERE g.status = 'PENDING' "
              + "AND g.gate_type = :gateType "
              + "AND g.proposed_action ->> 'correspondence_id' = :correspondenceId "
              + "ORDER BY g.created_at DESC LIMIT 1",
      nativeQuery = true)
  Optional<UUID> findPendingGateIdForCorrespondence(
      @Param("correspondenceId") String correspondenceId, @Param("gateType") String gateType);
}
