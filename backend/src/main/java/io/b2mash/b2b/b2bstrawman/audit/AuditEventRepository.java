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

  /** Typed projection for event-type count aggregation. */
  interface EventTypeCount {
    String getEventType();

    long getCount();
  }

  /**
   * JPQL-based findById that respects Hibernate @Filter (unlike JpaRepository.findById which uses
   * EntityManager.find and bypasses @Filter). Required for shared-schema tenant isolation.
   */
  @Query("SELECT e FROM AuditEvent e WHERE e.id = :id")
  Optional<AuditEvent> findOneById(@Param("id") UUID id);

  /**
   * Multi-parameter JPQL query with nullable filters. Each parameter uses the nullable pattern:
   * {@code (:param IS NULL OR e.field = :param)}. For eventTypePrefix, uses LIKE prefix match.
   * Results ordered by occurredAt DESC.
   */
  @Query(
      """
      SELECT e FROM AuditEvent e
      WHERE (:entityType IS NULL OR e.entityType = :entityType)
        AND (:entityId IS NULL OR e.entityId = :entityId)
        AND (:actorId IS NULL OR e.actorId = :actorId)
        AND (CAST(:eventTypePrefix AS string) IS NULL OR e.eventType LIKE CONCAT(CAST(:eventTypePrefix AS string), '%'))
        AND (CAST(:from AS timestamp) IS NULL OR e.occurredAt >= :from)
        AND (CAST(:to AS timestamp) IS NULL OR e.occurredAt < :to)
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

  /**
   * Finds audit events for a specific project using the JSONB details->>'project_id' field. Uses
   * the expression index idx_audit_project for efficient lookups.
   */
  @Query(
      value =
          """
          SELECT * FROM audit_events
          WHERE (details->>'project_id') = CAST(:projectId AS TEXT)
            AND (:entityType IS NULL OR entity_type = :entityType)
            AND (CAST(:since AS TIMESTAMPTZ) IS NULL OR occurred_at >= CAST(:since AS TIMESTAMPTZ))
          ORDER BY occurred_at DESC
          """,
      countQuery =
          """
          SELECT count(*) FROM audit_events
          WHERE (details->>'project_id') = CAST(:projectId AS TEXT)
            AND (:entityType IS NULL OR entity_type = :entityType)
            AND (CAST(:since AS TIMESTAMPTZ) IS NULL OR occurred_at >= CAST(:since AS TIMESTAMPTZ))
          """,
      nativeQuery = true)
  Page<AuditEvent> findByProjectId(
      @Param("projectId") String projectId,
      @Param("entityType") String entityType,
      @Param("since") Instant since,
      Pageable pageable);

  /**
   * Counts audit events grouped by event type, ordered by count descending. Uses typed projection
   * instead of Object[] for type safety.
   */
  @Query(
      "SELECT e.eventType AS eventType, COUNT(e) AS count FROM AuditEvent e"
          + " GROUP BY e.eventType ORDER BY COUNT(e) DESC")
  List<EventTypeCount> countByEventType();

  /**
   * Finds the most recent audit event timestamp for a project. Uses native SQL to query the JSONB
   * details->>'project_id' field. Compares as text (matching findByProjectId pattern). RLS handles
   * tenant isolation for native queries.
   */
  @Query(
      value =
          "SELECT MAX(ae.occurred_at) FROM audit_events ae WHERE (ae.details->>'project_id') = CAST(:projectId AS TEXT)",
      nativeQuery = true)
  Optional<Instant> findMostRecentByProject(@Param("projectId") UUID projectId);
}
