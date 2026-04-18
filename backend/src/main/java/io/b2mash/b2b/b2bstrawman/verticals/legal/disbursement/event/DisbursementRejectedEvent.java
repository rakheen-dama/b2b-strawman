package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.event;

import java.time.Instant;
import java.util.UUID;

/** Domain event emitted when a disbursement transitions to {@code REJECTED}. */
public record DisbursementRejectedEvent(
    UUID disbursementId, UUID projectId, UUID customerId, UUID actorId, Instant occurredAt) {

  public static DisbursementRejectedEvent of(
      UUID disbursementId, UUID projectId, UUID customerId, UUID actorId) {
    return new DisbursementRejectedEvent(
        disbursementId, projectId, customerId, actorId, Instant.now());
  }
}
