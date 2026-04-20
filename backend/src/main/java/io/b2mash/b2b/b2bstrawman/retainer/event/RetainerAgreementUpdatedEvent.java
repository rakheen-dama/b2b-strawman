package io.b2mash.b2b.b2bstrawman.retainer.event;

import io.b2mash.b2b.b2bstrawman.retainer.RetainerAgreement;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published (Epic 496A) after a retainer agreement's terms or status change. The portal sync
 * service listens for this event to re-project the {@code portal_retainer_summary} row (name,
 * allocated hours, mapped status).
 */
public record RetainerAgreementUpdatedEvent(
    UUID agreementId,
    UUID customerId,
    String name,
    BigDecimal allocatedHours,
    String status,
    String tenantId,
    String orgId,
    Instant occurredAt) {

  public static RetainerAgreementUpdatedEvent of(
      RetainerAgreement agreement, String tenantId, String orgId) {
    return new RetainerAgreementUpdatedEvent(
        agreement.getId(),
        agreement.getCustomerId(),
        agreement.getName(),
        agreement.getAllocatedHours(),
        agreement.getStatus().name(),
        tenantId,
        orgId,
        Instant.now());
  }
}
