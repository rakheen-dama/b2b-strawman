package io.b2mash.b2b.b2bstrawman.retainer.event;

import io.b2mash.b2b.b2bstrawman.retainer.RetainerAgreement;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerPeriod;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published (Epic 496A) when a retainer period closes and the subsequent period opens (rollover).
 * The portal sync service listens for this event to refresh the {@code portal_retainer_summary} row
 * with the new period bounds and rollover hours.
 */
public record RetainerPeriodRolloverEvent(
    UUID agreementId,
    UUID customerId,
    UUID closedPeriodId,
    UUID newPeriodId,
    LocalDate newPeriodStart,
    LocalDate newPeriodEnd,
    BigDecimal rolloverHoursOut,
    BigDecimal allocatedHours,
    LocalDate nextRenewalDate,
    String tenantId,
    String orgId,
    Instant occurredAt) {

  public static RetainerPeriodRolloverEvent of(
      RetainerAgreement agreement,
      RetainerPeriod closedPeriod,
      RetainerPeriod newPeriod,
      BigDecimal rolloverHoursOut,
      String tenantId,
      String orgId) {
    return new RetainerPeriodRolloverEvent(
        agreement.getId(),
        agreement.getCustomerId(),
        closedPeriod.getId(),
        newPeriod.getId(),
        newPeriod.getPeriodStart(),
        newPeriod.getPeriodEnd(),
        rolloverHoursOut,
        newPeriod.getAllocatedHours(),
        newPeriod.getPeriodEnd(),
        tenantId,
        orgId,
        Instant.now());
  }
}
