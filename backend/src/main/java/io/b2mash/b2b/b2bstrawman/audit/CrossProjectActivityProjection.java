package io.b2mash.b2b.b2bstrawman.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * Projection for cross-project activity native SQL query. Returns enriched activity items with
 * actor and project names joined in the query.
 */
public interface CrossProjectActivityProjection {

  UUID getEventId();

  String getEventType();

  String getEntityType();

  String getActorName();

  UUID getProjectId();

  String getProjectName();

  Instant getOccurredAt();
}
