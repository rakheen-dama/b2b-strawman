package io.b2mash.b2b.b2bstrawman.dashboard.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link KpiResponse#withFinancialsRedacted()} with non-null financial values in
 * both the current and previous period (the service currently always computes a null margin, so the
 * integration tests cannot prove margin redaction on their own).
 */
class KpiResponseRedactionTest {

  private static final List<TrendPoint> TREND = List.of(new TrendPoint("2026-10", 12.5));

  private final KpiResponse full =
      new KpiResponse(4, 120.0, 75.0, 2, 31.5, TREND, new KpiValues(3, 90.0, 60.0, 1, 22.0));

  @Test
  void redactsCurrentAndPreviousPeriodFinancials() {
    var redacted = full.withFinancialsRedacted();

    assertThat(redacted.billablePercent()).isNull();
    assertThat(redacted.averageMarginPercent()).isNull();
    assertThat(redacted.previousPeriod().billablePercent()).isNull();
    assertThat(redacted.previousPeriod().averageMarginPercent()).isNull();
  }

  @Test
  void keepsNonFinancialFieldsInBothPeriods() {
    var redacted = full.withFinancialsRedacted();

    assertThat(redacted.activeMatterCount()).isEqualTo(4);
    assertThat(redacted.totalHoursLogged()).isEqualTo(120.0);
    assertThat(redacted.overdueTaskCount()).isEqualTo(2);
    assertThat(redacted.trend()).isEqualTo(TREND);
    assertThat(redacted.previousPeriod()).isEqualTo(new KpiValues(3, 90.0, null, 1, null));
  }

  @Test
  void leavesTheOriginalUntouched() {
    full.withFinancialsRedacted();

    assertThat(full.billablePercent()).isEqualTo(75.0);
    assertThat(full.averageMarginPercent()).isEqualTo(31.5);
    assertThat(full.previousPeriod()).isEqualTo(new KpiValues(3, 90.0, 60.0, 1, 22.0));
  }

  @Test
  void toleratesMissingPreviousPeriod() {
    var noPrevious = new KpiResponse(4, 120.0, 75.0, 2, 31.5, TREND, null);

    assertThat(noPrevious.withFinancialsRedacted().previousPeriod()).isNull();
  }
}
