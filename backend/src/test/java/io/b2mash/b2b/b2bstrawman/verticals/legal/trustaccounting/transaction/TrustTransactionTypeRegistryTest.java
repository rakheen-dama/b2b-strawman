package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType.Approval;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType.Origin;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType.Reversal;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Phase 92 slice 646A (task 646.2, architecture §16.2.1, ADR-378): the transaction-type registry
 * carries exactly the seven kept types, each with every attribute of the §16.2.1 table. Plain JUnit
 * — the registry is a pure enum. {@code creditorAccountType} is a 646B attribute and is not on the
 * 646A registry.
 */
class TrustTransactionTypeRegistryTest {

  /** An all-upper-case constant name such as {@code FEE_TRANSFER} or {@code DEPOSIT}. */
  private static final Pattern CONSTANT = Pattern.compile("\\b[A-Z]{2,}(_[A-Z]+)*\\b");

  /** The §16.2.1 table for the seven kept types (646A), row by row. */
  private record Row(
      TrustTransactionType type,
      int cashbookSign,
      TrustLedger ledger,
      int ledgerSign,
      Approval approval,
      Reversal reversal,
      Origin origin,
      boolean clientVisible,
      String auditKey,
      String label,
      String clientLabel) {}

  private static final List<Row> TABLE =
      List.of(
          new Row(
              TrustTransactionType.DEPOSIT,
              1,
              TrustLedger.CLIENT,
              1,
              Approval.NONE,
              Reversal.NEEDS_APPROVAL,
              Origin.FIRM,
              true,
              "trust_deposit",
              "Deposit",
              "Deposit"),
          new Row(
              TrustTransactionType.PAYMENT,
              -1,
              TrustLedger.CLIENT,
              -1,
              Approval.REQUIRED,
              Reversal.IMMEDIATE,
              Origin.FIRM,
              true,
              "trust_payment",
              "Payment",
              "Payment"),
          new Row(
              TrustTransactionType.FEE_TRANSFER,
              -1,
              TrustLedger.CLIENT,
              -1,
              Approval.REQUIRED,
              Reversal.IMMEDIATE,
              Origin.FIRM,
              true,
              "trust_fee_transfer",
              "Fee transfer",
              "Fee transfer"),
          new Row(
              TrustTransactionType.REFUND,
              -1,
              TrustLedger.CLIENT,
              -1,
              Approval.REQUIRED,
              Reversal.IMMEDIATE,
              Origin.FIRM,
              true,
              "trust_refund",
              "Refund",
              "Refund"),
          new Row(
              TrustTransactionType.TRANSFER_OUT,
              0,
              TrustLedger.CLIENT,
              -1,
              Approval.NONE,
              Reversal.PAIR,
              Origin.FIRM,
              true,
              "trust_transfer",
              "Transfer out",
              "Transfer out"),
          new Row(
              TrustTransactionType.TRANSFER_IN,
              0,
              TrustLedger.CLIENT,
              1,
              Approval.NONE,
              Reversal.VIA_PAIR,
              Origin.FIRM,
              true,
              "trust_transfer",
              "Transfer in",
              "Transfer in"),
          // REVERSAL has no effect, ledger or origin of its own (its original's); client visible
          // since 649B — the contra entry is replayed to the portal with its reversed original.
          new Row(
              TrustTransactionType.REVERSAL,
              0,
              null,
              0,
              Approval.WITH_ORIGINAL,
              Reversal.NONE,
              null,
              true,
              "trust_reversal",
              "Reversal",
              "Reversal"));

  @Test
  void registry_holdsExactlyTheSevenKeptTypes() {
    assertThat(TrustTransactionType.values())
        .containsExactlyInAnyOrderElementsOf(TABLE.stream().map(Row::type).toList());
  }

  @Test
  void everyAttribute_matchesTheArchitectureTable() {
    for (var row : TABLE) {
      var type = row.type();
      assertThat(type.cashbookSign()).as("%s cashbookSign", type).isEqualTo(row.cashbookSign());
      assertThat(type.ledger()).as("%s ledger", type).isEqualTo(row.ledger());
      assertThat(type.ledgerSign()).as("%s ledgerSign", type).isEqualTo(row.ledgerSign());
      assertThat(type.approval()).as("%s approval", type).isEqualTo(row.approval());
      assertThat(type.reversal()).as("%s reversal", type).isEqualTo(row.reversal());
      assertThat(type.origin()).as("%s origin", type).isEqualTo(row.origin());
      assertThat(type.clientVisible()).as("%s clientVisible", type).isEqualTo(row.clientVisible());
      assertThat(type.auditKey()).as("%s auditKey", type).isEqualTo(row.auditKey());
      assertThat(type.label()).as("%s label", type).isEqualTo(row.label());
      assertThat(type.clientLabel()).as("%s clientLabel", type).isEqualTo(row.clientLabel());
    }
  }

  @Test
  void names_fitTheVarchar20Column() {
    assertThat(TrustTransactionType.values())
        .allSatisfy(type -> assertThat(type.name()).hasSizeLessThanOrEqualTo(20));
  }

  @Test
  void labels_areHumanCopy_neverACode() {
    for (var type : TrustTransactionType.values()) {
      for (var label : List.of(type.label(), type.clientLabel())) {
        assertThat(label).as("%s label", type).isNotBlank().doesNotContain("_");
        assertThat(label).as("%s label", type).isNotEqualTo(type.name());
        assertThat(CONSTANT.matcher(label).find())
            .as("%s label %s contains an all-upper-case constant", type, label)
            .isFalse();
      }
    }
  }

  @Test
  void of_roundTripsEveryName() {
    for (var type : TrustTransactionType.values()) {
      assertThat(TrustTransactionType.of(type.name())).isSameAs(type);
      assertThat(TrustTransactionType.find(type.name())).contains(type);
    }
  }

  @Test
  void of_refusesAnUnknownCode() {
    assertThatThrownBy(() -> TrustTransactionType.of("NOPE"))
        .isInstanceOfSatisfying(
            InvalidStateException.class,
            e -> {
              assertThat(e.getBody().getStatus()).isEqualTo(400);
              assertThat(e.getBody().getTitle()).isEqualTo("Unknown trust transaction type");
              assertThat(e.getBody().getDetail()).isEqualTo("Unknown trust transaction type: NOPE");
            });
    // The removed interest types are unknown too (V16 deleted their rows).
    assertThatThrownBy(() -> TrustTransactionType.of("INTEREST_CREDIT"))
        .isInstanceOf(InvalidStateException.class);
    assertThatThrownBy(() -> TrustTransactionType.of(null))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void find_isLenient() {
    assertThat(TrustTransactionType.find("NOPE")).isEmpty();
    assertThat(TrustTransactionType.find("INTEREST_LPFF")).isEmpty();
    assertThat(TrustTransactionType.find(null)).isEmpty();
  }

  @Test
  void effectFor_signsTheAmountOnTheCashbookAndTheLedger() {
    var amount = new BigDecimal("1250.00");
    for (var row : TABLE) {
      if (row.type() == TrustTransactionType.REVERSAL) {
        continue;
      }
      var effect = row.type().effectFor(amount);
      assertThat(effect.ledger()).as("%s effect ledger", row.type()).isEqualTo(row.ledger());
      assertThat(effect.cashbookEffect())
          .as("%s cashbook effect", row.type())
          .isEqualByComparingTo(amount.multiply(BigDecimal.valueOf(row.cashbookSign())));
      assertThat(effect.ledgerEffect())
          .as("%s ledger effect", row.type())
          .isEqualByComparingTo(amount.multiply(BigDecimal.valueOf(row.ledgerSign())));
    }
    assertThat(TrustTransactionType.DEPOSIT.effectFor(amount).cashbookEffect())
        .isEqualByComparingTo("1250.00");
    assertThat(TrustTransactionType.PAYMENT.effectFor(amount).ledgerEffect())
        .isEqualByComparingTo("-1250.00");
    assertThat(TrustTransactionType.TRANSFER_OUT.effectFor(amount).cashbookEffect())
        .isEqualByComparingTo("0");
    assertThat(TrustTransactionType.TRANSFER_OUT.effectFor(amount).ledgerEffect())
        .isEqualByComparingTo("-1250.00");
  }

  @Test
  void reversal_hasNoEffectOfItsOwn() {
    assertThatThrownBy(() -> TrustTransactionType.REVERSAL.effectFor(BigDecimal.TEN))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void zeroAndNegatedEffects() {
    var zero = TrustLedgerEffect.zero(TrustLedger.CLIENT);
    assertThat(zero.cashbookEffect()).isEqualByComparingTo("0");
    assertThat(zero.ledgerEffect()).isEqualByComparingTo("0");
    var negated = TrustTransactionType.DEPOSIT.effectFor(new BigDecimal("10.00")).negated();
    assertThat(negated.ledger()).isEqualTo(TrustLedger.CLIENT);
    assertThat(negated.cashbookEffect()).isEqualByComparingTo("-10.00");
    assertThat(negated.ledgerEffect()).isEqualByComparingTo("-10.00");
    assertThat(Arrays.stream(TrustLedger.values()).map(Enum::name))
        .containsExactly("CLIENT", "CLIENT_INVESTMENT", "NON_CLIENT", "INTER_ACCOUNT");
  }
}
