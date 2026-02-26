package io.b2mash.b2b.b2bstrawman.integration.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.integration.secret.SecretStore;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayFastPaymentGatewayTest {

  @Mock private SecretStore secretStore;

  private PayFastPaymentGateway gateway;

  @BeforeEach
  void setUp() throws Exception {
    gateway = new PayFastPaymentGateway(secretStore);
    setField(gateway, "sandbox", true);
    setField(gateway, "appBaseUrl", "http://localhost:8080");
  }

  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  // --- Signature generation tests ---

  @Test
  void generateSignature_matches_known_vector() {
    var params = new LinkedHashMap<String, String>();
    params.put("merchant_id", "10000100");
    params.put("merchant_key", "46f0cd694581a");
    params.put("amount", "100.00");
    params.put("item_name", "Test Item");

    var passphrase = "jt7NOE43FZPn";
    var signature = gateway.generateSignature(params, passphrase);

    // Compute expected: alphabetical order -> amount, item_name, merchant_id, merchant_key
    // URL-encoded:
    // amount=100.00&item_name=Test+Item&merchant_id=10000100&merchant_key=46f0cd694581a&passphrase=jt7NOE43FZPn
    var expected =
        md5(
            "amount=100.00&item_name=Test+Item&merchant_id=10000100&merchant_key=46f0cd694581a&passphrase=jt7NOE43FZPn");

    assertThat(signature).isEqualTo(expected);
  }

  @Test
  void generateSignature_url_encodes_special_chars() {
    var params = new LinkedHashMap<String, String>();
    params.put("item_name", "Invoice #001 & extras");
    params.put("email_address", "user@example.com");

    var signature = gateway.generateSignature(params, "secret");

    // Params sorted: email_address, item_name
    var expected =
        md5(
            "email_address="
                + URLEncoder.encode("user@example.com", StandardCharsets.UTF_8)
                + "&item_name="
                + URLEncoder.encode("Invoice #001 & extras", StandardCharsets.UTF_8)
                + "&passphrase="
                + URLEncoder.encode("secret", StandardCharsets.UTF_8));

    assertThat(signature).isEqualTo(expected);
  }

  @Test
  void generateSignature_alphabetical_order() {
    var params = new LinkedHashMap<String, String>();
    params.put("z_field", "last");
    params.put("a_field", "first");
    params.put("m_field", "middle");

    var signature = gateway.generateSignature(params, "pass");

    // Should be sorted: a_field, m_field, z_field
    var expected = md5("a_field=first&m_field=middle&z_field=last&passphrase=pass");

    assertThat(signature).isEqualTo(expected);
  }

  // --- Session creation tests ---

  @Test
  void createCheckoutSession_includes_custom_str1_tenant() {
    setupMerchantSecrets();
    var request = buildCheckoutRequest();

    var result = gateway.createCheckoutSession(request);

    assertThat(result.success()).isTrue();
    assertThat(result.supported()).isTrue();
    assertThat(result.redirectUrl()).contains("custom_str1=tenant_abc123");
  }

  @Test
  void createCheckoutSession_uses_sandbox_url() {
    setupMerchantSecrets();
    var request = buildCheckoutRequest();

    var result = gateway.createCheckoutSession(request);

    assertThat(result.success()).isTrue();
    // Default sandbox=true (set by @Value default)
    assertThat(result.redirectUrl()).startsWith("https://sandbox.payfast.co.za/eng/process?");
  }

  @Test
  void createCheckoutSession_formats_amount_two_decimals() {
    setupMerchantSecrets();
    var request =
        new CheckoutRequest(
            UUID.randomUUID(),
            "INV-002",
            new BigDecimal("1500"),
            "ZAR",
            "customer@example.com",
            "Test Customer",
            "https://example.com/success",
            "https://example.com/cancel",
            Map.of("tenantSchema", "tenant_abc123"));

    var result = gateway.createCheckoutSession(request);

    assertThat(result.success()).isTrue();
    assertThat(result.redirectUrl()).contains("amount=1500.00");
  }

  // --- Webhook handling tests ---

  @Test
  void handleWebhook_valid_signature_returns_verified() {
    setupMerchantSecrets();

    var params = new LinkedHashMap<String, String>();
    params.put("pf_payment_id", "1234567");
    params.put("payment_status", "COMPLETE");
    params.put("custom_str1", "tenant_abc123");
    params.put("custom_str2", "invoice-uuid-123");

    var signature = gateway.generateVerificationSignature(params, "test_passphrase");
    params.put("signature", signature);

    var payload = buildFormPayload(params);

    var result = gateway.handleWebhook(payload, Map.of());

    assertThat(result.verified()).isTrue();
    assertThat(result.sessionId()).isEqualTo("1234567");
    assertThat(result.paymentReference()).isEqualTo("1234567");
    assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(result.metadata()).containsEntry("tenantSchema", "tenant_abc123");
    assertThat(result.metadata()).containsEntry("invoiceId", "invoice-uuid-123");
  }

  @Test
  void handleWebhook_invalid_signature_returns_unverified() {
    setupMerchantSecrets();

    var params = new LinkedHashMap<String, String>();
    params.put("pf_payment_id", "1234567");
    params.put("payment_status", "COMPLETE");
    params.put("custom_str1", "tenant_abc123");
    params.put("custom_str2", "invoice-uuid-123");
    params.put("signature", "definitely_wrong_signature");

    var payload = buildFormPayload(params);

    var result = gateway.handleWebhook(payload, Map.of());

    assertThat(result.verified()).isFalse();
    assertThat(result.eventType()).isNull();
  }

  @Test
  void handleWebhook_maps_COMPLETE_to_COMPLETED() {
    setupMerchantSecrets();

    var params = new LinkedHashMap<String, String>();
    params.put("pf_payment_id", "1234567");
    params.put("payment_status", "COMPLETE");
    params.put("custom_str1", "tenant_abc123");
    params.put("custom_str2", "invoice-uuid-123");

    var signature = gateway.generateVerificationSignature(params, "test_passphrase");
    params.put("signature", signature);

    var payload = buildFormPayload(params);

    var result = gateway.handleWebhook(payload, Map.of());

    assertThat(result.verified()).isTrue();
    assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(result.eventType()).isEqualTo("payment.completed");
  }

  @Test
  void recordManualPayment_returns_success() {
    var request =
        new PaymentRequest(UUID.randomUUID(), new BigDecimal("500.00"), "ZAR", "Test payment");

    var result = gateway.recordManualPayment(request);

    assertThat(result.success()).isTrue();
    assertThat(result.paymentReference()).startsWith("MANUAL-");
    assertThat(result.errorMessage()).isNull();
  }

  @Test
  void testConnection_returns_advisory_message() {
    setupMerchantSecrets();

    var result = gateway.testConnection();

    assertThat(result.success()).isTrue();
    assertThat(result.providerName()).isEqualTo("payfast");
    assertThat(result.errorMessage())
        .isEqualTo("Configuration saved. Send a test payment to verify.");
  }

  @Test
  void testConnection_fails_when_config_missing() {
    // secretStore returns null for all keys
    var result = gateway.testConnection();

    assertThat(result.success()).isFalse();
    assertThat(result.providerName()).isEqualTo("payfast");
    assertThat(result.errorMessage()).contains("merchant_id");
  }

  @Test
  void createCheckoutSession_fails_when_tenantSchema_missing() {
    setupMerchantSecrets();
    var request =
        new CheckoutRequest(
            UUID.randomUUID(),
            "INV-003",
            new BigDecimal("500.00"),
            "ZAR",
            "customer@example.com",
            "Test Customer",
            "https://example.com/success",
            "https://example.com/cancel",
            Map.of()); // no tenantSchema

    var result = gateway.createCheckoutSession(request);

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).contains("tenantSchema");
  }

  @Test
  void handleWebhook_empty_payload_returns_unverified() {
    var result = gateway.handleWebhook("", Map.of());

    assertThat(result.verified()).isFalse();
    assertThat(result.eventType()).isNull();
  }

  @Test
  void handleWebhook_null_payload_returns_unverified() {
    var result = gateway.handleWebhook(null, Map.of());

    assertThat(result.verified()).isFalse();
    assertThat(result.eventType()).isNull();
  }

  // --- Helpers ---

  private void setupMerchantSecrets() {
    when(secretStore.retrieve("payment:payfast:merchant_id")).thenReturn("10000100");
    when(secretStore.retrieve("payment:payfast:merchant_key")).thenReturn("46f0cd694581a");
    when(secretStore.retrieve("payment:payfast:passphrase")).thenReturn("test_passphrase");
  }

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

  private String buildFormPayload(Map<String, String> params) {
    return params.entrySet().stream()
        .map(
            e ->
                URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
  }

  private static String md5(String input) {
    try {
      var md = MessageDigest.getInstance("MD5");
      var digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      var sb = new StringBuilder();
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
