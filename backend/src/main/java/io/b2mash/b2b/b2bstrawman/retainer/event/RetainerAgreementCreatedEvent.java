package io.b2mash.b2b.b2bstrawman.retainer.event;

import io.b2mash.b2b.b2bstrawman.retainer.RetainerAgreement;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerPeriod;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published (Epic 496A) after a new retainer agreement + first period are persisted. The portal
 * sync service listens for this event to seed a {@code portal_retainer_summary} row for the
 * customer's portal contacts.
 */
public record RetainerAgreementCreatedEvent(
    UUID agreementId,
    UUID customerId,
    UUID firstPeriodId,
    LocalDate periodStart,
    LocalDate periodEnd,
    BigDecimal allocatedHours,
    String name,
    String frequency,
    String tenantId,
    String orgId,
    Instant occurredAt) {

  public static RetainerAgreementCreatedEvent of(
      RetainerAgreement agreement, RetainerPeriod firstPeriod, String tenantId, String orgId) {
    return new RetainerAgreementCreatedEvent(
        agreement.getId(),
        agreement.getCustomerId(),
        firstPeriod.getId(),
        firstPeriod.getPeriodStart(),
        firstPeriod.getPeriodEnd(),
        agreement.getAllocatedHours(),
        agreement.getName(),
        agreement.getFrequency().name(),
        tenantId,
        orgId,
        Instant.now());
  }
}
