package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Phase 93 / 657A (task 657.3, C-12): {@code TrustInterestChargesScheduleQuery.addFundGroup} has a
 * {@code default} arm, so a registry type that can sit on a Fund ledger but has no column of its
 * own would compile and silently make its month read "does not balance". This pins every such type
 * — the existing ones and the take-on types alike — to a column. Plain JUnit.
 */
class TrustInterestChargesFundGroupTest {

  /** Every type that can sit on a Fund ledger has a column in the schedule — none "unexplained". */
  @ParameterizedTest
  @EnumSource(TrustTransactionType.class)
  void everyFundLedgerType_hasAColumn(TrustTransactionType type) {
    Assumptions.assumeTrue(
        type.creditorAccountType() == TrustCreditorAccountType.FUND_INTEREST_AND_CHARGES);
    var m = new TrustInterestChargesScheduleQuery.MonthMovements();
    TrustInterestChargesScheduleQuery.addFundGroup(m, row(type.name(), new BigDecimal("10.00")));
    assertThat(m.hasUnexplained).as(type.name()).isFalse();
  }

  @Test
  void takeOnFund_isTheMonthsBroughtForward_neverAMovement() {
    var m = new TrustInterestChargesScheduleQuery.MonthMovements();
    TrustInterestChargesScheduleQuery.addFundGroup(
        m, row("TAKEON_FUND", new BigDecimal("1250.00")));
    TrustInterestChargesScheduleQuery.addFundGroup(
        m, row("TAKEON_FUND_DEBIT", new BigDecimal("-50.00")));

    assertThat(m.takeOnBroughtForward).isEqualByComparingTo("1200.00");
    assertThat(m.fundInterest).isEqualByComparingTo("0");
    assertThat(m.sweeps).isEqualByComparingTo("0");
    assertThat(m.hasUnexplained).isFalse();
  }

  private static TrustReportQueryRepository.FundLedgerMonthRow row(String type, BigDecimal effect) {
    return new TrustReportQueryRepository.FundLedgerMonthRow() {
      @Override
      public LocalDate getPeriodMonth() {
        return LocalDate.of(2026, 7, 1);
      }

      @Override
      public String getTxType() {
        return type;
      }

      @Override
      public Boolean getRecoverable() {
        return "BANK_CHARGE".equals(type) ? Boolean.TRUE : null;
      }

      @Override
      public BigDecimal getLedgerEffect() {
        return effect;
      }

      @Override
      public BigDecimal getVat() {
        return BigDecimal.ZERO;
      }
    };
  }
}
