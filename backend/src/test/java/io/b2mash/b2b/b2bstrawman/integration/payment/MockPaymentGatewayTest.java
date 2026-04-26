package io.b2mash.b2b.b2bstrawman.integration.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for the dev-only {@link MockPaymentGateway}. Verifies session creation, webhook
 * parsing, status query, and the {@link IntegrationAdapter} annotation contract.
 */
class MockPaymentGatewayTest {

  private static final String APP_BASE_URL = "http://localhost:8080";
  private final ObjectMapper objectMapper = new ObjectMapper();

  private MockPaymentGateway gateway;

  @BeforeEach
  void setUp() {
    gateway = new MockPaymentGateway(objectMapper, APP_BASE_URL);
  }

  @Test
  void providerId_is_mock() {
    assertThat(gateway.providerId()).isEqualTo("mock");
  }

  @Test
  void createCheckoutSession_returns_success_with_mock_session_prefix() {
    var invoiceId = UUID.randomUUID();
    var request =
        new CheckoutRequest(
            invoiceId,
            "INV-0001",
            new BigDecimal("1234.56"),
            "ZAR",
            "customer@example.com",
            "Test Customer",
            "http://localhost:3002/invoices/" + invoiceId + "/payment-success",
            "http://localhost:3002/invoices/" + invoiceId + "/payment-cancelled",
            Map.of("tenantSchema", "tenant_abcdef012345"));

    var result = gateway.createCheckoutSession(request);

    assertThat(result.success()).isTrue();
    assertThat(result.supported()).isTrue();
    assertThat(result.sessionId()).startsWith("MOCK-SESS-");
    assertThat(result.redirectUrl()).startsWith(APP_BASE_URL + "/portal/dev/mock-payment");
    assertThat(result.redirectUrl()).contains("sessionId=MOCK-SESS-");
    assertThat(result.redirectUrl()).contains("invoiceId=" + invoiceId);
    assertThat(result.redirectUrl()).contains("amount=1234.56");
    assertThat(result.redirectUrl()).contains("currency=ZAR");
    assertThat(result.redirectUrl()).contains("returnUrl=");
  }

  @Test
  void handleWebhook_paid_payload_returns_completed_with_metadata() {
    var sessionId = "MOCK-SESS-" + UUID.randomUUID();
    var invoiceId = UUID.randomUUID();
    var payload =
        "{\"sessionId\":\""
            + sessionId
            + "\",\"status\":\"PAID\",\"reference\":\"REF-1\",\"tenantSchema\":\"tenant_abcdef012345\",\"invoiceId\":\""
            + invoiceId
            + "\"}";

    var result = gateway.handleWebhook(payload, Map.of());

    assertThat(result.verified()).isTrue();
    assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(result.sessionId()).isEqualTo(sessionId);
    assertThat(result.paymentReference()).isEqualTo("REF-1");
    assertThat(result.metadata()).containsEntry("provider", "mock");
    assertThat(result.metadata()).containsEntry("tenantSchema", "tenant_abcdef012345");
    assertThat(result.metadata()).containsEntry("invoiceId", invoiceId.toString());
  }

  @Test
  void handleWebhook_failed_payload_returns_failed_status() {
    var sessionId = "MOCK-SESS-" + UUID.randomUUID();
    var payload =
        "{\"sessionId\":\""
            + sessionId
            + "\",\"status\":\"FAILED\",\"tenantSchema\":\"tenant_abcdef012345\",\"invoiceId\":\""
            + UUID.randomUUID()
            + "\"}";

    var result = gateway.handleWebhook(payload, Map.of());

    assertThat(result.verified()).isTrue();
    assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
  }

  @Test
  void handleWebhook_invalid_payload_returns_unverified() {
    var result = gateway.handleWebhook("not-json{", Map.of());
    assertThat(result.verified()).isFalse();
  }

  @Test
  void queryPaymentStatus_returns_paid_after_webhook_for_same_session() {
    var sessionId = "MOCK-SESS-" + UUID.randomUUID();
    var invoiceId = UUID.randomUUID();
    var payload =
        "{\"sessionId\":\""
            + sessionId
            + "\",\"status\":\"PAID\",\"tenantSchema\":\"tenant_abcdef012345\",\"invoiceId\":\""
            + invoiceId
            + "\"}";

    gateway.handleWebhook(payload, Map.of());

    assertThat(gateway.queryPaymentStatus(sessionId)).isEqualTo(PaymentStatus.COMPLETED);
  }

  @Test
  void queryPaymentStatus_unknown_session_returns_pending() {
    assertThat(gateway.queryPaymentStatus("MOCK-SESS-unknown")).isEqualTo(PaymentStatus.PENDING);
    assertThat(gateway.queryPaymentStatus("not-a-mock-session")).isEqualTo(PaymentStatus.PENDING);
    assertThat(gateway.queryPaymentStatus(null)).isEqualTo(PaymentStatus.PENDING);
  }

  @Test
  void recordManualPayment_returns_mock_manual_reference() {
    var request =
        new PaymentRequest(UUID.randomUUID(), new BigDecimal("100.00"), "ZAR", "Test payment");
    var result = gateway.recordManualPayment(request);

    assertThat(result.success()).isTrue();
    assertThat(result.paymentReference()).startsWith("MOCK-MANUAL-");
    assertThat(result.errorMessage()).isNull();
  }

  @Test
  void testConnection_returns_success() {
    var result = gateway.testConnection();
    assertThat(result.success()).isTrue();
    assertThat(result.providerName()).isEqualTo("mock");
  }

  @Test
  void integrationAdapter_annotation_present_with_mock_slug() {
    var annotation =
        MockPaymentGateway.class.getAnnotation(
            io.b2mash.b2b.b2bstrawman.integration.IntegrationAdapter.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.slug()).isEqualTo("mock");
    assertThat(annotation.domain())
        .isEqualTo(io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain.PAYMENT);
  }

  @Test
  void profile_annotation_excludes_prod() {
    var profile =
        MockPaymentGateway.class.getAnnotation(
            org.springframework.context.annotation.Profile.class);
    assertThat(profile).isNotNull();
    assertThat(profile.value()).containsExactlyInAnyOrder("local", "dev", "keycloak", "test");
    assertThat(profile.value()).doesNotContain("prod");
  }

  /**
   * Regression for GAP-L-66: the mock checkout page is served by {@code MockPaymentController} on
   * the backend (port 8080), but earlier dev/QA cycles relied on {@code docteams.app.base-url}
   * (frontend, port 3000) and "Pay Now" links 404'd. Each profile that activates the mock gateway
   * must explicitly point {@code docteams.payment.mock.checkout-base-url} at the backend host.
   */
  @Test
  void localProfile_overrides_checkoutBaseUrl_to_backendHost() {
    assertThat(loadProperty("application-local.yml", "docteams.payment.mock.checkout-base-url"))
        .isEqualTo("http://localhost:8080");
  }

  @Test
  void devProfile_overrides_checkoutBaseUrl_to_backendHost() {
    // application-dev.yml uses an env-var override with localhost:8080 as the default.
    assertThat(loadProperty("application-dev.yml", "docteams.payment.mock.checkout-base-url"))
        .isEqualTo("http://localhost:8080");
  }

  @Test
  void keycloakProfile_overrides_checkoutBaseUrl_to_backendHost() {
    assertThat(loadProperty("application-keycloak.yml", "docteams.payment.mock.checkout-base-url"))
        .isEqualTo("http://localhost:8080");
  }

  /**
   * Loads a profile yml from the classpath and resolves a single property, including {@code
   * ${VAR:default}} placeholders. Avoids spinning up a full Spring context — the {@link
   * MockPaymentGateway} constructor is the only consumer of this property.
   */
  private static String loadProperty(String yamlResource, String propertyKey) {
    var factory = new YamlPropertiesFactoryBean();
    factory.setResources(new ClassPathResource(yamlResource));
    Properties props = factory.getObject();
    assertThat(props).as("yml resource %s loaded", yamlResource).isNotNull();
    String raw = props.getProperty(propertyKey);
    assertThat(raw).as("property %s present in %s", propertyKey, yamlResource).isNotNull();
    return resolvePlaceholders(raw);
  }

  /**
   * Minimal {@code ${VAR:default}} resolver — Spring's PropertyPlaceholderHelper requires a
   * PropertyResolver, and we don't want to bootstrap a Spring context here. Returns the default
   * portion when the env var is unset, the env var value when it is set.
   */
  private static String resolvePlaceholders(String value) {
    if (value == null || !value.contains("${")) {
      return value;
    }
    int start = value.indexOf("${");
    int end = value.indexOf('}', start);
    if (end < 0) {
      return value;
    }
    String spec = value.substring(start + 2, end);
    int colon = spec.indexOf(':');
    String name = colon < 0 ? spec : spec.substring(0, colon);
    String defaultValue = colon < 0 ? "" : spec.substring(colon + 1);
    String envValue = System.getenv(name);
    String resolved = envValue != null ? envValue : defaultValue;
    return value.substring(0, start) + resolved + value.substring(end + 1);
  }
}
