package io.b2mash.b2b.b2bstrawman.integration.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegrationRepository;
import io.b2mash.b2b.b2bstrawman.integration.secret.SecretStore;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StripePaymentGatewayTest {

  @Mock private SecretStore secretStore;
  @Mock private OrgIntegrationRepository orgIntegrationRepository;

  private StripePaymentGateway gateway;

  @BeforeEach
  void setUp() {
    gateway = new StripePaymentGateway(secretStore, orgIntegrationRepository);
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
          .when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
          .thenReturn(mockSession);

      var result = gateway.createCheckoutSession(request);

      assertThat(result.success()).isTrue();
      assertThat(result.supported()).isTrue();
      assertThat(result.sessionId()).isEqualTo("cs_test_abc");
      assertThat(result.redirectUrl()).isEqualTo("https://checkout.stripe.com/pay/cs_test_abc");
      assertThat(result.errorMessage()).isNull();
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
