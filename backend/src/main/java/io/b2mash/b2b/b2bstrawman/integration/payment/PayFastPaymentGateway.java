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
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

  private final SecretStore secretStore;

  @Value("${docteams.payfast.sandbox:true}")
  private boolean sandbox;

  @Value("${docteams.app.base-url:http://localhost:8080}")
  private String appBaseUrl;

  public PayFastPaymentGateway(SecretStore secretStore) {
    this.secretStore = secretStore;
  }

  @Override
  public String providerId() {
    return "payfast";
  }

  @Override
  public CreateSessionResult createCheckoutSession(CheckoutRequest request) {
    try {
      var config = resolveConfig();
      var tenantSchema =
          Objects.requireNonNull(
              request.metadata().get("tenantSchema"), "tenantSchema required in metadata");

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
    } catch (Exception e) {
      log.error("PayFast session creation failed: {}", e.getMessage(), e);
      return CreateSessionResult.failure(e.getMessage());
    }
  }

  @Override
  public WebhookResult handleWebhook(String payload, Map<String, String> headers) {
    try {
      var params = parseFormData(payload);

      // Step 2: Verify signature (177A scope)
      var config = resolveConfig();
      var paramsWithoutSig = new LinkedHashMap<>(params);
      paramsWithoutSig.remove("signature");

      var expectedSignature = generateSignature(paramsWithoutSig, config.passphrase());
      if (!expectedSignature.equals(params.get("signature"))) {
        log.warn("PayFast ITN signature verification failed");
        return new WebhookResult(false, null, null, null, null, Map.of());
      }

      var status = mapPayFastStatus(params.get("payment_status"));
      return new WebhookResult(
          true,
          "payment." + status.name().toLowerCase(),
          params.get("custom_str2"),
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
    return new ConnectionTestResult(
        true, "payfast", "Configuration saved. Send a test payment to verify.");
  }

  /**
   * Generates a PayFast MD5 signature. Parameters are URL-encoded, sorted alphabetically by key,
   * joined with {@code &}, passphrase appended (if non-empty), then MD5 hashed.
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

    try {
      var md = MessageDigest.getInstance("MD5");
      var digest = md.digest(paramString.getBytes(StandardCharsets.UTF_8));
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
    return new PayFastConfig(
        secretStore.retrieve("payment:payfast:merchant_id"),
        secretStore.retrieve("payment:payfast:merchant_key"),
        secretStore.retrieve("payment:payfast:passphrase"));
  }

  private record PayFastConfig(String merchantId, String merchantKey, String passphrase) {}
}
