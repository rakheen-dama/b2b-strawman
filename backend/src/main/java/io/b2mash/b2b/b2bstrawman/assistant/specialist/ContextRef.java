package io.b2mash.b2b.b2bstrawman.assistant.specialist;

/**
 * Lightweight reference to the contextual entity a specialist session was launched against (e.g.
 * the customer/matter/invoice the user was viewing). Optional — generic specialists may pass {@code
 * null}.
 *
 * @param entityType e.g. {@code "Customer"}, {@code "Matter"}, {@code "Invoice"}
 * @param entityId the entity's identifier (string form to support both UUIDs and natural keys)
 * @param currentPage the route the user was on when starting the session
 */
public record ContextRef(String entityType, String entityId, String currentPage) {}
