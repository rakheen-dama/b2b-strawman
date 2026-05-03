package io.b2mash.b2b.b2bstrawman.audit;

import java.util.List;

/**
 * Single-transaction snapshot of three facet aggregations across {@code audit_events} for a given
 * date range. Per architecture §12.3.1 the snapshot powers all three facet dropdown endpoints
 * (actors / event-types / entity-types) wired up in 502B.
 *
 * @param actors top-500 actor facets ordered by {@code eventCount} DESC, with {@code
 *     actorDisplayName} already resolved
 * @param eventTypes per-eventType counts ordered DESC, with registry-derived {@code label /
 *     severity / group}
 * @param entityTypes per-entityType counts ordered DESC, with title-cased {@code label}
 */
public record FacetSnapshot(
    List<ActorFacet> actors, List<EventTypeFacet> eventTypes, List<EntityTypeFacet> entityTypes) {}
