package io.b2mash.b2b.b2bstrawman.invoice;

import static org.assertj.core.api.Assertions.assertThat;

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
}
