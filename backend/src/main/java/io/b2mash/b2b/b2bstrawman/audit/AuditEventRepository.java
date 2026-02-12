package io.b2mash.b2b.b2bstrawman.audit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

  /**
   * JPQL-based findById that respects Hibernate @Filter (unlike JpaRepository.findById which uses
   * EntityManager.find and bypasses @Filter). Required for shared-schema tenant isolation.
   */
  @Query("SELECT e FROM AuditEvent e WHERE e.id = :id")
  Optional<AuditEvent> findOneById(@Param("id") UUID id);

  /**
   * Multi-parameter JPQL query with nullable filters. Each parameter uses the {@code (:param IS
   * NULL OR e.field = :param)} pattern so that null values mean "no filter on this dimension". For
   * {@code eventTypePrefix}, uses LIKE concatenation for prefix matching.
   */
  @Query(
      """
      SELECT e FROM AuditEvent e
      WHERE (:entityType IS NULL OR e.entityType = :entityType)
        AND (:entityId IS NULL OR e.entityId = :entityId)
        AND (:actorId IS NULL OR e.actorId = :actorId)
        AND (:eventTypePrefix IS NULL OR e.eventType LIKE CONCAT(:eventTypePrefix, '%'))
        AND (:from IS NULL OR e.occurredAt >= :from)
        AND (:to IS NULL OR e.occurredAt <= :to)
      ORDER BY e.occurredAt DESC
      """)
  Page<AuditEvent> findByFilter(
      @Param("entityType") String entityType,
      @Param("entityId") UUID entityId,
      @Param("actorId") UUID actorId,
      @Param("eventTypePrefix") String eventTypePrefix,
      @Param("from") Instant from,
      @Param("to") Instant to,
      Pageable pageable);

  /** Counts audit events grouped by event type, ordered by count descending. */
  @Query(
      """
      SELECT e.eventType, COUNT(e) FROM AuditEvent e
      GROUP BY e.eventType
      ORDER BY COUNT(e) DESC
      """)
  List<Object[]> countByEventType();
}
