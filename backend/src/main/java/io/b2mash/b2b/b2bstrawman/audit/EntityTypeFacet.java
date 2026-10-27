package io.b2mash.b2b.b2bstrawman.audit;

/**
 * Facet aggregate row for the entity-type dropdown filter. The registry currently keys on {@code
 * event_type} (not {@code entity_type}), so the service derives the {@code label} from the raw
 * {@code entity_type} value: legal sentence-cased (AuditEventTypeRegistry.legalLabel).
 *
 * @param entityType the raw entity-type string as stored on the {@code audit_events} row (e.g.
 *     {@code "task"}, {@code "document"}, {@code "matter"})
 * @param label legal sentence-cased human label (AuditEventTypeRegistry.legalLabel), e.g. {@code
 *     "project"} ⇒ {@code "Matter"}
 * @param count number of events targeting this entity type in the queried range
 */
public record EntityTypeFacet(String entityType, String label, long count) {}
