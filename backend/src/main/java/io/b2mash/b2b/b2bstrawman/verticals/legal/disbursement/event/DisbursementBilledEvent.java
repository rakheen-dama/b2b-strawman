package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a disbursement transitions to {@code BILLED} — published from {@code
 * DisbursementService.markBilled(...)} which is invoked by 487B's invoice-creation pipeline.
 */
public record DisbursementBilledEvent(
    UUID disbursementId,
    UUID projectId,
    UUID customerId,
    UUID invoiceLineId,
    UUID actorId,
    Instant occurredAt) {

  public static DisbursementBilledEvent of(
      UUID disbursementId, UUID projectId, UUID customerId, UUID invoiceLineId, UUID actorId) {
    return new DisbursementBilledEvent(
        disbursementId, projectId, customerId, invoiceLineId, actorId, Instant.now());
  }
}
