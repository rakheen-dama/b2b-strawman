package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.CustomerTypeTotalRow;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * G89-F13: {@link LedgerTotals} folds per-type posted totals and reconciles to the balance; each
 * total's sign comes from {@code TrustTransactionType.ledgerSign()} (ADR-378).
 */
class LedgerTotalsTest {

  private static final UUID CLIENT_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID CLIENT_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

  @Test
  void derivedBalance_636B_isZero() {
    var totals =
        LedgerTotals.ZERO
            .plus("DEPOSIT", new BigDecimal("5000.00"))
            .plus("PAYMENT", new BigDecimal("1250.00"))
            .plus("REFUND", new BigDecimal("3750.00"));

    assertThat(totals.deposits()).isEqualByComparingTo("5000.00");
    assertThat(totals.payments()).isEqualByComparingTo("1250.00");
    assertThat(totals.refunds()).isEqualByComparingTo("3750.00");
    assertThat(totals.derivedBalance()).isEqualByComparingTo("0.00");
  }

  @Test
  void derivedBalance_addsCreditsAndSubtractsDebits() {
    var totals =
        new LedgerTotals(
            new BigDecimal("1000"),
            new BigDecimal("200"),
            new BigDecimal("300"),
            new BigDecimal("150"),
            new BigDecimal("50"),
            new BigDecimal("100"));

    // 1000 + 200 − 300 − 150 − 50 − 100
    assertThat(totals.derivedBalance()).isEqualByComparingTo("600");
  }

  @Test
  void plus_mapsEveryClientLedgerType_andUnknownTypesLeaveTotalsUnchanged() {
    var totals =
        LedgerTotals.ZERO
            .plus("TRANSFER_IN", new BigDecimal("40"))
            .plus("TRANSFER_OUT", new BigDecimal("15"))
            .plus("FEE_TRANSFER", new BigDecimal("5"))
            .plus("NOT_A_TRUST_TYPE", new BigDecimal("999"))
            .plus("REVERSAL", new BigDecimal("999"))
            .plus("DEPOSIT", null);

    assertThat(totals.transfersIn()).isEqualByComparingTo("40");
    assertThat(totals.transfersOut()).isEqualByComparingTo("15");
    assertThat(totals.feeTransfers()).isEqualByComparingTo("5");
    assertThat(totals.deposits()).isEqualByComparingTo("0");
    assertThat(totals.derivedBalance()).isEqualByComparingTo("20");
  }

  @Test
  void byCustomer_foldsSignedLedgerEffectRowsPerClient_intoPositiveTotals() {
    // Rows carry the signed ledger_effect sum (TrustLedgerQueryRepository.clientTotalsByType);
    // each type's ledgerSign() turns it back into the type's posted amount
    var totals =
        LedgerTotals.byCustomer(
            List.of(
                row(CLIENT_A, "DEPOSIT", "5000.00"),
                row(CLIENT_A, "REFUND", "-3750.00"),
                row(CLIENT_B, "TRANSFER_IN", "400.00"),
                row(CLIENT_B, "TRANSFER_OUT", "-150.00"),
                row(CLIENT_A, "PAYMENT", "-1250.00")));

    assertThat(totals).hasSize(2);
    assertThat(totals.get(CLIENT_A).refunds()).isEqualByComparingTo("3750.00");
    assertThat(totals.get(CLIENT_A).payments()).isEqualByComparingTo("1250.00");
    assertThat(totals.get(CLIENT_B).transfersOut()).isEqualByComparingTo("150.00");
    assertThat(totals.get(CLIENT_A).derivedBalance()).isEqualByComparingTo("0.00");
    assertThat(totals.get(CLIENT_B).transfersIn()).isEqualByComparingTo("400.00");
    assertThat(totals.get(CLIENT_B).derivedBalance()).isEqualByComparingTo("250.00");
  }

  private static CustomerTypeTotalRow row(UUID customerId, String type, String total) {
    return new CustomerTypeTotalRow() {
      @Override
      public UUID getCustomerId() {
        return customerId;
      }

      @Override
      public String getTransactionType() {
        return type;
      }

      @Override
      public BigDecimal getTotal() {
        return new BigDecimal(total);
      }
    };
  }
}
