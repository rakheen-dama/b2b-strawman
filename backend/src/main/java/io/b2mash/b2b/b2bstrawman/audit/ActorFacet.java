package io.b2mash.b2b.b2bstrawman.audit;

import java.util.UUID;

/**
 * Facet aggregate row for the actor dropdown filter. {@code actorDisplayName} has been resolved by
 * the service via the §12.3.4 fallback chain (live member name → "Former member ({uuid})" → static
 * label for non-USER actor types).
 *
 * @param actorId the actor's UUID (always non-null; rows with no actor are excluded by the query)
 * @param actorDisplayName non-null display label suitable for UI presentation
 * @param actorType the actor type captured at log time ({@code USER}, {@code PORTAL_CONTACT},
 *     {@code SYSTEM}, {@code AUTOMATION}, {@code API_KEY})
 * @param eventCount number of events authored by this actor in the queried range
 */
public record ActorFacet(
    UUID actorId, String actorDisplayName, String actorType, long eventCount) {}
