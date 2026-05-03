package io.b2mash.b2b.b2bstrawman.audit;

/**
 * Facet aggregate row for the event-type dropdown filter. {@code label}, {@code severity}, and
 * {@code group} are enriched by the service from {@link AuditEventTypeRegistry#resolve(String)}.
 *
 * @param eventType the raw event-type string as stored on the {@code audit_events} row
 * @param label registry-resolved human label (title-cased fallback when unregistered)
 * @param severity registry-resolved severity (defaults to {@link AuditSeverity#INFO})
 * @param group registry-resolved group (defaults to {@link AuditEventGroup#STANDARD})
 * @param count number of events of this type in the queried range
 */
public record EventTypeFacet(
    String eventType, String label, AuditSeverity severity, AuditEventGroup group, long count) {}
