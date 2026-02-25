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
   * details->>'project_id' field. Compares as text (matching findByProjectId pattern). Tenant
   * isolation is provided by the dedicated schema (search_path).
   */
  @Query(
      value =
          """
          SELECT MAX(ae.occurred_at) FROM audit_events ae
          WHERE (ae.details->>'project_id') = CAST(:projectId AS TEXT)
             OR (ae.entity_type = 'project' AND ae.entity_id = :projectId)
          """,
      nativeQuery = true)
  Optional<Instant> findMostRecentByProject(@Param("projectId") UUID projectId);

  // --- Cross-project activity queries (Epic 76B) ---

  /**
   * Cross-project activity for admin/owner: returns recent events with actor and project names
   * joined. Only returns events that have a project_id in their details. Tenant isolation is
   * provided by the dedicated schema (search_path).
   */
  @Query(
      nativeQuery = true,
      value =
          """
      SELECT ae.id AS eventId, ae.event_type AS eventType,
             ae.entity_type AS entityType,
             COALESCE(m.name, ae.details->>'actor_name', 'Unknown') AS actorName,
             (ae.details->>'project_id')::uuid AS projectId,
             p.name AS projectName,
             ae.occurred_at AS occurredAt
      FROM audit_events ae
      LEFT JOIN members m ON ae.actor_id = m.id
      LEFT JOIN projects p ON (ae.details->>'project_id')::uuid = p.id
      WHERE (ae.details->>'project_id') IS NOT NULL
        AND (ae.details->>'project_id') ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
      ORDER BY ae.occurred_at DESC
      LIMIT :limit
      """)
  List<CrossProjectActivityProjection> findCrossProjectActivity(@Param("limit") int limit);

  /**
   * Cross-project activity for regular members: filtered to projects the member belongs to via
   * project_members subquery. Tenant isolation is provided by the dedicated schema (search_path).
   */
  @Query(
      nativeQuery = true,
      value =
          """
      SELECT ae.id AS eventId, ae.event_type AS eventType,
             ae.entity_type AS entityType,
             COALESCE(m.name, ae.details->>'actor_name', 'Unknown') AS actorName,
             (ae.details->>'project_id')::uuid AS projectId,
             p.name AS projectName,
             ae.occurred_at AS occurredAt
      FROM audit_events ae
      LEFT JOIN members m ON ae.actor_id = m.id
      LEFT JOIN projects p ON (ae.details->>'project_id')::uuid = p.id
      WHERE (ae.details->>'project_id') IS NOT NULL
        AND (ae.details->>'project_id') ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
        AND (ae.details->>'project_id')::uuid IN (
            SELECT pm.project_id FROM project_members pm WHERE pm.member_id = CAST(:memberId AS UUID)
        )
      ORDER BY ae.occurred_at DESC
      LIMIT :limit
      """)
  List<CrossProjectActivityProjection> findCrossProjectActivityForMember(
      @Param("memberId") UUID memberId, @Param("limit") int limit);

  @Query("SELECT e.id FROM AuditEvent e WHERE e.occurredAt < :before")
  List<UUID> findIdsByOccurredAtBefore(@Param("before") Instant before);
}
