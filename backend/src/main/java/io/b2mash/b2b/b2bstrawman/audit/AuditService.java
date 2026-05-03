package io.b2mash.b2b.b2bstrawman.audit;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for recording and querying audit events. Implementations are tenant-scoped --
 * queries return only events in the current tenant's dedicated schema.
 */
public interface AuditService {

  /**
   * Records a single audit event within the current transaction. If the enclosing transaction rolls
   * back, the audit event is also rolled back (no REQUIRES_NEW).
   *
   * @param record the audit event data to persist
   */
  void log(AuditEventRecord record);

  /**
   * Queries audit events matching the given filter, scoped to the current tenant schema. All filter
   * fields are optional -- null means "no filter on this field".
   *
   * @param filter query filter with optional entity type, entity ID, actor ID, event type prefix,
   *     and time range
   * @param pageable pagination and sorting parameters
   * @return a page of matching audit events ordered by occurredAt DESC
   */
  Page<AuditEvent> findEvents(AuditEventFilter filter, Pageable pageable);

  /**
   * Counts audit events grouped by event type for the current tenant schema.
   *
   * @return list of event type counts ordered by count descending
   */
  List<AuditEventRepository.EventTypeCount> countEventsByType();

  /**
   * Resolves a batch of {@code USER}-actor display names from the {@code members} table in a single
   * query. The returned map only contains entries for {@code actorId}s that resolve to a live
   * member row; non-USER actor types and missing/former members must be handled by the caller via
   * {@link #resolveActorDisplay(UUID, String)} (or per architecture §12.3.4 fallback rules).
   *
   * @param actorIds candidate actor IDs (nulls are filtered out; duplicates collapsed)
   * @return map keyed by member id, valued by {@code Member.name}; empty map if no input or no
   *     matches
   */
  Map<UUID, String> resolveActorDisplayNames(Collection<UUID> actorIds);

  /**
   * Resolves a single audit event's actor display name per architecture §12.3.4:
   *
   * <ul>
   *   <li>{@code actorType=USER} + live member ⇒ {@code Member.name}
   *   <li>{@code actorType=USER} + missing member (or null actorId) ⇒ {@code "Former member
   *       ({actorId})"} (or {@code "System"} when actorId is null)
   *   <li>{@code actorType=PORTAL_CONTACT} ⇒ {@code "Portal Contact"}
   *   <li>{@code actorType=SYSTEM} ⇒ {@code "System"}
   *   <li>{@code actorType=AUTOMATION} ⇒ {@code "Automation"}
   *   <li>{@code actorType=API_KEY} ⇒ {@code "API Key"}
   *   <li>any other / null actor type ⇒ {@code "System"} (defensive fallback)
   * </ul>
   *
   * @param actorId actor UUID (nullable)
   * @param actorType actor type string from {@link AuditEvent#getActorType()}
   * @return non-null display label suitable for UI presentation
   */
  String resolveActorDisplay(UUID actorId, String actorType);

  /**
   * Returns a single transactional snapshot of three facet aggregations across {@code audit_events}
   * inside the date range {@code [from, to)}. Per architecture §12.3.1 the snapshot powers all
   * three controller-level facet endpoints (502B). EventType facets are enriched via {@link
   * AuditEventTypeRegistry#resolve(String)}; actor facets via the §12.3.4 fallback chain.
   *
   * @param from inclusive lower bound (UTC)
   * @param to exclusive upper bound (UTC)
   * @return non-null snapshot with three lists; each list may be empty if the range produces no
   *     data
   */
  FacetSnapshot facets(Instant from, Instant to);
}
