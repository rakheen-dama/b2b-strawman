package io.b2mash.b2b.b2bstrawman.integration.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class PayFastPaymentGatewayTest {

  private static final String PAYFAST_IP = "197.97.145.150";

  @Mock private SecretStore secretStore;
  @Mock private RestClient restClient;
  @Mock private RestClient.RequestBodyUriSpec requestBodyUriSpec;
  @Mock private RestClient.RequestBodySpec requestBodySpec;
  @Mock private RestClient.ResponseSpec responseSpec;

  private PayFastPaymentGateway gateway;

  @BeforeEach
  void setUp() throws Exception {
    gateway = new PayFastPaymentGateway(secretStore);
    setField(gateway, "sandbox", true);
    setField(gateway, "appBaseUrl", "http://localhost:8080");
    setField(gateway, "restClient", restClient);
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

  // --- IP validation tests ---

  @Test
  void isPayFastIp_valid_ip_in_range() {
    // Test all 16 IPs in the 197.97.145.144/28 range
    for (int i = 144; i <= 159; i++) {
      assertThat(gateway.isPayFastIp("197.97.145." + i))
          .as("IP 197.97.145.%d should be valid", i)
          .isTrue();
    }
  }

  @Test
  void isPayFastIp_invalid_ip_outside_range() {
    assertThat(gateway.isPayFastIp("197.97.145.143")).isFalse();
    assertThat(gateway.isPayFastIp("197.97.145.160")).isFalse();
    assertThat(gateway.isPayFastIp("10.0.0.1")).isFalse();
  }

  @Test
  void isPayFastIp_extracts_first_from_forwarded_for() {
    // X-Forwarded-For with valid PayFast IP as first entry
    assertThat(gateway.isPayFastIp("197.97.145.150, 10.0.0.1")).isTrue();
    // X-Forwarded-For with non-PayFast IP as first entry
    assertThat(gateway.isPayFastIp("10.0.0.1, 197.97.145.150")).isFalse();
  }

  @Test
  void isPayFastIp_null_or_blank_returns_false() {
    assertThat(gateway.isPayFastIp(null)).isFalse();
    assertThat(gateway.isPayFastIp("")).isFalse();
    assertThat(gateway.isPayFastIp("   ")).isFalse();
  }

  // --- Server confirmation tests ---

  @Test
  void confirmWithPayFast_valid_response() {
    mockServerConfirmation("VALID");

    assertThat(gateway.confirmWithPayFast("some=payload")).isTrue();
  }

  @Test
  void confirmWithPayFast_invalid_response() {
    mockServerConfirmation("INVALID");

    assertThat(gateway.confirmWithPayFast("some=payload")).isFalse();
  }

  // --- Webhook handling tests ---

  @Test
  void handleWebhook_valid_signature_returns_verified() {
    setupMerchantSecrets();
    mockServerConfirmation("VALID");

    var params = new LinkedHashMap<String, String>();
    params.put("pf_payment_id", "1234567");
    params.put("payment_status", "COMPLETE");
    params.put("custom_str1", "tenant_abc123");
    params.put("custom_str2", "invoice-uuid-123");

    var signature = gateway.generateVerificationSignature(params, "test_passphrase");
    params.put("signature", signature);

    var payload = buildFormPayload(params);

    var result = gateway.handleWebhook(payload, Map.of("X-Forwarded-For", PAYFAST_IP));

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

    var result = gateway.handleWebhook(payload, Map.of("X-Forwarded-For", PAYFAST_IP));

    assertThat(result.verified()).isFalse();
    assertThat(result.eventType()).isNull();
  }

  @Test
  void handleWebhook_maps_COMPLETE_to_COMPLETED() {
    setupMerchantSecrets();
    mockServerConfirmation("VALID");

    var params = new LinkedHashMap<String, String>();
    params.put("pf_payment_id", "1234567");
    params.put("payment_status", "COMPLETE");
    params.put("custom_str1", "tenant_abc123");
    params.put("custom_str2", "invoice-uuid-123");

    var signature = gateway.generateVerificationSignature(params, "test_passphrase");
    params.put("signature", signature);

    var payload = buildFormPayload(params);

    var result = gateway.handleWebhook(payload, Map.of("X-Forwarded-For", PAYFAST_IP));

    assertThat(result.verified()).isTrue();
    assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(result.eventType()).isEqualTo("payment.completed");
  }

  @Test
  void handleWebhook_rejects_non_payfast_ip() {
    var params = new LinkedHashMap<String, String>();
    params.put("pf_payment_id", "1234567");
    params.put("payment_status", "COMPLETE");
    params.put("custom_str1", "tenant_abc123");
    params.put("custom_str2", "invoice-uuid-123");

    var signature = gateway.generateVerificationSignature(params, "test_passphrase");
    params.put("signature", signature);

    var payload = buildFormPayload(params);

    // Non-PayFast IP
    var result = gateway.handleWebhook(payload, Map.of("X-Forwarded-For", "10.0.0.1"));

    assertThat(result.verified()).isFalse();
    assertThat(result.eventType()).isNull();
  }

  @Test
  void handleWebhook_rejects_failed_server_confirmation() {
    setupMerchantSecrets();
    mockServerConfirmation("INVALID");

    var params = new LinkedHashMap<String, String>();
    params.put("pf_payment_id", "1234567");
    params.put("payment_status", "COMPLETE");
    params.put("custom_str1", "tenant_abc123");
    params.put("custom_str2", "invoice-uuid-123");

    var signature = gateway.generateVerificationSignature(params, "test_passphrase");
    params.put("signature", signature);

    var payload = buildFormPayload(params);

    var result = gateway.handleWebhook(payload, Map.of("X-Forwarded-For", PAYFAST_IP));

    assertThat(result.verified()).isFalse();
    assertThat(result.eventType()).isNull();
  }

  @Test
  void handleWebhook_full_valid_flow() {
    setupMerchantSecrets();
    mockServerConfirmation("VALID");

    var params = new LinkedHashMap<String, String>();
    params.put("pf_payment_id", "9999999");
    params.put("payment_status", "COMPLETE");
    params.put("custom_str1", "tenant_xyz");
    params.put("custom_str2", "invoice-456");

    var signature = gateway.generateVerificationSignature(params, "test_passphrase");
    params.put("signature", signature);

    var payload = buildFormPayload(params);

    var result = gateway.handleWebhook(payload, Map.of("X-Forwarded-For", PAYFAST_IP));

    assertThat(result.verified()).isTrue();
    assertThat(result.sessionId()).isEqualTo("9999999");
    assertThat(result.paymentReference()).isEqualTo("9999999");
    assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(result.metadata()).containsEntry("tenantSchema", "tenant_xyz");
    assertThat(result.metadata()).containsEntry("invoiceId", "invoice-456");
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
            Map.of());

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

  private void mockServerConfirmation(String response) {
    when(restClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
    when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
    when(requestBodySpec.body(anyString())).thenReturn(requestBodySpec);
    when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(String.class)).thenReturn(response);
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
