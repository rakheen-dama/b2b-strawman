package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TrustAccountingConstantsTest {

  @Test
  void statutoryLpffSharePercentHasCorrectValueAndScale() {
    assertThat(TrustAccountingConstants.STATUTORY_LPFF_SHARE_PERCENT)
        .isEqualTo(new BigDecimal("0.05"));
    assertThat(TrustAccountingConstants.STATUTORY_LPFF_SHARE_PERCENT.scale())
        .as("Scale should be 2 (from string constructor \"0.05\")")
        .isEqualTo(2);
  }

  @Test
  void investmentBasisEnumHasExactlyTwoValues() {
    assertThat(InvestmentBasis.values())
        .containsExactly(InvestmentBasis.FIRM_DISCRETION, InvestmentBasis.CLIENT_INSTRUCTION);
  }
}
