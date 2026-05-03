package io.b2mash.b2b.b2bstrawman.audit;

/**
 * Facet aggregate row for the entity-type dropdown filter. The registry currently keys on {@code
 * event_type} (not {@code entity_type}), so the service title-cases the raw {@code entity_type}
 * value for the {@code label}.
 *
 * @param entityType the raw entity-type string as stored on the {@code audit_events} row (e.g.
 *     {@code "task"}, {@code "document"}, {@code "matter"})
 * @param label title-cased human label
 * @param count number of events targeting this entity type in the queried range
 */
public record EntityTypeFacet(String entityType, String label, long count) {}
