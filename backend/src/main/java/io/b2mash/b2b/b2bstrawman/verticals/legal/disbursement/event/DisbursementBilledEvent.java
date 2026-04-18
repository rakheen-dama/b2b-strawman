package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.event;

import java.time.Instant;
import java.util.UUID;

/** Published when an approved legal disbursement is marked billed on a specific invoice line. */
public record DisbursementBilledEvent(
    UUID disbursementId,
    UUID projectId,
    UUID customerId,
    UUID actorId,
    UUID invoiceLineId,
    Instant occurredAt) {

  public static DisbursementBilledEvent of(
      UUID disbursementId, UUID projectId, UUID customerId, UUID actorId, UUID invoiceLineId) {
    return new DisbursementBilledEvent(
        disbursementId, projectId, customerId, actorId, invoiceLineId, Instant.now());
  }
}
