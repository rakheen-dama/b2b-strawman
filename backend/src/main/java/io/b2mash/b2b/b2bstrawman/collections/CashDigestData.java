package io.b2mash.b2b.b2bstrawman.collections;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Deterministically-assembled inputs for the weekly cash digest (Phase 83, ADR-328, §3.5). Every
 * figure here is query-derived — the email template prints these authoritative numbers regardless
 * of what the AI narration says, so a hallucinated figure in prose can never change what the tables
 * show.
 *
 * @param outstandingTotal total outstanding across all SENT invoices with a due date
 * @param buckets the four-way aging split (current / d30 / d60 / d90plus), summed tenant-wide
 * @param billed value issued-and-sent in the trailing 7 days ({@code issue_date} in window, status
 *     SENT or PAID)
 * @param collected value collected in the trailing 7 days ({@code paid_at} in window)
 * @param staleWipEntryCount unbilled billable {@code TimeEntry} rows older than 30 days
 * @param staleWipHours total unbilled hours older than 30 days
 * @param activityCountsByStatus reminder-activity counts by status over the trailing 7 days
 * @param topRisks the top debtors by outstanding, each carrying its deterministic triage signals
 */
public record CashDigestData(
    BigDecimal outstandingTotal,
    Buckets buckets,
    BigDecimal billed,
    BigDecimal collected,
    long staleWipEntryCount,
    double staleWipHours,
    Map<String, Long> activityCountsByStatus,
    List<TopRisk> topRisks) {

  /** The four-way aging split, summed across the tenant's outstanding book (matches §4.1). */
  public record Buckets(BigDecimal current, BigDecimal d30, BigDecimal d60, BigDecimal d90plus) {}

  /**
   * One top-debtor entry: name, outstanding amount + currency, and the deterministic triage signals
   * (592A) plus advisor-contributed detail strings. Signals render in the digest even when AI is
   * disabled (ADR-328 B1) because they are arithmetic, not narration.
   */
  public record TopRisk(
      String customerName,
      BigDecimal outstanding,
      String currency,
      List<String> signals,
      Map<String, String> signalDetails) {}
}
