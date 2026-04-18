package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a disbursement transitions to {@code APPROVED}. Consumed by 488's
 * frontend notifications and by downstream audit/analytics listeners.
 */
public record DisbursementApprovedEvent(
    UUID disbursementId, UUID projectId, UUID customerId, UUID actorId, Instant occurredAt) {

  public static DisbursementApprovedEvent of(
      UUID disbursementId, UUID projectId, UUID customerId, UUID actorId) {
    return new DisbursementApprovedEvent(
        disbursementId, projectId, customerId, actorId, Instant.now());
  }
}
