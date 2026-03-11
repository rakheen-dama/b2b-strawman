package io.b2mash.b2b.b2bstrawman.timeentry;

import io.b2mash.b2b.b2bstrawman.billingrate.BillingRateService;
import io.b2mash.b2b.b2bstrawman.costrate.CostRateService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Handles point-in-time rate snapshotting (billing + cost) and bulk re-snapshotting. Extracted from
 * TimeEntryService to reduce constructor bloat.
 */
@Service
class RateSnapshotService {

  private final BillingRateService billingRateService;
  private final CostRateService costRateService;

  RateSnapshotService(BillingRateService billingRateService, CostRateService costRateService) {
    this.billingRateService = billingRateService;
    this.costRateService = costRateService;
  }

  record RateValues(
      BigDecimal billingRate, String billingCurrency, BigDecimal costRate, String costCurrency) {}

  /**
   * Snapshots billing and cost rates onto the time entry. Returns a rate warning message if the
   * entry is billable but no billing rate was found, or null otherwise.
   */
  String snapshotRates(
      TimeEntry entry, UUID projectId, UUID memberId, LocalDate date, boolean billable) {
    // Snapshot billing rate (ADR-040)
    var billingRate = billingRateService.resolveRate(memberId, projectId, date);
    billingRate.ifPresent(r -> entry.snapshotBillingRate(r.hourlyRate(), r.currency()));

    // Determine rate warning for billable entries without a billing rate
    String rateWarning = null;
    if (billable && billingRate.isEmpty()) {
      rateWarning =
          "No rate card found. This time entry will generate a zero-amount invoice line item.";
    }

    // Snapshot cost rate
    var costRate = costRateService.resolveCostRate(memberId, date);
    costRate.ifPresent(r -> entry.snapshotCostRate(r.hourlyCost(), r.currency()));

    return rateWarning;
  }

  /** Re-snapshots billing and cost rates when a time entry's date changes. */
  void reSnapshotOnDateChange(TimeEntry entry, UUID projectId, LocalDate effectiveDate) {
    var billingRate = billingRateService.resolveRate(entry.getMemberId(), projectId, effectiveDate);
    if (billingRate.isPresent()) {
      entry.snapshotBillingRate(billingRate.get().hourlyRate(), billingRate.get().currency());
    } else {
      entry.snapshotBillingRate(null, null);
    }

    var costRate = costRateService.resolveCostRate(entry.getMemberId(), effectiveDate);
    if (costRate.isPresent()) {
      entry.snapshotCostRate(costRate.get().hourlyCost(), costRate.get().currency());
    } else {
      entry.snapshotCostRate(null, null);
    }
  }

  /** Resolves current billing and cost rates for a member/project/date combination. */
  RateValues resolveRates(UUID memberId, UUID projectId, LocalDate date) {
    var billingRate = billingRateService.resolveRate(memberId, projectId, date);
    var costRate = costRateService.resolveCostRate(memberId, date);
    return new RateValues(
        billingRate.map(r -> r.hourlyRate()).orElse(null),
        billingRate.map(r -> r.currency()).orElse(null),
        costRate.map(r -> r.hourlyCost()).orElse(null),
        costRate.map(r -> r.currency()).orElse(null));
  }
}
