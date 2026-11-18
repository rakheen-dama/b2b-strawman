package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType.Approval;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType.Origin;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType.Reversal;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Phase 92 slices 646A / 646B (tasks 646.2 / 646.13, architecture §16.2.1, ADR-378, ADR-376): the
 * transaction-type registry carries exactly the 25 types — the seven kept (646A) and the eighteen
 * non-client, investment and inter-account types (646B) — each with every attribute of the §16.2.1
 * table, including {@code creditorAccountType}. Plain JUnit — the registry is a pure enum.
 */
class TrustTransactionTypeRegistryTest {

  /** An all-upper-case constant name such as {@code FEE_TRANSFER} or {@code DEPOSIT}. */
  private static final Pattern CONSTANT = Pattern.compile("\\b[A-Z]{2,}(_[A-Z]+)*\\b");

  /** The §16.2.1 table for all 25 types, row by row. */
  private record Row(
      TrustTransactionType type,
      int cashbookSign,
      TrustLedger ledger,
      int ledgerSign,
      TrustCreditorAccountType creditorAccountType,
      Approval approval,
      Reversal reversal,
      Origin origin,
      boolean clientVisible,
      String auditKey,
      String label,
      String clientLabel) {}

  private static final TrustCreditorAccountType FUND =
      TrustCreditorAccountType.FUND_INTEREST_AND_CHARGES;
  private static final TrustCreditorAccountType UNID =
      TrustCreditorAccountType.UNIDENTIFIED_DEPOSITS;

  private static final List<Row> TABLE =
      List.of(
          new Row(
              TrustTransactionType.DEPOSIT,
              1,
              TrustLedger.CLIENT,
              1,
              null,
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
              null,
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
              null,
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
              null,
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
              null,
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
              null,
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
              null,
              Approval.WITH_ORIGINAL,
              Reversal.NONE,
              null,
              true,
              "trust_reversal",
              "Reversal",
              "Reversal"),
          // 646B: the eighteen non-client, investment and inter-account types (§16.2.1 order).
          new Row(
              TrustTransactionType.FUND_INTEREST,
              1,
              TrustLedger.NON_CLIENT,
              1,
              FUND,
              Approval.NONE,
              Reversal.IMMEDIATE,
              Origin.BANK,
              false,
              "trust_fund_interest",
              "Interest received for the Legal Practitioners' Fidelity Fund",
              "Interest received for the Legal Practitioners' Fidelity Fund"),
          new Row(
              TrustTransactionType.FUND_SWEEP,
              -1,
              TrustLedger.NON_CLIENT,
              -1,
              FUND,
              Approval.NONE,
              Reversal.IMMEDIATE,
              Origin.BANK,
              false,
              "trust_fund_sweep",
              "Interest paid to the Legal Practitioners' Fidelity Fund",
              "Interest paid to the Legal Practitioners' Fidelity Fund"),
          new Row(
              TrustTransactionType.BANK_CHARGE,
              -1,
              TrustLedger.NON_CLIENT,
              -1,
              FUND,
              Approval.NONE,
              Reversal.IMMEDIATE,
              Origin.BANK,
              false,
              "trust_bank_charge",
              "Bank charge",
              "Bank charge"),
          new Row(
              TrustTransactionType.CHARGE_REFUND,
              1,
              TrustLedger.NON_CLIENT,
              1,
              FUND,
              Approval.NONE,
              Reversal.IMMEDIATE,
              Origin.BANK,
              false,
              "trust_charge_refund",
              "Bank charge refunded from the business account",
              "Bank charge refunded from the business account"),
          new Row(
              TrustTransactionType.UNIDENTIFIED_DEPOSIT,
              1,
              TrustLedger.NON_CLIENT,
              1,
              UNID,
              Approval.NONE,
              Reversal.OWNER,
              Origin.BANK,
              false,
              "trust_unidentified_deposit",
              "Unidentified deposit",
              "Unidentified deposit"),
          new Row(
              TrustTransactionType.UNIDENT_ALLOC_OUT,
              0,
              TrustLedger.NON_CLIENT,
              -1,
              UNID,
              Approval.REQUIRED,
              Reversal.PAIR,
              Origin.FIRM,
              false,
              "trust_unident_alloc_out",
              "Unidentified deposit allocated",
              "Unidentified deposit allocated"),
          new Row(
              TrustTransactionType.UNIDENT_ALLOC_IN,
              0,
              TrustLedger.CLIENT,
              1,
              null,
              Approval.WITH_PAIR,
              Reversal.VIA_PAIR,
              Origin.FIRM,
              true,
              "trust_unident_alloc_in",
              "Deposit identified and allocated",
              "Deposit identified and allocated"),
          new Row(
              TrustTransactionType.UNIDENT_FUND_PAY,
              -1,
              TrustLedger.NON_CLIENT,
              -1,
              UNID,
              Approval.REQUIRED,
              Reversal.IMMEDIATE,
              Origin.FIRM,
              false,
              "trust_unident_fund_pay",
              "Unidentified deposit paid to the Fund",
              "Unidentified deposit paid to the Fund"),
          new Row(
              TrustTransactionType.INV_PLACE,
              -1,
              TrustLedger.CLIENT,
              -1,
              null,
              Approval.REQUIRED,
              Reversal.NONE,
              Origin.FIRM,
              true,
              "trust_inv_place",
              "Placed in section 86(4) investment",
              "Placed in section 86(4) investment"),
          new Row(
              TrustTransactionType.INV_RECEIVE,
              1,
              TrustLedger.CLIENT_INVESTMENT,
              1,
              null,
              Approval.WITH_PAIR,
              Reversal.NONE,
              Origin.FIRM,
              false,
              "trust_inv_receive",
              "Investment capital received",
              "Investment capital received"),
          new Row(
              TrustTransactionType.INV_INTEREST,
              1,
              TrustLedger.CLIENT_INVESTMENT,
              1,
              null,
              Approval.NONE,
              Reversal.OWNER,
              Origin.FIRM,
              true,
              "trust_inv_interest",
              "Section 86(4) investment interest",
              "Section 86(4) investment interest"),
          new Row(
              TrustTransactionType.FUND_INV_SHARE,
              1,
              TrustLedger.NON_CLIENT,
              1,
              FUND,
              Approval.NONE,
              Reversal.OWNER,
              Origin.FIRM,
              false,
              "trust_fund_inv_share",
              "Fund share of section 86(4) interest (5%)",
              "Fund share of section 86(4) interest (5%)"),
          new Row(
              TrustTransactionType.INV_WITHDRAW,
              -1,
              TrustLedger.CLIENT_INVESTMENT,
              -1,
              null,
              Approval.NONE,
              Reversal.NONE,
              Origin.FIRM,
              false,
              "trust_inv_withdraw",
              "Investment withdrawn",
              "Investment withdrawn"),
          new Row(
              TrustTransactionType.INV_RETURN,
              1,
              TrustLedger.CLIENT,
              1,
              null,
              Approval.WITH_PAIR,
              Reversal.NONE,
              Origin.FIRM,
              true,
              "trust_inv_return",
              "Investment returned to trust",
              "Investment returned to trust"),
          new Row(
              TrustTransactionType.FIRM_INV_PLACE,
              -1,
              TrustLedger.INTER_ACCOUNT,
              -1,
              null,
              Approval.REQUIRED,
              Reversal.NONE,
              Origin.FIRM,
              false,
              "trust_firm_inv_place",
              "Placed in section 86(3) investment",
              "Placed in section 86(3) investment"),
          new Row(
              TrustTransactionType.FIRM_INV_RECEIVE,
              1,
              TrustLedger.INTER_ACCOUNT,
              1,
              null,
              Approval.WITH_PAIR,
              Reversal.NONE,
              Origin.FIRM,
              false,
              "trust_firm_inv_receive",
              "Section 86(3) investment capital received",
              "Section 86(3) investment capital received"),
          new Row(
              TrustTransactionType.FIRM_INV_WITHDRAW,
              -1,
              TrustLedger.INTER_ACCOUNT,
              -1,
              null,
              Approval.NONE,
              Reversal.NONE,
              Origin.FIRM,
              false,
              "trust_firm_inv_withdraw",
              "Section 86(3) investment withdrawn",
              "Section 86(3) investment withdrawn"),
          new Row(
              TrustTransactionType.FIRM_INV_RETURN,
              1,
              TrustLedger.INTER_ACCOUNT,
              1,
              null,
              Approval.WITH_PAIR,
              Reversal.NONE,
              Origin.FIRM,
              false,
              "trust_firm_inv_return",
              "Section 86(3) investment returned to trust",
              "Section 86(3) investment returned to trust"));

  @Test
  void registry_holdsTheTwentyFiveTypes() {
    assertThat(TrustTransactionType.values()).hasSize(25);
    assertThat(TABLE).hasSize(25);
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
      assertThat(type.creditorAccountType())
          .as("%s creditorAccountType", type)
          .isEqualTo(row.creditorAccountType());
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
  void creditorAccountType_isSetExactlyOnNonClientTypes() {
    for (var type : TrustTransactionType.values()) {
      assertThat(type.creditorAccountType() != null)
          .as("%s has a creditor account type iff its ledger is NON_CLIENT", type)
          .isEqualTo(type.ledger() == TrustLedger.NON_CLIENT);
    }
  }

  @Test
  void bankOriginTypes_postWithoutApproval() {
    var bankTypes =
        Arrays.stream(TrustTransactionType.values())
            .filter(t -> t.origin() == Origin.BANK)
            .toList();
    assertThat(bankTypes)
        .containsExactlyInAnyOrder(
            TrustTransactionType.FUND_INTEREST,
            TrustTransactionType.FUND_SWEEP,
            TrustTransactionType.BANK_CHARGE,
            TrustTransactionType.CHARGE_REFUND,
            TrustTransactionType.UNIDENTIFIED_DEPOSIT);
    assertThat(bankTypes).allSatisfy(t -> assertThat(t.approval()).isEqualTo(Approval.NONE));
  }

  @Test
  void nonClientAndInterAccountTypes_areNeverClientVisible() {
    assertThat(TrustTransactionType.values())
        .filteredOn(
            t -> t.ledger() == TrustLedger.NON_CLIENT || t.ledger() == TrustLedger.INTER_ACCOUNT)
        .hasSize(12)
        .allSatisfy(t -> assertThat(t.clientVisible()).as("%s clientVisible", t).isFalse());
  }

  @Test
  void investmentRows_areTheInvestmentLedgersAndTheClientLegsPairedWithThem() {
    assertThat(TrustTransactionType.values())
        .filteredOn(TrustTransactionType::isInvestmentRow)
        .containsExactlyInAnyOrder(
            TrustTransactionType.INV_PLACE,
            TrustTransactionType.INV_RECEIVE,
            TrustTransactionType.INV_INTEREST,
            TrustTransactionType.INV_WITHDRAW,
            TrustTransactionType.INV_RETURN,
            TrustTransactionType.FIRM_INV_PLACE,
            TrustTransactionType.FIRM_INV_RECEIVE,
            TrustTransactionType.FIRM_INV_WITHDRAW,
            TrustTransactionType.FIRM_INV_RETURN);
    // Every one but the owner-reversed interest entry (648B) is final: withdrawn, never reversed
    assertThat(TrustTransactionType.values())
        .filteredOn(type -> type.isInvestmentRow() && type.reversal() != Reversal.NONE)
        .containsExactly(TrustTransactionType.INV_INTEREST);
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
    var sweep = TrustTransactionType.FUND_SWEEP.effectFor(amount);
    assertThat(sweep.ledger()).isEqualTo(TrustLedger.NON_CLIENT);
    assertThat(sweep.cashbookEffect()).isEqualByComparingTo("-1250.00");
    assertThat(sweep.ledgerEffect()).isEqualByComparingTo("-1250.00");
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

  // --- 646E: direction (ledger-effect sign; D2) ---

  @Test
  void direction_isTheLedgerSign_neutralOnlyForReversal() {
    for (var type : TrustTransactionType.values()) {
      var expected =
          type == TrustTransactionType.REVERSAL
              ? TrustTransactionDirection.NEUTRAL
              : type.ledgerSign() > 0
                  ? TrustTransactionDirection.IN
                  : TrustTransactionDirection.OUT;
      assertThat(type.direction()).as(type.name()).isEqualTo(expected);
    }
    assertThat(TrustTransactionType.TRANSFER_IN.direction())
        .isEqualTo(TrustTransactionDirection.IN);
    assertThat(TrustTransactionType.TRANSFER_OUT.direction())
        .isEqualTo(TrustTransactionDirection.OUT);
  }

  @Test
  void rowDirection_reversalIsTheOppositeOfItsOriginal_neutralWhenUnresolved() {
    assertThat(TrustTransactionDirection.ofRow("REVERSAL", "PAYMENT"))
        .isEqualTo(TrustTransactionDirection.IN);
    assertThat(TrustTransactionDirection.ofRow("REVERSAL", "FEE_TRANSFER"))
        .isEqualTo(TrustTransactionDirection.IN);
    assertThat(TrustTransactionDirection.ofRow("REVERSAL", "DEPOSIT"))
        .isEqualTo(TrustTransactionDirection.OUT);
    assertThat(TrustTransactionDirection.ofRow("REVERSAL", "TRANSFER_IN"))
        .isEqualTo(TrustTransactionDirection.OUT);
    assertThat(TrustTransactionDirection.ofRow("REVERSAL", null))
        .isEqualTo(TrustTransactionDirection.NEUTRAL);
    assertThat(TrustTransactionDirection.ofRow("REVERSAL", "INTEREST_CREDIT"))
        .isEqualTo(TrustTransactionDirection.NEUTRAL);
    assertThat(TrustTransactionDirection.ofRow("PAYMENT", null))
        .isEqualTo(TrustTransactionDirection.OUT);
    assertThat(TrustTransactionDirection.ofRow("DEPOSIT", "PAYMENT"))
        .isEqualTo(TrustTransactionDirection.IN);
    // Unknown or retired codes never throw (D4)
    assertThat(TrustTransactionDirection.ofRow("NOPE", null))
        .isEqualTo(TrustTransactionDirection.NEUTRAL);
    assertThat(TrustTransactionDirection.ofRow(null, null))
        .isEqualTo(TrustTransactionDirection.NEUTRAL);
  }

  @Test
  void direction_opposite() {
    assertThat(TrustTransactionDirection.IN.opposite()).isEqualTo(TrustTransactionDirection.OUT);
    assertThat(TrustTransactionDirection.OUT.opposite()).isEqualTo(TrustTransactionDirection.IN);
    assertThat(TrustTransactionDirection.NEUTRAL.opposite())
        .isEqualTo(TrustTransactionDirection.NEUTRAL);
  }
}
