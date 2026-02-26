package io.b2mash.b2b.b2bstrawman.integration.payment;

import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationAdapter;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.secret.SecretStore;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * PayFast payment gateway adapter. Uses MD5 signature generation and redirect-based checkout (no
 * server-side session creation API). Credentials resolved per-tenant from SecretStore.
 */
@Component
@IntegrationAdapter(domain = IntegrationDomain.PAYMENT, slug = "payfast")
public class PayFastPaymentGateway implements PaymentGateway {

  private static final Logger log = LoggerFactory.getLogger(PayFastPaymentGateway.class);

  private static final String SANDBOX_URL = "https://sandbox.payfast.co.za/eng/process";
  private static final String PRODUCTION_URL = "https://www.payfast.co.za/eng/process";
  private static final String SANDBOX_VALIDATE_URL =
      "https://sandbox.payfast.co.za/eng/query/validate";
  private static final String PRODUCTION_VALIDATE_URL =
      "https://www.payfast.co.za/eng/query/validate";

  // PayFast ITN source IP range: 197.97.145.144/28 (16 addresses)
  private static final long PAYFAST_IP_RANGE_START = ipToLong("197.97.145.144");
  private static final long PAYFAST_IP_RANGE_END = ipToLong("197.97.145.159");

  private final SecretStore secretStore;
  private final RestClient restClient;

  @Value("${docteams.payfast.sandbox:true}")
  private boolean sandbox;

  @Value("${docteams.app.base-url:http://localhost:8080}")
  private String appBaseUrl;

  public PayFastPaymentGateway(SecretStore secretStore) {
    this.secretStore = secretStore;
    this.restClient = RestClient.create();
  }

  @Override
  public String providerId() {
    return "payfast";
  }

  @Override
  public CreateSessionResult createCheckoutSession(CheckoutRequest request) {
    try {
      var config = resolveConfig();
      var tenantSchema = request.metadata().get("tenantSchema");
      if (tenantSchema == null || tenantSchema.isBlank()) {
        throw new IllegalStateException("tenantSchema required in metadata");
      }

      var data = new LinkedHashMap<String, String>();
      data.put("merchant_id", config.merchantId());
      data.put("merchant_key", config.merchantKey());
      data.put("return_url", request.successUrl());
      data.put("cancel_url", request.cancelUrl());
      data.put("notify_url", appBaseUrl + "/api/webhooks/payment/payfast");
      data.put("amount", request.amount().setScale(2, RoundingMode.HALF_UP).toPlainString());
      data.put("item_name", "Invoice " + request.invoiceNumber());
      data.put("email_address", request.customerEmail());
      data.put("custom_str1", tenantSchema);
      data.put("custom_str2", request.invoiceId().toString());

      var signature = generateSignature(data, config.passphrase());
      data.put("signature", signature);

      var queryString =
          data.entrySet().stream()
              .map(
                  e ->
                      URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                          + "="
                          + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
              .collect(Collectors.joining("&"));

      var baseUrl = sandbox ? SANDBOX_URL : PRODUCTION_URL;
      var redirectUrl = baseUrl + "?" + queryString;
      var localSessionId = UUID.randomUUID().toString();

      return CreateSessionResult.success(localSessionId, redirectUrl);
    } catch (IllegalStateException e) {
      log.error("PayFast session creation failed: {}", e.getMessage(), e);
      return CreateSessionResult.failure(e.getMessage());
    }
  }

  @Override
  public WebhookResult handleWebhook(String payload, Map<String, String> headers) {
    try {
      var params = parseFormData(payload);
      if (params.isEmpty()) {
        log.warn("PayFast ITN: empty or null payload");
        return new WebhookResult(false, null, null, null, null, Map.of());
      }

      // Step 1: IP validation
      var sourceIp = getHeaderCaseInsensitive(headers, "X-Forwarded-For");
      if (!isPayFastIp(sourceIp)) {
        log.warn("PayFast ITN: request from non-PayFast IP: {}", sourceIp);
        return new WebhookResult(false, null, null, null, null, Map.of());
      }

      // Step 2: Signature verification using raw decoded values (no URL re-encoding)
      var config = resolveConfig();
      var paramsWithoutSig = new LinkedHashMap<>(params);
      paramsWithoutSig.remove("signature");

      var expectedSignature = generateVerificationSignature(paramsWithoutSig, config.passphrase());
      if (!expectedSignature.equals(params.get("signature"))) {
        log.warn("PayFast ITN signature verification failed");
        return new WebhookResult(false, null, null, null, null, Map.of());
      }

      // Step 3: Server confirmation
      if (!confirmWithPayFast(payload)) {
        log.warn("PayFast ITN server confirmation failed");
        return new WebhookResult(false, null, null, null, null, Map.of());
      }

      var status = mapPayFastStatus(params.get("payment_status"));
      return new WebhookResult(
          true,
          "payment." + status.name().toLowerCase(),
          params.get("pf_payment_id"),
          params.get("pf_payment_id"),
          status,
          Map.of(
              "tenantSchema", params.getOrDefault("custom_str1", ""),
              "invoiceId", params.getOrDefault("custom_str2", "")));
    } catch (Exception e) {
      log.error("PayFast webhook processing failed: {}", e.getMessage(), e);
      return new WebhookResult(false, null, null, null, null, Map.of());
    }
  }

  @Override
  public PaymentStatus queryPaymentStatus(String sessionId) {
    // PayFast has no session status API
    log.debug(
        "PayFast: no status query API available, returning PENDING for session {}", sessionId);
    return PaymentStatus.PENDING;
  }

  @Override
  public PaymentResult recordManualPayment(PaymentRequest request) {
    String reference = "MANUAL-" + UUID.randomUUID();
    log.info(
        "PayFast adapter: recorded manual payment for invoice={}, amount={} {}, reference={}",
        request.invoiceId(),
        request.amount(),
        request.currency(),
        reference);
    return new PaymentResult(true, reference, null);
  }

  @Override
  public ConnectionTestResult testConnection() {
    try {
      resolveConfig();
      return new ConnectionTestResult(
          true, "payfast", "Configuration saved. Send a test payment to verify.");
    } catch (IllegalStateException e) {
      return new ConnectionTestResult(false, "payfast", e.getMessage());
    }
  }

  /**
   * Checks if the given source IP (from X-Forwarded-For header) is in PayFast's ITN IP range
   * (197.97.145.144/28). Extracts the first IP from a comma-separated X-Forwarded-For value.
   * Package-private for testing.
   */
  boolean isPayFastIp(String sourceIp) {
    if (sourceIp == null || sourceIp.isBlank()) {
      return false;
    }
    // X-Forwarded-For may contain multiple IPs: "client, proxy1, proxy2"
    var ip = sourceIp.split(",")[0].trim();
    try {
      long ipLong = ipToLong(ip);
      return ipLong >= PAYFAST_IP_RANGE_START && ipLong <= PAYFAST_IP_RANGE_END;
    } catch (Exception e) {
      String safeIp = ip.replaceAll("[\\r\\n\\t]", "").substring(0, Math.min(ip.length(), 45));
      log.warn("PayFast ITN: invalid IP address '{}'", safeIp);
      return false;
    }
  }

  /**
   * Confirms the ITN with PayFast by POSTing the payload to their validation endpoint. Returns true
   * if response is "VALID". Package-private for testing.
   */
  boolean confirmWithPayFast(String payload) {
    try {
      var validateUrl = sandbox ? SANDBOX_VALIDATE_URL : PRODUCTION_VALIDATE_URL;
      var response =
          restClient
              .post()
              .uri(validateUrl)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(payload)
              .retrieve()
              .body(String.class);
      var valid = "VALID".equals(response);
      if (!valid) {
        log.warn("PayFast ITN server confirmation failed, response: {}", response);
      }
      return valid;
    } catch (Exception e) {
      log.error("PayFast ITN server confirmation request failed: {}", e.getMessage(), e);
      return false;
    }
  }

  private static String getHeaderCaseInsensitive(Map<String, String> headers, String name) {
    String value = headers.get(name);
    if (value != null) return value;
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      if (entry.getKey().equalsIgnoreCase(name)) {
        return entry.getValue();
      }
    }
    return null;
  }

  private static long ipToLong(String ip) {
    var parts = ip.split("\\.");
    if (parts.length != 4) {
      throw new IllegalArgumentException("Invalid IPv4 address: " + ip);
    }
    long result = 0;
    for (String part : parts) {
      int octet = Integer.parseInt(part);
      if (octet < 0 || octet > 255) {
        throw new IllegalArgumentException("Invalid IPv4 octet: " + part);
      }
      result = (result << 8) + octet;
    }
    return result;
  }

  /**
   * Generates a PayFast MD5 signature for outbound requests. Parameters are URL-encoded, sorted
   * alphabetically by key, joined with {@code &}, passphrase appended (if non-empty), then MD5
   * hashed.
   *
   * <p>Package-private for testing.
   */
  String generateSignature(Map<String, String> params, String passphrase) {
    var sorted = new TreeMap<>(params);
    var paramString =
        sorted.entrySet().stream()
            .map(
                e ->
                    URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(e.getValue().trim(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));

    if (passphrase != null && !passphrase.isEmpty()) {
      paramString += "&passphrase=" + URLEncoder.encode(passphrase.trim(), StandardCharsets.UTF_8);
    }

    return md5Hash(paramString);
  }

  /**
   * Generates a PayFast MD5 signature for ITN (webhook) verification. Uses raw decoded param values
   * without URL re-encoding, sorted alphabetically by key. PayFast ITN docs specify using the raw
   * values as-is.
   *
   * <p>Package-private for testing.
   */
  String generateVerificationSignature(Map<String, String> params, String passphrase) {
    var sorted = new TreeMap<>(params);
    var paramString =
        sorted.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue().trim())
            .collect(Collectors.joining("&"));

    if (passphrase != null && !passphrase.isEmpty()) {
      paramString += "&passphrase=" + passphrase.trim();
    }

    return md5Hash(paramString);
  }

  private String md5Hash(String input) {
    try {
      var md = MessageDigest.getInstance("MD5");
      var digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      var sb = new StringBuilder();
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("MD5 algorithm not available", e);
    }
  }

  /**
   * Parses URL-encoded form data (application/x-www-form-urlencoded) into a parameter map.
   * Package-private for testing.
   */
  Map<String, String> parseFormData(String body) {
    var result = new LinkedHashMap<String, String>();
    if (body == null || body.isBlank()) {
      return result;
    }
    for (String pair : body.split("&")) {
      var parts = pair.split("=", 2);
      var key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
      var value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
      result.put(key, value);
    }
    return result;
  }

  private PaymentStatus mapPayFastStatus(String payFastStatus) {
    if (payFastStatus == null) {
      return PaymentStatus.PENDING;
    }
    return switch (payFastStatus) {
      case "COMPLETE" -> PaymentStatus.COMPLETED;
      case "FAILED" -> PaymentStatus.FAILED;
      case "PENDING" -> PaymentStatus.PENDING;
      default -> {
        log.warn("PayFast: unknown payment_status '{}', defaulting to PENDING", payFastStatus);
        yield PaymentStatus.PENDING;
      }
    };
  }

  private PayFastConfig resolveConfig() {
    var merchantId = secretStore.retrieve("payment:payfast:merchant_id");
    var merchantKey = secretStore.retrieve("payment:payfast:merchant_key");
    var passphrase = secretStore.retrieve("payment:payfast:passphrase");

    if (merchantId == null || merchantId.isBlank()) {
      throw new IllegalStateException("PayFast merchant_id not configured");
    }
    if (merchantKey == null || merchantKey.isBlank()) {
      throw new IllegalStateException("PayFast merchant_key not configured");
    }
    if (passphrase == null || passphrase.isBlank()) {
      throw new IllegalStateException("PayFast passphrase not configured");
    }

    return new PayFastConfig(merchantId, merchantKey, passphrase);
  }

  private record PayFastConfig(String merchantId, String merchantKey, String passphrase) {}
}
