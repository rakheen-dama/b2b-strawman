package io.b2mash.b2b.b2bstrawman.integration.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Balance;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import io.b2mash.b2b.b2bstrawman.integration.secret.SecretStore;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StripePaymentGatewayTest {

  @Mock private SecretStore secretStore;

  @Captor private ArgumentCaptor<SessionCreateParams> paramsCaptor;
  @Captor private ArgumentCaptor<RequestOptions> optionsCaptor;

  private StripePaymentGateway gateway;

  @BeforeEach
  void setUp() {
    gateway = new StripePaymentGateway(secretStore);
  }

  // --- Session creation tests ---

  @Test
  void createCheckoutSession_builds_correct_params() {
    when(secretStore.retrieve("payment:stripe:api_key")).thenReturn("sk_test_123");
    var request = buildCheckoutRequest();

    try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
      var mockSession = mock(Session.class);
      when(mockSession.getId()).thenReturn("cs_test_abc");
      when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_test_abc");
      sessionMock
          .when(() -> Session.create(paramsCaptor.capture(), optionsCaptor.capture()))
          .thenReturn(mockSession);

      var result = gateway.createCheckoutSession(request);

      assertThat(result.success()).isTrue();
      assertThat(result.supported()).isTrue();
      assertThat(result.sessionId()).isEqualTo("cs_test_abc");
      assertThat(result.redirectUrl()).isEqualTo("https://checkout.stripe.com/pay/cs_test_abc");
      assertThat(result.errorMessage()).isNull();

      // Verify captured session params
      var params = paramsCaptor.getValue();
      assertThat(params.getMode()).isEqualTo(SessionCreateParams.Mode.PAYMENT);
      assertThat(params.getClientReferenceId()).isEqualTo(request.invoiceId().toString());
      assertThat(params.getCustomerEmail()).isEqualTo("customer@example.com");
      assertThat(params.getSuccessUrl()).contains("session_id={CHECKOUT_SESSION_ID}");
      assertThat(params.getCancelUrl()).isEqualTo("https://example.com/cancel");
      assertThat(params.getMetadata()).containsEntry("tenantSchema", "tenant_abc123");
      assertThat(params.getMetadata()).containsEntry("invoiceId", request.invoiceId().toString());

      var lineItem = params.getLineItems().get(0);
      assertThat(lineItem.getQuantity()).isEqualTo(1L);
      assertThat(lineItem.getPriceData().getCurrency()).isEqualTo("zar");
      assertThat(lineItem.getPriceData().getUnitAmount()).isEqualTo(150000L);
    }
  }

  @Test
  void createCheckoutSession_includes_metadata_tenantSchema() {
    when(secretStore.retrieve("payment:stripe:api_key")).thenReturn("sk_test_123");
    var invoiceId = UUID.randomUUID();
    var request =
        new CheckoutRequest(
            invoiceId,
            "INV-001",
            new BigDecimal("1500.00"),
            "ZAR",
            "customer@example.com",
            "Test Customer",
            "https://example.com/success",
            "https://example.com/cancel",
            Map.of("tenantSchema", "tenant_abc123"));

    try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
      var mockSession = mock(Session.class);
      when(mockSession.getId()).thenReturn("cs_test_abc");
      when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_test_abc");
      sessionMock
          .when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
          .thenAnswer(
              invocation -> {
                SessionCreateParams params = invocation.getArgument(0);
                assertThat(params.getMetadata()).containsEntry("tenantSchema", "tenant_abc123");
                assertThat(params.getMetadata()).containsEntry("invoiceId", invoiceId.toString());
                return mockSession;
              });

      var result = gateway.createCheckoutSession(request);

      assertThat(result.success()).isTrue();
    }
  }

  @Test
  void createCheckoutSession_converts_ZAR_to_cents() {
    when(secretStore.retrieve("payment:stripe:api_key")).thenReturn("sk_test_123");
    var request =
        new CheckoutRequest(
            UUID.randomUUID(),
            "INV-001",
            new BigDecimal("150.50"),
            "ZAR",
            "customer@example.com",
            "Test Customer",
            "https://example.com/success",
            "https://example.com/cancel",
            Map.of("tenantSchema", "tenant_abc123"));

    try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
      var mockSession = mock(Session.class);
      when(mockSession.getId()).thenReturn("cs_test_abc");
      when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_test_abc");
      sessionMock
          .when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
          .thenAnswer(
              invocation -> {
                SessionCreateParams params = invocation.getArgument(0);
                var lineItem = params.getLineItems().get(0);
                assertThat(lineItem.getPriceData().getUnitAmount()).isEqualTo(15050L);
                return mockSession;
              });

      gateway.createCheckoutSession(request);
    }
  }

  @Test
  void createCheckoutSession_handles_stripe_exception() {
    when(secretStore.retrieve("payment:stripe:api_key")).thenReturn("sk_test_123");
    var request = buildCheckoutRequest();

    try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
      sessionMock
          .when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
          .thenThrow(new StripeException("Invalid API key", null, null, 401) {});

      var result = gateway.createCheckoutSession(request);

      assertThat(result.success()).isFalse();
      assertThat(result.supported()).isTrue();
      assertThat(result.errorMessage()).contains("Invalid API key");
    }
  }

  // --- toSmallestUnit tests ---

  @Test
  void toSmallestUnit_ZAR() {
    assertThat(gateway.toSmallestUnit(new BigDecimal("150.50"), "ZAR")).isEqualTo(15050L);
  }

  @Test
  void toSmallestUnit_JPY() {
    assertThat(gateway.toSmallestUnit(new BigDecimal("1000"), "JPY")).isEqualTo(1000L);
  }

  @Test
  void toSmallestUnit_rounds_half_up_for_fractional_cents() {
    // 150.505 * 100 = 15050.5 → rounds to 15051
    assertThat(gateway.toSmallestUnit(new BigDecimal("150.505"), "ZAR")).isEqualTo(15051L);
    // 150.504 * 100 = 15050.4 → rounds to 15050
    assertThat(gateway.toSmallestUnit(new BigDecimal("150.504"), "ZAR")).isEqualTo(15050L);
  }

  @Test
  void toSmallestUnit_JPY_rounds_half_up() {
    assertThat(gateway.toSmallestUnit(new BigDecimal("1000.5"), "JPY")).isEqualTo(1001L);
  }

  // --- Webhook handling tests ---

  @Test
  void handleWebhook_completed_returns_payment_reference() {
    when(secretStore.retrieve("payment:stripe:webhook_secret")).thenReturn("whsec_test");

    try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
      var session = mock(Session.class);
      when(session.getId()).thenReturn("cs_test_abc");
      when(session.getPaymentIntent()).thenReturn("pi_test_123");
      when(session.getMetadata())
          .thenReturn(Map.of("tenantSchema", "tenant_abc123", "invoiceId", "invoice-uuid"));

      var deserializer = mock(EventDataObjectDeserializer.class);
      when(deserializer.getObject()).thenReturn(Optional.of(session));

      var event = mock(Event.class);
      when(event.getType()).thenReturn("checkout.session.completed");
      when(event.getDataObjectDeserializer()).thenReturn(deserializer);

      webhookMock
          .when(() -> Webhook.constructEvent("payload", "sig_header", "whsec_test"))
          .thenReturn(event);

      var result = gateway.handleWebhook("payload", Map.of("Stripe-Signature", "sig_header"));

      assertThat(result.verified()).isTrue();
      assertThat(result.eventType()).isEqualTo("checkout.session.completed");
      assertThat(result.sessionId()).isEqualTo("cs_test_abc");
      assertThat(result.paymentReference()).isEqualTo("pi_test_123");
      assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
      assertThat(result.metadata()).containsEntry("tenantSchema", "tenant_abc123");
    }
  }

  @Test
  void handleWebhook_expired_returns_expired_status() {
    when(secretStore.retrieve("payment:stripe:webhook_secret")).thenReturn("whsec_test");

    try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
      var session = mock(Session.class);
      when(session.getId()).thenReturn("cs_test_expired");
      when(session.getMetadata()).thenReturn(Map.of("tenantSchema", "tenant_abc123"));

      var deserializer = mock(EventDataObjectDeserializer.class);
      when(deserializer.getObject()).thenReturn(Optional.of(session));

      var event = mock(Event.class);
      when(event.getType()).thenReturn("checkout.session.expired");
      when(event.getDataObjectDeserializer()).thenReturn(deserializer);

      webhookMock
          .when(() -> Webhook.constructEvent("payload", "sig_header", "whsec_test"))
          .thenReturn(event);

      var result = gateway.handleWebhook("payload", Map.of("Stripe-Signature", "sig_header"));

      assertThat(result.verified()).isTrue();
      assertThat(result.eventType()).isEqualTo("checkout.session.expired");
      assertThat(result.status()).isEqualTo(PaymentStatus.EXPIRED);
      assertThat(result.paymentReference()).isNull();
    }
  }

  @Test
  void handleWebhook_invalid_signature_returns_unverified() {
    when(secretStore.retrieve("payment:stripe:webhook_secret")).thenReturn("whsec_test");

    try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
      webhookMock
          .when(() -> Webhook.constructEvent("payload", "bad_sig", "whsec_test"))
          .thenThrow(new SignatureVerificationException("Invalid signature", "bad_sig"));

      var result = gateway.handleWebhook("payload", Map.of("Stripe-Signature", "bad_sig"));

      assertThat(result.verified()).isFalse();
      assertThat(result.eventType()).isNull();
    }
  }

  @Test
  void handleWebhook_resolves_lowercase_signature_header() {
    when(secretStore.retrieve("payment:stripe:webhook_secret")).thenReturn("whsec_test");

    try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
      var event = mock(Event.class);
      when(event.getType()).thenReturn("payment_intent.succeeded");

      webhookMock
          .when(() -> Webhook.constructEvent("payload", "sig_lower", "whsec_test"))
          .thenReturn(event);

      // Use lowercase header key — should still be resolved
      var result = gateway.handleWebhook("payload", Map.of("stripe-signature", "sig_lower"));

      // Event is unhandled type, but signature was resolved (not a missing-header response)
      assertThat(result.eventType()).isEqualTo("payment_intent.succeeded");
    }
  }

  @Test
  void handleWebhook_unknown_event_type_returns_unverified() {
    when(secretStore.retrieve("payment:stripe:webhook_secret")).thenReturn("whsec_test");

    try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
      var event = mock(Event.class);
      when(event.getType()).thenReturn("payment_intent.succeeded");

      webhookMock
          .when(() -> Webhook.constructEvent("payload", "sig_header", "whsec_test"))
          .thenReturn(event);

      var result = gateway.handleWebhook("payload", Map.of("Stripe-Signature", "sig_header"));

      assertThat(result.verified()).isFalse();
      assertThat(result.eventType()).isEqualTo("payment_intent.succeeded");
    }
  }

  // --- queryPaymentStatus tests ---

  @Test
  void queryPaymentStatus_complete_returns_COMPLETED() {
    when(secretStore.retrieve("payment:stripe:api_key")).thenReturn("sk_test_123");

    try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
      var mockSession = mock(Session.class);
      when(mockSession.getStatus()).thenReturn("complete");
      sessionMock
          .when(() -> Session.retrieve(any(String.class), any(RequestOptions.class)))
          .thenReturn(mockSession);

      var result = gateway.queryPaymentStatus("cs_test_abc");

      assertThat(result).isEqualTo(PaymentStatus.COMPLETED);
    }
  }

  @Test
  void queryPaymentStatus_expired_returns_EXPIRED() {
    when(secretStore.retrieve("payment:stripe:api_key")).thenReturn("sk_test_123");

    try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
      var mockSession = mock(Session.class);
      when(mockSession.getStatus()).thenReturn("expired");
      sessionMock
          .when(() -> Session.retrieve(any(String.class), any(RequestOptions.class)))
          .thenReturn(mockSession);

      var result = gateway.queryPaymentStatus("cs_test_abc");

      assertThat(result).isEqualTo(PaymentStatus.EXPIRED);
    }
  }

  @Test
  void queryPaymentStatus_open_returns_PENDING() {
    when(secretStore.retrieve("payment:stripe:api_key")).thenReturn("sk_test_123");

    try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
      var mockSession = mock(Session.class);
      when(mockSession.getStatus()).thenReturn("open");
      sessionMock
          .when(() -> Session.retrieve(any(String.class), any(RequestOptions.class)))
          .thenReturn(mockSession);

      var result = gateway.queryPaymentStatus("cs_test_abc");

      assertThat(result).isEqualTo(PaymentStatus.PENDING);
    }
  }

  @Test
  void queryPaymentStatus_stripeException_returns_FAILED() {
    when(secretStore.retrieve("payment:stripe:api_key")).thenReturn("sk_test_123");

    try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
      sessionMock
          .when(() -> Session.retrieve(any(String.class), any(RequestOptions.class)))
          .thenThrow(new StripeException("Not found", null, null, 404) {});

      var result = gateway.queryPaymentStatus("cs_test_invalid");

      assertThat(result).isEqualTo(PaymentStatus.FAILED);
    }
  }

  // --- testConnection tests ---

  @Test
  void testConnection_success() {
    when(secretStore.retrieve("payment:stripe:api_key")).thenReturn("sk_test_123");

    try (MockedStatic<Balance> balanceMock = mockStatic(Balance.class)) {
      var mockBalance = mock(Balance.class);
      balanceMock.when(() -> Balance.retrieve(any(RequestOptions.class))).thenReturn(mockBalance);

      var result = gateway.testConnection();

      assertThat(result.success()).isTrue();
      assertThat(result.providerName()).isEqualTo("stripe");
      assertThat(result.errorMessage()).isNull();
    }
  }

  @Test
  void testConnection_failure() {
    when(secretStore.retrieve("payment:stripe:api_key")).thenReturn("sk_test_123");

    try (MockedStatic<Balance> balanceMock = mockStatic(Balance.class)) {
      balanceMock
          .when(() -> Balance.retrieve(any(RequestOptions.class)))
          .thenThrow(new StripeException("Invalid API Key", null, null, 401) {});

      var result = gateway.testConnection();

      assertThat(result.success()).isFalse();
      assertThat(result.providerName()).isEqualTo("stripe");
      assertThat(result.errorMessage()).contains("Invalid API Key");
    }
  }

  // --- expireSession tests ---

  @Test
  void expireSession_calls_stripe() throws StripeException {
    when(secretStore.retrieve("payment:stripe:api_key")).thenReturn("sk_test_123");

    try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
      var mockSession = mock(Session.class);
      sessionMock
          .when(() -> Session.retrieve(any(String.class), any(RequestOptions.class)))
          .thenReturn(mockSession);

      gateway.expireSession("cs_test_abc");

      verify(mockSession).expire(any(RequestOptions.class));
    }
  }

  @Test
  void expireSession_swallows_exception() {
    when(secretStore.retrieve("payment:stripe:api_key")).thenReturn("sk_test_123");

    try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
      sessionMock
          .when(() -> Session.retrieve(any(String.class), any(RequestOptions.class)))
          .thenThrow(new StripeException("Session not found", null, null, 404) {});

      // Should NOT throw
      gateway.expireSession("cs_test_invalid");
    }
  }

  // --- recordManualPayment tests ---

  @Test
  void recordManualPayment_returns_success() {
    var request =
        new PaymentRequest(UUID.randomUUID(), new BigDecimal("500.00"), "ZAR", "Test payment");

    var result = gateway.recordManualPayment(request);

    assertThat(result.success()).isTrue();
    assertThat(result.paymentReference()).startsWith("MANUAL-");
    assertThat(result.errorMessage()).isNull();
  }

  // --- Helpers ---

  private CheckoutRequest buildCheckoutRequest() {
    return new CheckoutRequest(
        UUID.randomUUID(),
        "INV-001",
        new BigDecimal("1500.00"),
        "ZAR",
        "customer@example.com",
        "Test Customer",
        "https://example.com/success",
        "https://example.com/cancel",
        Map.of("tenantSchema", "tenant_abc123"));
  }
}
