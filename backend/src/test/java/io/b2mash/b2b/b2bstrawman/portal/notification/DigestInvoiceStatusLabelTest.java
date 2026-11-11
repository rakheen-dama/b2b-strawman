package io.b2mash.b2b.b2bstrawman.portal.notification;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * G89 W2-c2: the weekly digest's fee-note rows use the portal fee-note badge labels and never print
 * a raw status enum.
 */
class DigestInvoiceStatusLabelTest {

  @ParameterizedTest
  @CsvSource({
    "DRAFT, Draft",
    "APPROVED, Approved",
    "SENT, Sent",
    "PARTIALLY_PAID, Partially paid",
    "PAID, Paid",
    "VOID, Void"
  })
  void everyFeeNoteStatus_hasThePortalLabel(String status, String label) {
    assertThat(PortalDigestContentAssembler.invoiceStatusLabel(status)).isEqualTo(label);
  }

  /** Drift guard: a new {@link InvoiceStatus} must not reach the digest as its enum name. */
  @ParameterizedTest
  @EnumSource(InvoiceStatus.class)
  void noFeeNoteStatus_printsItsEnumName(InvoiceStatus status) {
    assertThat(PortalDigestContentAssembler.invoiceStatusLabel(status.name()))
        .isNotBlank()
        .isNotEqualTo(status.name());
  }

  @Test
  void unmappedStatus_fallsBackToSentenceCase_andMissingIsEmpty() {
    assertThat(PortalDigestContentAssembler.invoiceStatusLabel("SOME_NEW_STATUS"))
        .isEqualTo("Some new status");
    assertThat(PortalDigestContentAssembler.invoiceStatusLabel(null)).isEmpty();
  }
}
