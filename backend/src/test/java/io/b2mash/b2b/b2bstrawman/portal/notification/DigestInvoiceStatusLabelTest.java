package io.b2mash.b2b.b2bstrawman.portal.notification;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * G89 W2-c2: the weekly digest's fee-note rows use the portal fee-note badge labels and never print
 * a raw status enum.
 */
class DigestInvoiceStatusLabelTest {

  /**
   * The portal fee-note badge labels (`portal/components/invoice-status-badge.tsx`). Must cover
   * every {@link InvoiceStatus}: adding a status without deciding its client-facing label fails
   * {@link #expectedLabels_coverEveryFeeNoteStatus()} (the sentence-case fallback alone would
   * otherwise let it through silently).
   */
  private static final Map<InvoiceStatus, String> EXPECTED =
      new EnumMap<>(
          Map.of(
              InvoiceStatus.DRAFT, "Draft",
              InvoiceStatus.APPROVED, "Approved",
              InvoiceStatus.SENT, "Sent",
              InvoiceStatus.PARTIALLY_PAID, "Partially paid",
              InvoiceStatus.PAID, "Paid",
              InvoiceStatus.VOID, "Void"));

  @Test
  void expectedLabels_coverEveryFeeNoteStatus() {
    assertThat(EXPECTED.keySet())
        .as("every InvoiceStatus needs a decided digest label (portal badge parity)")
        .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(InvoiceStatus.class));
  }

  @ParameterizedTest
  @EnumSource(InvoiceStatus.class)
  void everyFeeNoteStatus_hasThePortalLabel(InvoiceStatus status) {
    assertThat(PortalDigestContentAssembler.invoiceStatusLabel(status.name()))
        .isEqualTo(EXPECTED.get(status))
        .isNotEqualTo(status.name());
  }

  @Test
  void unmappedStatus_fallsBackToSentenceCase_andMissingIsEmpty() {
    assertThat(PortalDigestContentAssembler.invoiceStatusLabel("SOME_NEW_STATUS"))
        .isEqualTo("Some new status");
    assertThat(PortalDigestContentAssembler.invoiceStatusLabel(null)).isEmpty();
  }
}
