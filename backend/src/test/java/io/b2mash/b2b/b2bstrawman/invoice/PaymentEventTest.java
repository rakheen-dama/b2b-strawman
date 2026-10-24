package io.b2mash.b2b.b2bstrawman.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentEventTest {

  private PaymentEvent buildEvent() {
    return new PaymentEvent(
        UUID.randomUUID(),
        "stripe",
        "cs_test_123",
        PaymentEventStatus.CREATED,
        new BigDecimal("1500.00"),
        "ZAR",
        "OPERATING");
  }

  @Test
  void constructor_sets_fields() {
    var invoiceId = UUID.randomUUID();
    var event =
        new PaymentEvent(
            invoiceId,
            "payfast",
            "sess_abc",
            PaymentEventStatus.PENDING,
            new BigDecimal("250.50"),
            "USD",
            "TRUST");

    assertThat(event.getInvoiceId()).isEqualTo(invoiceId);
    assertThat(event.getProviderSlug()).isEqualTo("payfast");
    assertThat(event.getSessionId()).isEqualTo("sess_abc");
    assertThat(event.getStatus()).isEqualTo(PaymentEventStatus.PENDING);
    assertThat(event.getAmount()).isEqualByComparingTo(new BigDecimal("250.50"));
    assertThat(event.getCurrency()).isEqualTo("USD");
    assertThat(event.getPaymentDestination()).isEqualTo("TRUST");
  }

  @Test
  void updateStatus_changes_status() {
    var event = buildEvent();
    assertThat(event.getStatus()).isEqualTo(PaymentEventStatus.CREATED);

    event.updateStatus(PaymentEventStatus.COMPLETED);
    assertThat(event.getStatus()).isEqualTo(PaymentEventStatus.COMPLETED);
  }

  @Test
  void prePersist_sets_timestamps() {
    var event = buildEvent();
    assertThat(event.getCreatedAt()).isNull();
    assertThat(event.getUpdatedAt()).isNull();

    event.onPrePersist();

    assertThat(event.getCreatedAt()).isNotNull();
    assertThat(event.getUpdatedAt()).isNotNull();
    assertThat(event.getCreatedAt()).isEqualTo(event.getUpdatedAt());
  }

  // --- Phase 88 U15 (ADR-347 A5) ---

  @Test
  void completed_factory_records_the_posting_with_its_own_destination() {
    var invoiceId = UUID.randomUUID();
    var posting =
        new PaymentPosting(
            new BigDecimal("12000.00"),
            PaymentMethod.TRUST_TRANSFER,
            "FT-1",
            "trust_fee_transfer",
            "sess_1",
            "TRUST");

    var event = PaymentEvent.completed(invoiceId, "ZAR", "OPERATING", posting);

    assertThat(event.getInvoiceId()).isEqualTo(invoiceId);
    assertThat(event.getStatus()).isEqualTo(PaymentEventStatus.COMPLETED);
    assertThat(event.getAmount()).isEqualByComparingTo("12000.00");
    assertThat(event.getPaymentMethod()).isEqualTo(PaymentMethod.TRUST_TRANSFER);
    assertThat(event.getPaymentReference()).isEqualTo("FT-1");
    assertThat(event.getProviderSlug()).isEqualTo("trust_fee_transfer");
    assertThat(event.getSessionId()).isEqualTo("sess_1");
    assertThat(event.getCurrency()).isEqualTo("ZAR");
    assertThat(event.getPaymentDestination()).isEqualTo("TRUST");
  }

  @Test
  void completed_factory_falls_back_to_the_invoice_destination() {
    var posting = PaymentPosting.manual(new BigDecimal("100.00"), PaymentMethod.EFT, null);

    var event = PaymentEvent.completed(UUID.randomUUID(), "ZAR", "OPERATING", posting);

    assertThat(event.getPaymentDestination()).isEqualTo("OPERATING");
    assertThat(event.getProviderSlug()).isEqualTo("manual");
    assertThat(event.getPaymentMethod()).isEqualTo(PaymentMethod.EFT);
    assertThat(event.getPaymentReference()).isNull();
    assertThat(event.getSessionId()).isNull();
  }

  @Test
  void payment_method_isManual_is_eft_cash_other_only() {
    assertThat(PaymentMethod.EFT.isManual()).isTrue();
    assertThat(PaymentMethod.CASH.isManual()).isTrue();
    assertThat(PaymentMethod.OTHER.isManual()).isTrue();
    assertThat(PaymentMethod.CARD.isManual()).isFalse();
    assertThat(PaymentMethod.TRUST_TRANSFER.isManual()).isFalse();
    assertThat(PaymentMethod.XERO.isManual()).isFalse();
  }

  @Test
  void posting_refuses_excess_for_manual_entry_whatever_the_method() {
    assertThat(PaymentPosting.manual(BigDecimal.TEN, PaymentMethod.CARD, null).refusesExcess())
        .isTrue();
    assertThat(
            new PaymentPosting(BigDecimal.TEN, PaymentMethod.CARD, null, "stripe", "s", null)
                .refusesExcess())
        .isFalse();
    assertThat(
            new PaymentPosting(BigDecimal.TEN, PaymentMethod.XERO, null, "xero", null, null)
                .refusesExcess())
        .isFalse();
  }

  @Test
  void posting_rejects_a_negative_amount() {
    assertThatThrownBy(
            () -> PaymentPosting.manual(new BigDecimal("-1.00"), PaymentMethod.EFT, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
