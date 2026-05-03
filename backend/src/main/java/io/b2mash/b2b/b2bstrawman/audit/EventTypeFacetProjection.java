package io.b2mash.b2b.b2bstrawman.audit;

/**
 * Spring Data projection for event-type-facet aggregate rows. The service enriches each row with
 * registry-derived {@code label / severity / group} via {@link AuditEventTypeRegistry#resolve}.
 */
public interface EventTypeFacetProjection {

  String getEventType();

  long getCount();
}
