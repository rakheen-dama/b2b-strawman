package io.b2mash.b2b.b2bstrawman.billing.payfast;

import io.b2mash.b2b.b2bstrawman.billing.BillingProperties;
import io.b2mash.b2b.b2bstrawman.billing.SubscribeResponse;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Platform-level PayFast service for subscription checkout form generation and cancellation.
 *
 * <p>This is intentionally separate from {@code PayFastPaymentGateway} (tenant BYOAK). Different
 * credential source ({@code @ConfigurationProperties} vs {@code SecretStore}), different payment
 * type (recurring subscription vs one-time invoice), different entities. See ADR-220.
 */
@Service
public class PlatformPayFastService {

  private static final Logger log = LoggerFactory.getLogger(PlatformPayFastService.class);

  private static final String SANDBOX_CHECKOUT_URL = "https://sandbox.payfast.co.za/eng/process";
  private static final String PRODUCTION_CHECKOUT_URL = "https://www.payfast.co.za/eng/process";
  private static final String SANDBOX_API_URL = "https://sandbox.payfast.co.za/subscriptions";
  private static final String PRODUCTION_API_URL = "https://api.payfast.co.za/subscriptions";

  private final PayFastBillingProperties payfastProperties;
  private final BillingProperties billingProperties;
  private final RestClient restClient;

  public PlatformPayFastService(
      PayFastBillingProperties payfastProperties, BillingProperties billingProperties) {
    this.payfastProperties = payfastProperties;
    this.billingProperties = billingProperties;
    this.restClient = RestClient.create();
  }

  /** Generates a PayFast checkout form with all required fields and MD5 signature. */
  public SubscribeResponse generateCheckoutForm(UUID organizationId) {
    var data = new LinkedHashMap<String, String>();
    data.put("merchant_id", payfastProperties.merchantId());
    data.put("merchant_key", payfastProperties.merchantKey());
    data.put("return_url", billingProperties.returnUrl());
    data.put("cancel_url", billingProperties.cancelUrl());
    data.put("notify_url", billingProperties.notifyUrl());
    data.put("amount", formatCentsToRands(billingProperties.monthlyPriceCents()));
    data.put("item_name", billingProperties.itemName());
    data.put("subscription_type", "1");
    data.put("recurring_amount", formatCentsToRands(billingProperties.monthlyPriceCents()));
    data.put("frequency", "3");
    data.put("cycles", "0");
    data.put("custom_str1", organizationId.toString());
    data.put("signature", generateSignature(data));

    String paymentUrl =
        payfastProperties.sandbox() ? SANDBOX_CHECKOUT_URL : PRODUCTION_CHECKOUT_URL;

    return new SubscribeResponse(paymentUrl, Map.copyOf(data));
  }

  /**
   * Cancels a PayFast subscription via their API.
   *
   * @param payfastToken the PayFast subscription token
   * @throws InvalidStateException if the cancellation API returns a non-2xx response
   */
  public void cancelPayFastSubscription(String payfastToken) {
    String baseUrl = payfastProperties.sandbox() ? SANDBOX_API_URL : PRODUCTION_API_URL;
    String url =
        UriComponentsBuilder.fromUriString(baseUrl)
            .pathSegment(payfastToken, "cancel")
            .toUriString();

    String timestamp = Instant.now().toString();
    String signature = generateCancellationSignature(timestamp);

    try {
      restClient
          .put()
          .uri(url)
          .header("merchant-id", payfastProperties.merchantId())
          .header("version", "v1")
          .header("timestamp", timestamp)
          .header("signature", signature)
          .retrieve()
          .toBodilessEntity();
    } catch (Exception e) {
      log.error("PayFast cancellation failed for token: {}", payfastToken, e);
      throw new InvalidStateException(
          "PayFast cancellation failed",
          "HTTP error from PayFast cancellation API: " + e.getMessage());
    }
  }

  /**
   * Generates an MD5 signature over form fields, sorted alphabetically by key. Appends passphrase
   * if configured.
   */
  private String generateSignature(Map<String, String> data) {
    var sorted = new TreeMap<>(data);
    sorted.remove("signature");

    var paramString =
        sorted.entrySet().stream()
            .map(
                e ->
                    URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(e.getValue().trim(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));

    String passphrase = payfastProperties.passphrase();
    if (passphrase != null && !passphrase.isEmpty()) {
      paramString += "&passphrase=" + URLEncoder.encode(passphrase.trim(), StandardCharsets.UTF_8);
    }

    return md5Hash(paramString);
  }

  /** Generates the MD5 signature for the cancellation API headers. */
  private String generateCancellationSignature(String timestamp) {
    var params = new TreeMap<String, String>();
    params.put("merchant-id", payfastProperties.merchantId());
    params.put("version", "v1");
    params.put("timestamp", timestamp);

    var paramString =
        params.entrySet().stream()
            .map(
                e ->
                    URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(e.getValue().trim(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));

    String passphrase = payfastProperties.passphrase();
    if (passphrase != null && !passphrase.isEmpty()) {
      paramString += "&passphrase=" + URLEncoder.encode(passphrase.trim(), StandardCharsets.UTF_8);
    }

    return md5Hash(paramString);
  }

  /** Formats cents to rands with 2 decimal places (e.g. 49900 -> "499.00"). */
  String formatCentsToRands(int cents) {
    return String.format("%d.%02d", cents / 100, cents % 100);
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
}
