package io.b2mash.b2b.b2bstrawman.integration.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NoOpPaymentGatewayTest {

  private final NoOpPaymentGateway gateway = new NoOpPaymentGateway();

  @Test
  void providerId_returns_noop() {
    assertThat(gateway.providerId()).isEqualTo("noop");
  }

  @Test
  void recordManualPayment_returns_success_with_manual_reference() {
    var request =
        new PaymentRequest(UUID.randomUUID(), new BigDecimal("1500.00"), "ZAR", "Test payment");

    var result = gateway.recordManualPayment(request);

    assertThat(result.success()).isTrue();
    assertThat(result.paymentReference()).startsWith("MANUAL-");
    assertThat(result.errorMessage()).isNull();
  }

  @Test
  void createCheckoutSession_returns_not_supported() {
    var request =
        new CheckoutRequest(
            UUID.randomUUID(),
            "INV-001",
            new BigDecimal("1500.00"),
            "ZAR",
            "customer@example.com",
            "Test Customer",
            "https://example.com/success",
            "https://example.com/cancel",
            Map.of());

    var result = gateway.createCheckoutSession(request);

    assertThat(result.success()).isFalse();
    assertThat(result.supported()).isFalse();
    assertThat(result.errorMessage()).contains("NoOp adapter");
  }

  @Test
  void handleWebhook_throws_unsupported_operation() {
    assertThatThrownBy(() -> gateway.handleWebhook("{}", Map.of()))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("webhooks");
  }

  @Test
  void queryPaymentStatus_throws_unsupported_operation() {
    assertThatThrownBy(() -> gateway.queryPaymentStatus("session-123"))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("payment status");
  }

  @Test
  void testConnection_returns_success() {
    var result = gateway.testConnection();

    assertThat(result.success()).isTrue();
    assertThat(result.providerName()).isEqualTo("noop");
    assertThat(result.errorMessage()).isNull();
  }
}
