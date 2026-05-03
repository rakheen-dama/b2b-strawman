package io.b2mash.b2b.b2bstrawman.audit;

import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.hibernate.jpa.HibernateHints;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
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

  /**
   * Finds a single audit event by exportId stored in the JSONB details column. Used by
   * DataExportService.getExportStatus() to look up a specific compliance export.
   */
  @Query(
      value =
          """
          SELECT * FROM audit_events
          WHERE event_type = :eventType
            AND (details->>'exportId') = :exportId
          ORDER BY occurred_at DESC
          LIMIT 1
          """,
      nativeQuery = true)
  Optional<AuditEvent> findByExportId(
      @Param("eventType") String eventType, @Param("exportId") String exportId);

  // --- Portal activity timeline queries (GAP-OBS-Portal-Activity / E4.3) ---
  // Tenant isolation is provided by Hibernate search_path. The UUID-shape regex on the JSONB
  // lookup mirrors findCrossProjectActivity (line 119) and is safety-critical: a malformed
  // details->>'project_id' value would crash the ::uuid cast.

  /**
   * Combined portal-contact + firm activity for a portal contact. Returns events the contact
   * authored AND firm-side events on any project linked to the contact's customer.
   *
   * <p>The firm-side branch is filtered by {@code eventTypes} -- only allow-listed firm events
   * surface to the portal. The portal-contact-authored branch is unfiltered: portal contacts always
   * see their own actions. See {@link
   * io.b2mash.b2b.b2bstrawman.portal.PortalActivityEventTypes#PORTAL_VISIBLE_FIRM_EVENT_TYPES}.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT * FROM audit_events ae
          WHERE (ae.actor_type = 'PORTAL_CONTACT' AND ae.actor_id = :portalContactId)
             OR (
               ae.event_type IN (:eventTypes)
               AND (ae.details->>'project_id') IS NOT NULL
               AND (ae.details->>'project_id') ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
               AND (ae.details->>'project_id')::uuid IN (
                 SELECT project_id FROM customer_projects WHERE customer_id = :customerId
               )
             )
          ORDER BY ae.occurred_at DESC
          """,
      countQuery =
          """
          SELECT count(*) FROM audit_events ae
          WHERE (ae.actor_type = 'PORTAL_CONTACT' AND ae.actor_id = :portalContactId)
             OR (
               ae.event_type IN (:eventTypes)
               AND (ae.details->>'project_id') IS NOT NULL
               AND (ae.details->>'project_id') ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
               AND (ae.details->>'project_id')::uuid IN (
                 SELECT project_id FROM customer_projects WHERE customer_id = :customerId
               )
             )
          """)
  Page<AuditEvent> findActivityForPortalContact(
      @Param("portalContactId") UUID portalContactId,
      @Param("customerId") UUID customerId,
      @Param("eventTypes") Set<String> eventTypes,
      Pageable pageable);

  /** Portal-contact-authored events only ("Your actions" tab). */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT * FROM audit_events ae
          WHERE ae.actor_type = 'PORTAL_CONTACT' AND ae.actor_id = :portalContactId
          ORDER BY ae.occurred_at DESC
          """,
      countQuery =
          """
          SELECT count(*) FROM audit_events ae
          WHERE ae.actor_type = 'PORTAL_CONTACT' AND ae.actor_id = :portalContactId
          """)
  Page<AuditEvent> findActivityMineForPortalContact(
      @Param("portalContactId") UUID portalContactId, Pageable pageable);

  /**
   * Firm-side events on any project linked to the customer ("Firm actions" tab). Excludes
   * portal-contact-authored events.
   *
   * <p>Filtered by {@code eventTypes} -- only allow-listed firm events surface to the portal so
   * client-facing tabs don't over-disclose internal firm bookkeeping (time entries, disbursements,
   * project mutations). See {@link
   * io.b2mash.b2b.b2bstrawman.portal.PortalActivityEventTypes#PORTAL_VISIBLE_FIRM_EVENT_TYPES}.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT * FROM audit_events ae
          WHERE ae.actor_type <> 'PORTAL_CONTACT'
            AND ae.event_type IN (:eventTypes)
            AND (ae.details->>'project_id') IS NOT NULL
            AND (ae.details->>'project_id') ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
            AND (ae.details->>'project_id')::uuid IN (
              SELECT project_id FROM customer_projects WHERE customer_id = :customerId
            )
          ORDER BY ae.occurred_at DESC
          """,
      countQuery =
          """
          SELECT count(*) FROM audit_events ae
          WHERE ae.actor_type <> 'PORTAL_CONTACT'
            AND ae.event_type IN (:eventTypes)
            AND (ae.details->>'project_id') IS NOT NULL
            AND (ae.details->>'project_id') ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
            AND (ae.details->>'project_id')::uuid IN (
              SELECT project_id FROM customer_projects WHERE customer_id = :customerId
            )
          """)
  Page<AuditEvent> findActivityFirmForCustomer(
      @Param("customerId") UUID customerId,
      @Param("eventTypes") Set<String> eventTypes,
      Pageable pageable);

  // --- Epic 502A — severity-filtered list + facet projections ---

  /**
   * Variant of {@link #findByFilter} that adds a registry-derived severity restriction. Caller
   * (DatabaseAuditService) computes the include/exclude eventType sets from the registry pre-flight
   * and passes them as Postgres TEXT[] arrays for {@code = ANY} / {@code LIKE ANY} filtering.
   *
   * <p>Semantics of the eventType predicate (architecture §12.3.5):
   *
   * <ul>
   *   <li>{@code exactTypes} — event_type IN (...)
   *   <li>{@code prefixPatterns} — event_type LIKE ANY (... pattern array, e.g. {@code
   *       'matter.closure.%'})
   *   <li>{@code excludeExact} — exact strings to subtract (handles the prefix-vs-exact severity
   *       conflict, e.g. {@code matter.closure.override_used} is CRITICAL even though {@code
   *       matter.closure.*} is NOTICE)
   *   <li>{@code allRegisteredExacts} / {@code allRegisteredPrefixes} — when non-null, an
   *       additional OR clause matches event types that are NOT registered in the catalogue
   *       (severity defaults to INFO via the registry's defaultFor()). Pass non-null only when INFO
   *       is in the requested severity set.
   * </ul>
   *
   * <p>All array params accept {@code null} to mean "constraint disabled". Pass {@code String[0]}
   * (empty) only when you want the predicate to match nothing — but callers typically short-circuit
   * to {@link Page#empty(Pageable)} instead of issuing a query that matches zero rows.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT * FROM audit_events e
          WHERE (CAST(:entityType AS TEXT) IS NULL OR e.entity_type = CAST(:entityType AS TEXT))
            AND (CAST(:entityId AS UUID) IS NULL OR e.entity_id = CAST(:entityId AS UUID))
            AND (CAST(:actorId AS UUID) IS NULL OR e.actor_id = CAST(:actorId AS UUID))
            AND (CAST(:eventTypePrefix AS TEXT) IS NULL OR e.event_type LIKE CONCAT(CAST(:eventTypePrefix AS TEXT), '%'))
            AND (CAST(:fromTs AS TIMESTAMPTZ) IS NULL OR e.occurred_at >= CAST(:fromTs AS TIMESTAMPTZ))
            AND (CAST(:toTs AS TIMESTAMPTZ) IS NULL OR e.occurred_at < CAST(:toTs AS TIMESTAMPTZ))
            AND (
              (CAST(:exactTypes AS TEXT[]) IS NOT NULL AND e.event_type = ANY(CAST(:exactTypes AS TEXT[])))
              OR (CAST(:prefixPatterns AS TEXT[]) IS NOT NULL AND e.event_type LIKE ANY(CAST(:prefixPatterns AS TEXT[])))
              OR (
                CAST(:allRegisteredExacts AS TEXT[]) IS NOT NULL
                AND CAST(:allRegisteredPrefixes AS TEXT[]) IS NOT NULL
                AND NOT (e.event_type = ANY(CAST(:allRegisteredExacts AS TEXT[])))
                AND NOT (e.event_type LIKE ANY(CAST(:allRegisteredPrefixes AS TEXT[])))
              )
            )
            AND (CAST(:excludeExact AS TEXT[]) IS NULL OR NOT (e.event_type = ANY(CAST(:excludeExact AS TEXT[]))))
          ORDER BY e.occurred_at DESC
          """,
      countQuery =
          """
          SELECT COUNT(*) FROM audit_events e
          WHERE (CAST(:entityType AS TEXT) IS NULL OR e.entity_type = CAST(:entityType AS TEXT))
            AND (CAST(:entityId AS UUID) IS NULL OR e.entity_id = CAST(:entityId AS UUID))
            AND (CAST(:actorId AS UUID) IS NULL OR e.actor_id = CAST(:actorId AS UUID))
            AND (CAST(:eventTypePrefix AS TEXT) IS NULL OR e.event_type LIKE CONCAT(CAST(:eventTypePrefix AS TEXT), '%'))
            AND (CAST(:fromTs AS TIMESTAMPTZ) IS NULL OR e.occurred_at >= CAST(:fromTs AS TIMESTAMPTZ))
            AND (CAST(:toTs AS TIMESTAMPTZ) IS NULL OR e.occurred_at < CAST(:toTs AS TIMESTAMPTZ))
            AND (
              (CAST(:exactTypes AS TEXT[]) IS NOT NULL AND e.event_type = ANY(CAST(:exactTypes AS TEXT[])))
              OR (CAST(:prefixPatterns AS TEXT[]) IS NOT NULL AND e.event_type LIKE ANY(CAST(:prefixPatterns AS TEXT[])))
              OR (
                CAST(:allRegisteredExacts AS TEXT[]) IS NOT NULL
                AND CAST(:allRegisteredPrefixes AS TEXT[]) IS NOT NULL
                AND NOT (e.event_type = ANY(CAST(:allRegisteredExacts AS TEXT[])))
                AND NOT (e.event_type LIKE ANY(CAST(:allRegisteredPrefixes AS TEXT[])))
              )
            )
            AND (CAST(:excludeExact AS TEXT[]) IS NULL OR NOT (e.event_type = ANY(CAST(:excludeExact AS TEXT[]))))
          """)
  Page<AuditEvent> findByFilterWithEventTypes(
      @Param("entityType") String entityType,
      @Param("entityId") UUID entityId,
      @Param("actorId") UUID actorId,
      @Param("eventTypePrefix") String eventTypePrefix,
      @Param("fromTs") Instant from,
      @Param("toTs") Instant to,
      @Param("exactTypes") String[] exactTypes,
      @Param("prefixPatterns") String[] prefixPatterns,
      @Param("excludeExact") String[] excludeExact,
      @Param("allRegisteredExacts") String[] allRegisteredExacts,
      @Param("allRegisteredPrefixes") String[] allRegisteredPrefixes,
      Pageable pageable);

  /**
   * Top-500 actor facet aggregates within {@code [from, to)}, LEFT JOINed to {@code members} so the
   * service can short-circuit the §12.3.4 actor-display fallback for live members. Rows where the
   * member is missing (deleted) come back with {@code actorName = NULL} -- the service maps these
   * to {@code "Former member ({uuid})"} per the architecture spec.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT
            ae.actor_id   AS actorId,
            m.name        AS actorName,
            ae.actor_type AS actorType,
            COUNT(*)      AS eventCount
          FROM audit_events ae
          LEFT JOIN members m ON m.id = ae.actor_id
          WHERE ae.actor_id IS NOT NULL
            AND ae.occurred_at >= :fromTs
            AND ae.occurred_at <  :toTs
          GROUP BY ae.actor_id, m.name, ae.actor_type
          ORDER BY eventCount DESC
          LIMIT 500
          """)
  List<ActorFacetProjection> projectActorFacets(
      @Param("fromTs") Instant from, @Param("toTs") Instant to);

  /**
   * Per-eventType counts within {@code [from, to)}. Service enriches with label/severity/group via
   * {@link AuditEventTypeRegistry#resolve(String)}.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT
            ae.event_type AS eventType,
            COUNT(*)      AS count
          FROM audit_events ae
          WHERE ae.occurred_at >= :fromTs
            AND ae.occurred_at <  :toTs
          GROUP BY ae.event_type
          ORDER BY count DESC
          """)
  List<EventTypeFacetProjection> projectEventTypeFacets(
      @Param("fromTs") Instant from, @Param("toTs") Instant to);

  /**
   * Per-entityType counts within {@code [from, to)}. Service title-cases the {@code entity_type}
   * for the {@code label} field.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT
            ae.entity_type AS entityType,
            COUNT(*)       AS count
          FROM audit_events ae
          WHERE ae.occurred_at >= :fromTs
            AND ae.occurred_at <  :toTs
          GROUP BY ae.entity_type
          ORDER BY count DESC
          """)
  List<EntityTypeFacetProjection> projectEntityTypeFacets(
      @Param("fromTs") Instant from, @Param("toTs") Instant to);

  // --- Epic 503A — streaming variants for CSV export ---

  /**
   * Streaming variant of {@link #findByFilter} for CSV export (Epic 503A). Ordered by {@code
   * occurredAt DESC} — same as the paginated cousin. The {@link HibernateHints#HINT_FETCH_SIZE}
   * keeps the JDBC driver pulling 100 rows at a time instead of materialising the entire result.
   *
   * <p>Caller MUST iterate inside an active transaction and close the stream (try-with-resources).
   */
  @QueryHints(@QueryHint(name = HibernateHints.HINT_FETCH_SIZE, value = "100"))
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
  Stream<AuditEvent> streamByFilter(
      @Param("entityType") String entityType,
      @Param("entityId") UUID entityId,
      @Param("actorId") UUID actorId,
      @Param("eventTypePrefix") String eventTypePrefix,
      @Param("from") Instant from,
      @Param("to") Instant to);

  /**
   * Streaming variant of {@link #findByFilterWithEventTypes} for severity-filtered CSV exports
   * (Epic 503A). Same WHERE semantics as the paginated cousin; no countQuery needed (streams do not
   * paginate).
   */
  @QueryHints(@QueryHint(name = HibernateHints.HINT_FETCH_SIZE, value = "100"))
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT * FROM audit_events e
          WHERE (CAST(:entityType AS TEXT) IS NULL OR e.entity_type = CAST(:entityType AS TEXT))
            AND (CAST(:entityId AS UUID) IS NULL OR e.entity_id = CAST(:entityId AS UUID))
            AND (CAST(:actorId AS UUID) IS NULL OR e.actor_id = CAST(:actorId AS UUID))
            AND (CAST(:eventTypePrefix AS TEXT) IS NULL OR e.event_type LIKE CONCAT(CAST(:eventTypePrefix AS TEXT), '%'))
            AND (CAST(:fromTs AS TIMESTAMPTZ) IS NULL OR e.occurred_at >= CAST(:fromTs AS TIMESTAMPTZ))
            AND (CAST(:toTs AS TIMESTAMPTZ) IS NULL OR e.occurred_at < CAST(:toTs AS TIMESTAMPTZ))
            AND (
              (CAST(:exactTypes AS TEXT[]) IS NOT NULL AND e.event_type = ANY(CAST(:exactTypes AS TEXT[])))
              OR (CAST(:prefixPatterns AS TEXT[]) IS NOT NULL AND e.event_type LIKE ANY(CAST(:prefixPatterns AS TEXT[])))
              OR (
                CAST(:allRegisteredExacts AS TEXT[]) IS NOT NULL
                AND CAST(:allRegisteredPrefixes AS TEXT[]) IS NOT NULL
                AND NOT (e.event_type = ANY(CAST(:allRegisteredExacts AS TEXT[])))
                AND NOT (e.event_type LIKE ANY(CAST(:allRegisteredPrefixes AS TEXT[])))
              )
            )
            AND (CAST(:excludeExact AS TEXT[]) IS NULL OR NOT (e.event_type = ANY(CAST(:excludeExact AS TEXT[]))))
          ORDER BY e.occurred_at DESC
          """)
  Stream<AuditEvent> streamByFilterWithEventTypes(
      @Param("entityType") String entityType,
      @Param("entityId") UUID entityId,
      @Param("actorId") UUID actorId,
      @Param("eventTypePrefix") String eventTypePrefix,
      @Param("fromTs") Instant from,
      @Param("toTs") Instant to,
      @Param("exactTypes") String[] exactTypes,
      @Param("prefixPatterns") String[] prefixPatterns,
      @Param("excludeExact") String[] excludeExact,
      @Param("allRegisteredExacts") String[] allRegisteredExacts,
      @Param("allRegisteredPrefixes") String[] allRegisteredPrefixes);

  // --- Epic 505A — DSAR customer-scoped audit-trail streaming query ---

  /**
   * Streams audit events related to a customer for DSAR export (Epic 505A). Three OR-branches per
   * architecture §12.6.2:
   *
   * <ul>
   *   <li>(a) {@code entity_type='customer'} AND {@code entity_id=customerId}
   *   <li>(b) {@code entity_type IN child types} AND {@code entity_id = ANY(childIds)}
   *   <li>(c) {@code details->>'customerId' = customerIdText} (best-effort, no dedicated index)
   * </ul>
   *
   * <p>Caller MUST iterate inside a read-only transaction and close the stream. Empty {@code
   * childIds}/{@code childTypes} arrays are valid — branch (b) will simply match nothing, but
   * branches (a) and (c) still apply.
   */
  @QueryHints(@QueryHint(name = HibernateHints.HINT_FETCH_SIZE, value = "100"))
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT * FROM audit_events e
          WHERE (e.entity_type = 'customer' AND e.entity_id = CAST(:customerId AS UUID))
             OR (
                  CAST(:childTypes AS TEXT[]) IS NOT NULL
                  AND CAST(:childIds AS UUID[]) IS NOT NULL
                  AND e.entity_type = ANY(CAST(:childTypes AS TEXT[]))
                  AND e.entity_id = ANY(CAST(:childIds AS UUID[]))
                )
             OR ((e.details->>'customerId') = CAST(:customerId AS TEXT))
          ORDER BY e.occurred_at DESC
          """)
  Stream<AuditEvent> streamForCustomer(
      @Param("customerId") UUID customerId,
      @Param("childTypes") String[] childTypes,
      @Param("childIds") UUID[] childIds);
}
