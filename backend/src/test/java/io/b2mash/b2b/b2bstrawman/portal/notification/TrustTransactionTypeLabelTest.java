package io.b2mash.b2b.b2bstrawman.portal.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 633C D6: the trust-activity email's Type cell uses client-facing labels (same copy as the portal
 * trust page) and never prints a raw enum.
 */
class TrustTransactionTypeLabelTest {

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
    assertThat(PortalEmailNotificationChannel.trustTransactionTypeLabel(type)).isEqualTo(label);
  }

  @Test
  void unmappedType_fallsBackToSentenceCase() {
    assertThat(PortalEmailNotificationChannel.trustTransactionTypeLabel("SOME_NEW_TYPE"))
        .isEqualTo("Some new type");
  }

  @Test
  void missingType_staysNullForTheTemplatePlaceholder() {
    assertThat(PortalEmailNotificationChannel.trustTransactionTypeLabel(null)).isNull();
    assertThat(PortalEmailNotificationChannel.trustTransactionTypeLabel(" ")).isNull();
  }
}
