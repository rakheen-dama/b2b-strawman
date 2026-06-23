package io.b2mash.b2b.b2bstrawman.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Pipeline summary reporting surface (Epic 578A, ADR-318 §11.4): open weighted pipeline value, a
 * per-stage breakdown, win rate over a date window (trailing 90 days by close date by default),
 * average deal size, and average days-to-close.
 *
 * <p><strong>Single-currency limitation (v1):</strong> {@code currency} is the org default from
 * {@link io.b2mash.b2b.b2bstrawman.settings.OrgSettings}; monetary fields sum {@code value_amount}
 * directly with no FX conversion. Mixed-currency pipelines are out of scope for v1 (ADR-318).
 *
 * @param openWeightedValue Σ(value × effectiveProbability / 100) over OPEN deals only
 * @param currency org default currency (no FX)
 * @param winRate won / (won + lost) over the window; {@link BigDecimal#ZERO} when no closed deals
 * @param windowFrom inclusive start of the win-rate / days-to-close window
 * @param windowTo inclusive end of the window
 * @param averageDealSize mean {@code value_amount} across OPEN deals; {@link BigDecimal#ZERO} when
 *     no OPEN deals
 * @param averageDaysToClose mean days from creation to win over WON deals in window; null when none
 * @param stages per-stage breakdown, one row per active OPEN stage (ordered by position)
 */
public record PipelineSummaryResponse(
    BigDecimal openWeightedValue,
    String currency,
    BigDecimal winRate,
    LocalDate windowFrom,
    LocalDate windowTo,
    BigDecimal averageDealSize,
    Integer averageDaysToClose,
    List<StageBreakdown> stages) {

  /**
   * Per-stage row within a {@link PipelineSummaryResponse}.
   *
   * @param stageId the OPEN stage id
   * @param stageName the stage name
   * @param dealCount number of OPEN deals in the stage
   * @param totalValue sum of {@code value_amount} across the stage's OPEN deals
   * @param weightedValue probability-weighted value across the stage's OPEN deals
   */
  public record StageBreakdown(
      UUID stageId,
      String stageName,
      long dealCount,
      BigDecimal totalValue,
      BigDecimal weightedValue) {}
}
