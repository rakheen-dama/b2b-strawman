package io.b2mash.b2b.b2bstrawman.retainer;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.event.TimeEntryChangedEvent;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RetainerConsumptionListener {

  private static final Logger log = LoggerFactory.getLogger(RetainerConsumptionListener.class);

  private final CustomerProjectRepository customerProjectRepository;
  private final RetainerAgreementRepository retainerAgreementRepository;
  private final RetainerPeriodRepository retainerPeriodRepository;
  private final NotificationService notificationService;
  private final CustomerRepository customerRepository;

  public RetainerConsumptionListener(
      CustomerProjectRepository customerProjectRepository,
      RetainerAgreementRepository retainerAgreementRepository,
      RetainerPeriodRepository retainerPeriodRepository,
      NotificationService notificationService,
      CustomerRepository customerRepository) {
    this.customerProjectRepository = customerProjectRepository;
    this.retainerAgreementRepository = retainerAgreementRepository;
    this.retainerPeriodRepository = retainerPeriodRepository;
    this.notificationService = notificationService;
    this.customerRepository = customerRepository;
  }

  @EventListener
  @Transactional
  public void onTimeEntryChanged(TimeEntryChangedEvent event) {
    try {
      handleTimeEntryChanged(event);
    } catch (Exception e) {
      // Consumption updates are self-healing (ADR-074) — a missed update will be
      // corrected on the next time entry change. Swallow to avoid rolling back the
      // outer time-entry transaction.
      log.warn(
          "Failed to update retainer consumption for time entry {}: {}",
          event.entityId(),
          e.getMessage(),
          e);
    }
  }

  private void handleTimeEntryChanged(TimeEntryChangedEvent event) {
    if (event.projectId() == null) {
      return;
    }

    // 1. Find customer for the project
    var customerIdOpt = customerProjectRepository.findFirstCustomerByProjectId(event.projectId());
    if (customerIdOpt.isEmpty()) {
      return;
    }
    UUID customerId = customerIdOpt.get();

    // 2. Find active or paused retainer for customer
    var agreementOpt = retainerAgreementRepository.findActiveOrPausedByCustomerId(customerId);
    if (agreementOpt.isEmpty()) {
      return;
    }
    var agreement = agreementOpt.get();

    // 3. Find the open period
    var periodOpt =
        retainerPeriodRepository.findByAgreementIdAndStatus(agreement.getId(), PeriodStatus.OPEN);
    if (periodOpt.isEmpty()) {
      return;
    }
    var period = periodOpt.get();

    // 4. Run consumption query
    long totalMinutes =
        retainerPeriodRepository.sumConsumedMinutes(
            customerId, period.getPeriodStart(), period.getPeriodEnd());

    // 5. Convert minutes to hours (scale 2, HALF_UP)
    BigDecimal newConsumedHours =
        BigDecimal.valueOf(totalMinutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

    // 6. Capture old consumed hours before update (for threshold check)
    BigDecimal oldConsumedHours = period.getConsumedHours();

    // 7. Update the period entity (dirty check will persist)
    period.updateConsumption(newConsumedHours);

    log.info(
        "Updated retainer period {} consumption: {} hours (was {} hours)",
        period.getId(),
        newConsumedHours,
        oldConsumedHours);

    // 8. Check thresholds (HOUR_BANK only)
    if (agreement.getType() == RetainerType.HOUR_BANK
        && agreement.getAllocatedHours() != null
        && agreement.getAllocatedHours().signum() > 0) {
      checkThresholds(agreement, period, oldConsumedHours, newConsumedHours, customerId);
    }
  }

  private void checkThresholds(
      RetainerAgreement agreement,
      RetainerPeriod period,
      BigDecimal oldConsumedHours,
      BigDecimal newConsumedHours,
      UUID customerId) {

    BigDecimal allocated = agreement.getAllocatedHours();
    BigDecimal hundred = BigDecimal.valueOf(100);

    BigDecimal oldPct =
        oldConsumedHours.multiply(hundred).divide(allocated, 2, RoundingMode.HALF_UP);
    BigDecimal newPct =
        newConsumedHours.multiply(hundred).divide(allocated, 2, RoundingMode.HALF_UP);

    BigDecimal threshold80 = BigDecimal.valueOf(80);
    BigDecimal threshold100 = BigDecimal.valueOf(100);

    String customerName =
        customerRepository.findById(customerId).map(Customer::getName).orElse("Unknown");

    // 80% threshold
    if (oldPct.compareTo(threshold80) < 0 && newPct.compareTo(threshold80) >= 0) {
      BigDecimal remaining = period.getRemainingHours();
      String title =
          "Retainer for %s is at %s%% capacity — %s hours remaining"
              .formatted(customerName, newPct.setScale(0, RoundingMode.HALF_UP), remaining);
      String body =
          "Agreement: %s, Allocated: %s hrs, Consumed: %s hrs, Remaining: %s hrs, Usage: %s%%"
              .formatted(
                  agreement.getName(),
                  allocated.stripTrailingZeros().toPlainString(),
                  newConsumedHours.stripTrailingZeros().toPlainString(),
                  remaining.stripTrailingZeros().toPlainString(),
                  newPct.setScale(1, RoundingMode.HALF_UP));
      notificationService.notifyAdminsAndOwners(
          "RETAINER_APPROACHING_CAPACITY", title, body, "RETAINER_AGREEMENT", agreement.getId());
      log.info(
          "Retainer approaching capacity: agreement={}, consumed={}%", agreement.getId(), newPct);
    }

    // 100% threshold
    if (oldPct.compareTo(threshold100) < 0 && newPct.compareTo(threshold100) >= 0) {
      BigDecimal remaining = period.getRemainingHours();
      String title =
          "Retainer for %s is fully consumed — further time will be billed as overage"
              .formatted(customerName);
      String body =
          "Agreement: %s, Allocated: %s hrs, Consumed: %s hrs, Remaining: %s hrs, Usage: %s%%"
              .formatted(
                  agreement.getName(),
                  allocated.stripTrailingZeros().toPlainString(),
                  newConsumedHours.stripTrailingZeros().toPlainString(),
                  remaining.stripTrailingZeros().toPlainString(),
                  newPct.setScale(1, RoundingMode.HALF_UP));
      notificationService.notifyAdminsAndOwners(
          "RETAINER_FULLY_CONSUMED", title, body, "RETAINER_AGREEMENT", agreement.getId());
      log.info("Retainer fully consumed: agreement={}", agreement.getId());
    }
  }
}
