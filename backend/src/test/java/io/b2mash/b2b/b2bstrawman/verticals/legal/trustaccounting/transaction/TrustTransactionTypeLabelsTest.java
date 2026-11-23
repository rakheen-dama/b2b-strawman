package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 633C D6 / G89 W2-c3 / Phase 92 646E: every trust type label comes from the registry — {@link
 * TrustTransactionTypeLabels#label} is the client copy (client surfaces), {@link
 * TrustTransactionTypeLabels#firmLabel} the firm copy (DTOs, MCP, firm reports) — and no label ever
 * prints a raw enum or throws on a code the registry does not know (D4).
 */
class TrustTransactionTypeLabelsTest {

  @ParameterizedTest
  @EnumSource(TrustTransactionType.class)
  void everyRegistryType_readsItsLabelsFromTheRegistry(TrustTransactionType type) {
    assertThat(TrustTransactionTypeLabels.label(type.name())).isEqualTo(type.clientLabel());
    assertThat(TrustTransactionTypeLabels.firmLabel(type.name())).isEqualTo(type.label());
  }

  @Test
  void theOriginalTypes_keepTheirCopy() {
    assertThat(TrustTransactionTypeLabels.label("DEPOSIT")).isEqualTo("Deposit");
    assertThat(TrustTransactionTypeLabels.label("FEE_TRANSFER")).isEqualTo("Fee transfer");
    assertThat(TrustTransactionTypeLabels.label("TRANSFER_IN")).isEqualTo("Transfer in");
    assertThat(TrustTransactionTypeLabels.label("REVERSAL")).isEqualTo("Reversal");
    assertThat(TrustTransactionTypeLabels.firmLabel("FUND_INTEREST"))
        .isEqualTo("Interest received for the Legal Practitioners' Fidelity Fund");
  }

  @Test
  void theTakeOnTypes_readAsTakeOnCopy() {
    // 657A (§17.2.8, X3): the brought-forward balance is the one take-on row a client sees
    assertThat(TrustTransactionTypeLabels.label("TAKEON_CLIENT"))
        .isEqualTo("Balance brought forward — take-on");
    assertThat(TrustTransactionTypeLabels.firmLabel("TAKEON_PAY_UNPRES"))
        .isEqualTo("Payment not yet presented at take-on");
  }

  /**
   * D4: a retired code (INTEREST_CREDIT / INTEREST_LPFF, deleted by V16 but still on historic audit
   * events) or any unknown code falls back to sentence case — never the raw enum, never a throw.
   */
  @ParameterizedTest
  @ValueSource(strings = {"INTEREST_CREDIT", "INTEREST_LPFF", "SOME_NEW_TYPE", "lower_case"})
  void unknownOrRetiredCode_fallsBackToSentenceCase_neverThrows(String code) {
    String expected =
        Character.toUpperCase(code.charAt(0))
            + code.substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
    assertThat(TrustTransactionTypeLabels.label(code)).isEqualTo(expected);
    assertThat(TrustTransactionTypeLabels.firmLabel(code)).isEqualTo(expected);
  }

  @Test
  void unknownCode_examples() {
    assertThat(TrustTransactionTypeLabels.label("SOME_NEW_TYPE")).isEqualTo("Some new type");
    assertThat(TrustTransactionTypeLabels.firmLabel("INTEREST_LPFF")).isEqualTo("Interest lpff");
  }

  @Test
  void missingType_staysNullForTheTemplatePlaceholder() {
    assertThat(TrustTransactionTypeLabels.label(null)).isNull();
    assertThat(TrustTransactionTypeLabels.label(" ")).isNull();
    assertThat(TrustTransactionTypeLabels.firmLabel(null)).isNull();
    assertThat(TrustTransactionTypeLabels.firmLabel(" ")).isNull();
  }

  /**
   * Phase 92 649B (PR #1789 review): a REVERSAL row's own description is the firm user's reversal
   * reason — every client surface prints the generated "Reversal — reference" instead.
   */
  @Test
  void clientDescription_reversalIsGenerated_neverTheFirmReason() {
    assertThat(
            TrustTransactionTypeLabels.clientDescription(
                "REVERSAL", "Clerk keyed the wrong matter", "REV-PAY-77"))
        .isEqualTo("Reversal — REV-PAY-77");
    assertThat(TrustTransactionTypeLabels.clientDescription("REVERSAL", "Wrong matter", null))
        .isEqualTo("Reversal");
    assertThat(TrustTransactionTypeLabels.clientDescription("REVERSAL", "Wrong matter", " "))
        .isEqualTo("Reversal");
  }

  @Test
  void clientDescription_everyOtherTypeKeepsItsOwnDescription() {
    assertThat(
            TrustTransactionTypeLabels.clientDescription("PAYMENT", "Payment to sheriff", "PAY-77"))
        .isEqualTo("Payment to sheriff");
    assertThat(TrustTransactionTypeLabels.clientDescription("DEPOSIT", null, "DEP-1")).isNull();
  }
}
