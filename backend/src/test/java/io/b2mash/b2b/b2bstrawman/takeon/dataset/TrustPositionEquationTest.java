package io.b2mash.b2b.b2bstrawman.takeon.dataset;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionEquation.Inputs;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionEquation.Sums;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionEquation.TrustPositionMessage;
import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Phase 93 657B (task 657.15, §17.3.9): the trust position's arithmetic at cutover, as a plain unit
 * test on the architecture §17.3.13 figures.
 */
class TrustPositionEquationTest {

  /** §17.3.13: B 418 500, OD 5 000, OP 20 000, CB 403 500, F 1 250, invested 300 000. */
  private static Inputs inputs(String bank, String cashbook, String deposits, String payments) {
    return new Inputs(
        new BigDecimal(bank),
        new BigDecimal(deposits),
        new BigDecimal(payments),
        new BigDecimal(cashbook),
        new BigDecimal("1250.00"),
        new BigDecimal("300000.00"));
  }

  private static Inputs section17313() {
    return inputs("418500.00", "403500.00", "5000.00", "20000.00");
  }

  /** Clients 499 500, unidentified 2 750, s86(3) 100 000, s86(4) 200 000, listed 5 000 / 20 000. */
  private static Sums sums(String listedDeposits, String listedPayments) {
    return new Sums(
        new BigDecimal("499500.00"),
        new BigDecimal("2750.00"),
        new BigDecimal("100000.00"),
        new BigDecimal("200000.00"),
        BigDecimal.ZERO,
        new BigDecimal("300000.00"),
        new BigDecimal(listedDeposits),
        new BigDecimal(listedPayments));
  }

  @Test
  void theSection17313PositionHoldsEverywhere_andMoneyHeldEqualsTheCreditors() {
    var result = TrustPositionEquation.compute(section17313(), sums("5000.00", "20000.00"));
    var eq = result.equation();

    assertThat(eq.eq1())
        .isEqualTo(
            new TrustPositionEquation.TrustEq1(
                "418500.00", "5000.00", "20000.00", "403500.00", "403500.00", "0.00", true));
    assertThat(eq.itemsTie())
        .isEqualTo(
            new TrustPositionEquation.TrustItemsTie(
                "5000.00", "20000.00", "5000.00", "20000.00", "0.00", true));
    assertThat(eq.eq2())
        .isEqualTo(
            new TrustPositionEquation.TrustEq2(
                "499500.00",
                "1250.00",
                "2750.00",
                "-100000.00",
                "403500.00",
                "403500.00",
                "0.00",
                true));
    assertThat(eq.investments())
        .isEqualTo(
            new TrustPositionEquation.TrustInvestmentsTie("300000.00", "300000.00", "0.00", true));
    assertThat(eq.deficit())
        .isEqualTo(new TrustPositionEquation.TrustDeficit("703500.00", "703500.00", "0.00", false));
    assertThat(result.blocks()).isEmpty();
    assertThat(result.fundInDebit()).isFalse();
  }

  /**
   * §17.3.9 "Check": when EQ-1, EQ-2 and the investments tie hold and the total invested is the
   * s86(4) principal + the s86(3) placed + the Fund amounts, the trust money held equals the firm's
   * trust creditors exactly — so the deficit test never fires on a position that agrees.
   */
  @Test
  void theIdentity_moneyHeldEqualsCreditorsWheneverTheEqualitiesHold() {
    var random = new Random(657L);
    for (int i = 0; i < 500; i++) {
      BigDecimal clients = cents(random, 5_000_000);
      BigDecimal fund = cents(random, 50_000).subtract(cents(random, 25_000)); // may be negative
      BigDecimal unidentified = cents(random, 100_000);
      BigDecimal s863 = cents(random, 1_000_000);
      BigDecimal s864 = cents(random, 1_000_000);
      BigDecimal fundAmounts = cents(random, 10_000);
      BigDecimal od = cents(random, 200_000);
      BigDecimal op = cents(random, 200_000);
      BigDecimal cashbook = clients.add(fund).add(unidentified).subtract(s863); // EQ-2
      BigDecimal bank = cashbook.subtract(od).add(op); // EQ-1
      BigDecimal invested = s864.add(s863).add(fundAmounts);

      var result =
          TrustPositionEquation.compute(
              new Inputs(bank, od, op, cashbook, fund, invested),
              new Sums(clients, unidentified, s863, s864, fundAmounts, invested, od, op));

      var deficit = result.equation().deficit();
      assertThat(deficit.moneyHeld()).as("case %d", i).isEqualTo(deficit.trustCreditors());
      assertThat(deficit.difference()).as("case %d", i).isEqualTo("0.00");
      assertThat(result.equation().deficit().inDeficit()).as("case %d", i).isFalse();
      assertThat(result.blocks()).as("case %d", i).isEmpty();
    }
  }

  @Test
  void aCashbookOffByTenRand_failsEq1AndEq2WithTheDifference() {
    var result =
        TrustPositionEquation.compute(
            inputs("418500.00", "403510.00", "5000.00", "20000.00"), sums("5000.00", "20000.00"));

    assertThat(result.equation().eq1().difference()).isEqualTo("-10.00");
    assertThat(result.equation().eq1().holds()).isFalse();
    assertThat(result.equation().eq2().difference()).isEqualTo("-10.00");
    assertThat(result.equation().deficit().inDeficit()).isFalse();
    assertThat(result.blocks())
        .extracting(TrustPositionMessage::code)
        .containsExactly("CONTROL_TOTAL_EQ1", "CONTROL_TOTAL_EQ2");
    assertThat(result.blocks().getFirst().message())
        .isEqualTo(
            "Bank balance R418 500,00 + outstanding deposits R5 000,00 −"
                + " outstanding payments R20 000,00 is R403 500,00, not the cashbook"
                + " balance R403 510,00.");
    assertThat(result.blocks().get(1).message())
        .isEqualTo(
            "The trust creditors (R403 500,00) do not equal the cashbook balance at cutover"
                + " (R403 510,00): difference R10,00. Every creditor group must be included"
                + " and must agree to the cent.");
  }

  @Test
  void bothSidesOfTheItemsTieOff_areTwoBlocks_depositsThenPayments() {
    var result = TrustPositionEquation.compute(section17313(), sums("4000.00", "21000.00"));

    assertThat(result.equation().itemsTie().holds()).isFalse();
    assertThat(result.equation().itemsTie().difference()).isEqualTo("2000.00");
    assertThat(result.blocks())
        .extracting(TrustPositionMessage::message)
        .containsExactly(
            "The outstanding deposits listed total R4 000,00; the reconciled position says"
                + " R5 000,00.",
            "The outstanding payments listed total R21 000,00; the reconciled position says"
                + " R20 000,00.");
  }

  @Test
  void aDeficitIsTheFirstBlock() {
    // EQ-1 holds (408 500 + 5 000 − 20 000 = 393 500) but the cashbook is R10 000 short of the
    // creditors: the trust money held is R10 000 below the trust creditors.
    var result =
        TrustPositionEquation.compute(
            inputs("408500.00", "393500.00", "5000.00", "20000.00"), sums("5000.00", "20000.00"));

    assertThat(result.equation().deficit())
        .isEqualTo(
            new TrustPositionEquation.TrustDeficit("703500.00", "693500.00", "10000.00", true));
    assertThat(result.blocks())
        .extracting(TrustPositionMessage::code)
        .containsExactly("TRUST_DEFICIT", "CONTROL_TOTAL_EQ2");
    assertThat(result.blocks().getFirst().message())
        .isEqualTo(
            "The trust creditors (R703 500,00) exceed the trust money held (R693 500,00)"
                + " by R10 000,00 at the cutover date. This is a trust deficit (Rules 54.14.8"
                + " and 54.14.10). Take-on cannot continue until it is resolved in the previous"
                + " system.");
  }

  @Test
  void aFundLedgerInDebitIsAFlag_andAFailedInvestmentsTieIsABlock() {
    var fundInDebit =
        new Inputs(
            new BigDecimal("416000.00"),
            new BigDecimal("5000.00"),
            new BigDecimal("20000.00"),
            new BigDecimal("401000.00"),
            new BigDecimal("-1250.00"),
            new BigDecimal("300000.00"));
    var result = TrustPositionEquation.compute(fundInDebit, sums("5000.00", "20000.00"));
    assertThat(result.fundInDebit()).isTrue();
    assertThat(result.blocks()).isEmpty();

    var investmentsOff =
        new Inputs(
            new BigDecimal("418500.00"),
            new BigDecimal("5000.00"),
            new BigDecimal("20000.00"),
            new BigDecimal("403500.00"),
            new BigDecimal("1250.00"),
            new BigDecimal("300500.00"));
    var off = TrustPositionEquation.compute(investmentsOff, sums("5000.00", "20000.00"));
    assertThat(off.equation().investments().difference()).isEqualTo("-500.00");
    assertThat(off.blocks())
        .extracting(TrustPositionMessage::code)
        .containsExactly("CONTROL_TOTAL_INVESTMENTS");
  }

  @Test
  void randsAreTheHouseFormat_aNegativeAsRMinus() {
    assertThat(TrustPositionMessages.rands(new BigDecimal("-1250"))).isEqualTo("R-1 250,00");
    assertThat(TrustPositionMessages.rands(new BigDecimal("403500.00"))).isEqualTo("R403 500,00");
    assertThat(TrustPositionMessages.rands(BigDecimal.ZERO)).isEqualTo("R0,00");
  }

  private static BigDecimal cents(Random random, int maxRand) {
    return BigDecimal.valueOf(random.nextLong(maxRand * 100L), 2);
  }

  // --- the typed reader of a stored summary (D-191) ---

  @Test
  void theStoredSummaryReadsBackAsTheRecord_withWhetherEq1AndEq2Held() {
    var agrees = TrustPositionEquation.compute(section17313(), sums("5000.00", "20000.00"));
    var summary =
        new TrustPositionEquation.TrustPositionSummary(
            List.of(),
            agrees.blocks(),
            List.of(),
            List.of("UNIDENTIFIED_OVER_ONE_YEAR"),
            agrees.equation());

    var read = TrustPositionEquation.TrustPositionSummary.from(summary.toMap()).orElseThrow();
    assertThat(read).isEqualTo(summary);
    assertThat(read.eq1Holds()).isTrue();
    assertThat(read.eq2Holds()).isTrue();

    var offByTen =
        TrustPositionEquation.compute(
            inputs("418500.00", "403510.00", "5000.00", "20000.00"), sums("5000.00", "20000.00"));
    var failing =
        TrustPositionEquation.TrustPositionSummary.from(
                new TrustPositionEquation.TrustPositionSummary(
                        List.of(), offByTen.blocks(), List.of(), List.of(), offByTen.equation())
                    .toMap())
            .orElseThrow();
    assertThat(failing.eq1Holds()).isFalse();
    assertThat(failing.eq2Holds()).isFalse();
  }

  @Test
  void anAccountCheckFailure_hasNoEquation_soNeitherHolds_andNoSummaryReadsEmpty() {
    var summary =
        new TrustPositionEquation.TrustPositionSummary(
            List.of(new TrustPositionMessage("ACCOUNT_CLOSED", "G is closed.")),
            List.of(),
            List.of(),
            List.of(),
            null);
    var read = TrustPositionEquation.TrustPositionSummary.from(summary.toMap()).orElseThrow();
    assertThat(read.accountErrors())
        .extracting(TrustPositionMessage::code)
        .containsExactly("ACCOUNT_CLOSED");
    assertThat(read.eq1Holds()).isFalse();
    assertThat(read.eq2Holds()).isFalse();
    assertThat(TrustPositionEquation.TrustPositionSummary.from(null)).isEmpty();
  }
}
