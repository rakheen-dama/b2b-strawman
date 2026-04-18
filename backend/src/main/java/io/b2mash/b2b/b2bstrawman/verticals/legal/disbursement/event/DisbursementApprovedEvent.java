package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.event;

import java.time.Instant;
import java.util.UUID;

/** Published when a pending legal disbursement transitions to APPROVED. */
public record DisbursementApprovedEvent(
    UUID disbursementId, UUID projectId, UUID customerId, UUID actorId, Instant occurredAt) {

  public static DisbursementApprovedEvent of(
      UUID disbursementId, UUID projectId, UUID customerId, UUID actorId) {
    return new DisbursementApprovedEvent(
        disbursementId, projectId, customerId, actorId, Instant.now());
  }
}
