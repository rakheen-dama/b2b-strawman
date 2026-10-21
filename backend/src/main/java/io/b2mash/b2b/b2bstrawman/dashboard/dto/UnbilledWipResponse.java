package io.b2mash.b2b.b2bstrawman.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Unbilled WIP (billable time not yet on an invoice) by fee earner — {@code GET
 * /api/dashboard/unbilled-wip} (Phase 87, Epic 614A, arch §12.2.6). Value fields are null when
 * {@code redacted} is true (caller lacks FINANCIAL_VISIBILITY and is not admin/owner).
 */
public record UnbilledWipResponse(
    LocalDate asOf,
    String currency,
    int totalMinutes,
    BigDecimal totalValue,
    BigDecimal unbilledDisbursementTotal,
    List<FeeEarnerWip> byFeeEarner,
    boolean redacted) {

  /** One fee earner's unbilled time. {@code value} is null when the response is redacted. */
  public record FeeEarnerWip(
      UUID memberId, String memberName, int minutes, BigDecimal value, LocalDate oldestEntryDate) {}

  /**
   * Returns the redacted view for a caller without financial visibility: the caller's own row only
   * (minutes kept, value null), {@code totalMinutes} = the caller's minutes (never the firm total),
   * all value fields null, {@code redacted = true}. Mirrors {@link
   * KpiResponse#withFinancialsRedacted()}.
   */
  public UnbilledWipResponse withFinancialsRedacted(UUID callerMemberId) {
    var own =
        byFeeEarner.stream()
            .filter(r -> r.memberId().equals(callerMemberId))
            .map(
                r ->
                    new FeeEarnerWip(
                        r.memberId(), r.memberName(), r.minutes(), null, r.oldestEntryDate()))
            .toList();
    int ownMinutes = own.stream().mapToInt(FeeEarnerWip::minutes).sum();
    return new UnbilledWipResponse(asOf, currency, ownMinutes, null, null, own, true);
  }
}
