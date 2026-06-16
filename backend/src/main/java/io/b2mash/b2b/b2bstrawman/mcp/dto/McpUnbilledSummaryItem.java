package io.b2mash.b2b.b2bstrawman.mcp.dto;

import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.CustomerUnbilledSummary;
import io.b2mash.b2b.b2bstrawman.setupstatus.UnbilledTimeSummary;
import java.util.UUID;

/**
 * Unbilled-work projection for {@code get_unbilled_time} (Epic 564A). The firm-wide path returns a
 * page of per-client rows ({@link #from(CustomerUnbilledSummary, String)}); the per-matter path
 * returns a single {@link McpUnbilledMatterSummary} object instead. Money is carried as minor units
 * (long) + currency — the firm-side rows carry no currency of their own, so it is supplied by the
 * tool from its {@code currency} argument / the matter summary's own currency.
 *
 * @param customerId the client this unbilled work belongs to
 * @param customerName the client name
 * @param amountMinor total unbilled amount in minor units
 * @param currency 3-letter currency code
 */
public record McpUnbilledSummaryItem(
    UUID customerId, String customerName, long amountMinor, String currency) {

  /** Projects a firm-wide per-client unbilled row, attaching the request currency. */
  public static McpUnbilledSummaryItem from(CustomerUnbilledSummary row, String currency) {
    return new McpUnbilledSummaryItem(
        row.customerId(),
        row.customerName(),
        row.totalUnbilledAmount() == null
            ? 0L
            : row.totalUnbilledAmount().movePointRight(2).longValueExact(),
        currency);
  }

  /**
   * Per-matter unbilled summary returned when {@code projectId} is supplied. Carries the matter's
   * own currency.
   *
   * @param projectId the matter (project) id
   * @param totalAmountMinor total unbilled amount in minor units
   * @param currency 3-letter currency code (from the matter summary)
   * @param entryCount number of unbilled time entries
   */
  public record McpUnbilledMatterSummary(
      UUID projectId, long totalAmountMinor, String currency, int entryCount) {

    /** Projects the per-matter {@link UnbilledTimeSummary}. */
    public static McpUnbilledMatterSummary from(UUID projectId, UnbilledTimeSummary summary) {
      return new McpUnbilledMatterSummary(
          projectId,
          summary.totalAmount() == null
              ? 0L
              : summary.totalAmount().movePointRight(2).longValueExact(),
          summary.currency(),
          summary.entryCount());
    }
  }
}
