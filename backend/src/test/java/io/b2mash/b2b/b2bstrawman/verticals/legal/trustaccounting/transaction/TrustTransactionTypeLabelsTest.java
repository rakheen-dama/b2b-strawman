package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 633C D6 / G89 W2-c3: the trust-activity email's Type cell and the Statement of Account's trust
 * Payments Type column use client-facing labels (same copy as the portal trust page) and never
 * print a raw enum.
 */
class TrustTransactionTypeLabelsTest {

  @ParameterizedTest
  @CsvSource({
    "DEPOSIT, Deposit",
    "PAYMENT, Payment",
    "REFUND, Refund",
    "FEE_TRANSFER, Fee transfer",
    "INTEREST_CREDIT, Interest credited",
    "INTEREST_LPFF, Interest to LPFF",
    "TRANSFER_IN, Transfer in",
    "TRANSFER_OUT, Transfer out",
    "REVERSAL, Reversal"
  })
  void everyTrustTransactionType_hasAClientFacingLabel(String type, String label) {
    assertThat(TrustTransactionTypeLabels.label(type)).isEqualTo(label);
  }

  @Test
  void unmappedType_fallsBackToSentenceCase() {
    assertThat(TrustTransactionTypeLabels.label("SOME_NEW_TYPE")).isEqualTo("Some new type");
  }

  @Test
  void missingType_staysNullForTheTemplatePlaceholder() {
    assertThat(TrustTransactionTypeLabels.label(null)).isNull();
    assertThat(TrustTransactionTypeLabels.label(" ")).isNull();
  }

  /** The client-surface label agrees with the type registry's client label for a reversal. */
  @Test
  void reversalLabel_agreesWithTheRegistryClientLabel() {
    assertThat(TrustTransactionTypeLabels.label("REVERSAL"))
        .isEqualTo(TrustTransactionType.REVERSAL.clientLabel());
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
