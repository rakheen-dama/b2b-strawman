package io.b2mash.b2b.b2bstrawman.crm;

import io.b2mash.b2b.b2bstrawman.dashboard.dto.PipelineSummaryResponse;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.PipelineSummaryResponse.StageBreakdown;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes the v1 pipeline summary reporting surface (Epic 578A, ADR-318) from two native aggregate
 * queries — no N+1: a per-stage OPEN breakdown and a win-rate window roll-up. The open weighted
 * value and average deal size are derived in Java from the already-fetched per-stage rows.
 *
 * <p><strong>Single-currency limitation (v1):</strong> the response {@code currency} is the org
 * default from {@link OrgSettingsService#getDefaultCurrency()} and monetary sums add {@code
 * value_amount} directly with no FX conversion. Mixed currencies are out of scope for v1 (ADR-318).
 *
 * <p>Tenant isolation is by schema-per-tenant {@code search_path}; the native queries carry no
 * {@code tenant_id} predicate by design.
 */
@Service
public class PipelineSummaryService {

  private static final int DEFAULT_WINDOW_DAYS = 90;

  private final DealRepository dealRepository;
  private final OrgSettingsService orgSettingsService;

  public PipelineSummaryService(
      DealRepository dealRepository, OrgSettingsService orgSettingsService) {
    this.dealRepository = dealRepository;
    this.orgSettingsService = orgSettingsService;
  }

  /**
   * Builds the pipeline summary for the given window and optional owner. Defaults the window to the
   * trailing 90 days (start) through today (end) when {@code from}/{@code to} are null.
   *
   * @param from inclusive start of the win-rate / days-to-close window; trailing 90 days when null
   * @param to inclusive end of the window; today when null
   * @param ownerId optional deal-owner filter; aggregates all owners when null
   */
  @Transactional(readOnly = true)
  public PipelineSummaryResponse getSummary(LocalDate from, LocalDate to, UUID ownerId) {
    LocalDate windowFrom = from != null ? from : LocalDate.now().minusDays(DEFAULT_WINDOW_DAYS);
    LocalDate windowTo = to != null ? to : LocalDate.now();

    List<StageBreakdownProjection> rows = dealRepository.stageBreakdown(ownerId);

    List<StageBreakdown> stages =
        rows.stream()
            .map(
                r ->
                    new StageBreakdown(
                        r.getStageId(),
                        r.getStageName(),
                        r.getDealCount(),
                        scale(r.getTotalValue()),
                        scale(r.getWeightedValue())))
            .toList();

    BigDecimal openWeightedValue =
        scale(
            stages.stream()
                .map(StageBreakdown::weightedValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

    long openDealCount = stages.stream().mapToLong(StageBreakdown::dealCount).sum();
    BigDecimal openTotalValue =
        stages.stream().map(StageBreakdown::totalValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal averageDealSize =
        openDealCount == 0
            ? BigDecimal.ZERO
            : openTotalValue.divide(BigDecimal.valueOf(openDealCount), 2, RoundingMode.HALF_UP);

    // Half-open window: WON/LOST deals closed on or after the exclusive next-day boundary of
    // windowTo are excluded. The response still reports windowTo as the inclusive end the caller
    // supplied.
    Instant windowStart = windowFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant windowEnd = windowTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    WinRateProjection win = dealRepository.winRate(ownerId, windowStart, windowEnd);

    long closed = win.getWonCount() + win.getLostCount();
    BigDecimal winRate =
        closed == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(win.getWonCount())
                .divide(BigDecimal.valueOf(closed), 4, RoundingMode.HALF_UP);

    Integer averageDaysToClose =
        win.getAvgDaysToClose() == null ? null : (int) Math.round(win.getAvgDaysToClose());

    return new PipelineSummaryResponse(
        openWeightedValue,
        orgSettingsService.getDefaultCurrency(),
        winRate,
        windowFrom,
        windowTo,
        averageDealSize,
        averageDaysToClose,
        stages);
  }

  /**
   * Normalises a possibly-null monetary value from PostgreSQL NUMERIC arithmetic to 2 d.p. so the
   * response always serialises at the documented scale (ADR-318 §11.4). Null totals (empty
   * pipeline) collapse to {@link BigDecimal#ZERO} scaled to 2 d.p.
   */
  private static BigDecimal scale(BigDecimal value) {
    return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
  }
}
