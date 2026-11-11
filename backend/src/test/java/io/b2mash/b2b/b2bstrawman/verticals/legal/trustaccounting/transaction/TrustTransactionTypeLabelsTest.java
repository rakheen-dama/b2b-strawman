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
}
