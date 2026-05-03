package io.b2mash.b2b.b2bstrawman.audit;

import java.util.UUID;

/**
 * Spring Data projection for actor-facet aggregate rows. {@code actorName} is null when no member
 * row joined (deleted member); the service applies the {@code "Former member ({uuid})"} fallback.
 */
public interface ActorFacetProjection {

  UUID getActorId();

  /** Member display name; null when the member row is missing or has been soft-deleted. */
  String getActorName();

  String getActorType();

  long getEventCount();
}
