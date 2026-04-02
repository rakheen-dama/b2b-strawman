package io.b2mash.b2b.b2bstrawman.billing;

import io.b2mash.b2b.b2bstrawman.billing.payfast.PayFastBillingProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processes PayFast subscription ITN (Instant Transaction Notification) callbacks. Validates source
 * IP and signature before processing payment status changes. Never throws exceptions — PayFast
 * requires HTTP 200 always.
 */
@Service
public class SubscriptionItnService {

  private static final Logger log = LoggerFactory.getLogger(SubscriptionItnService.class);

  // PayFast ITN source IP range: 197.97.145.144/28 (16 addresses)
  private static final long PAYFAST_IP_RANGE_START = ipToLong("197.97.145.144");
  private static final long PAYFAST_IP_RANGE_END = ipToLong("197.97.145.159");

  private final SubscriptionRepository subscriptionRepository;
  private final SubscriptionPaymentRepository subscriptionPaymentRepository;
  private final PayFastBillingProperties payfastProperties;
  private final BillingProperties billingProperties;

  public SubscriptionItnService(
      SubscriptionRepository subscriptionRepository,
      SubscriptionPaymentRepository subscriptionPaymentRepository,
      PayFastBillingProperties payfastProperties,
      BillingProperties billingProperties) {
    this.subscriptionRepository = subscriptionRepository;
    this.subscriptionPaymentRepository = subscriptionPaymentRepository;
    this.payfastProperties = payfastProperties;
    this.billingProperties = billingProperties;
  }

  @Transactional
  public void processItn(Map<String, String> params, String sourceIp) {
    // Step 1 — IP validation
    if (!validateSourceIp(sourceIp)) {
      return;
    }

    // Step 2 — Signature validation
    if (!validateSignature(params)) {
      return;
    }

    // Step 3 — Extract fields
    String mPaymentId = params.get("m_payment_id");
    String customStr1 = params.get("custom_str1");
    String paymentStatus = params.get("payment_status");
    String token = params.get("token");
    String amountGross = params.get("amount_gross");

    // Step 4 — Idempotency check
    if (mPaymentId != null && subscriptionPaymentRepository.existsByPayfastPaymentId(mPaymentId)) {
      log.info("Duplicate ITN received for payment ID: {}, skipping", mPaymentId);
      return;
    }

    // Step 5 — Route by payment_status
    switch (paymentStatus) {
      case "COMPLETE" -> handleComplete(mPaymentId, customStr1, token, amountGross, params);
      case "FAILED" -> handleFailed(mPaymentId, customStr1, amountGross, params);
      case "CANCELLED" -> handleCancelled(customStr1);
      default -> log.warn("Unknown payment_status in ITN: {}", paymentStatus);
    }
  }

  private void handleComplete(
      String mPaymentId,
      String customStr1,
      String token,
      String amountGross,
      Map<String, String> params) {
    var orgId = UUID.fromString(customStr1);
    var subscription =
        subscriptionRepository
            .findByOrganizationId(orgId)
            .orElseThrow(
                () -> {
                  log.warn("No subscription found for organization {} in COMPLETE ITN", orgId);
                  return new IllegalStateException("No subscription for organization: " + orgId);
                });

    subscription.transitionTo(Subscription.SubscriptionStatus.ACTIVE);

    if (token != null && subscription.getPayfastToken() == null) {
      subscription.setPayfastToken(token);
    }

    subscription.setLastPaymentAt(Instant.now());
    subscription.setNextBillingAt(Instant.now().plus(Duration.ofDays(30)));
    subscription.setGraceEndsAt(null);
    subscriptionRepository.save(subscription);

    int amountCents = (int) Math.round(Double.parseDouble(amountGross) * 100);
    var payment =
        new SubscriptionPayment(
            subscription.getId(),
            mPaymentId,
            amountCents,
            billingProperties.currency(),
            SubscriptionPayment.PaymentStatus.COMPLETE,
            Instant.now(),
            Map.copyOf(params));
    subscriptionPaymentRepository.save(payment);

    // TODO(422): evict SubscriptionStatusCache for orgId after status change

    log.info("COMPLETE ITN processed for organization {}, payment {}", orgId, mPaymentId);
  }

  private void handleFailed(
      String mPaymentId, String customStr1, String amountGross, Map<String, String> params) {
    var orgId = UUID.fromString(customStr1);
    var subscription =
        subscriptionRepository
            .findByOrganizationId(orgId)
            .orElseThrow(
                () -> {
                  log.warn("No subscription found for organization {} in FAILED ITN", orgId);
                  return new IllegalStateException("No subscription for organization: " + orgId);
                });

    if (subscription.getSubscriptionStatus() == Subscription.SubscriptionStatus.ACTIVE) {
      subscription.transitionTo(Subscription.SubscriptionStatus.PAST_DUE);
    }
    subscriptionRepository.save(subscription);

    int amountCents = (int) Math.round(Double.parseDouble(amountGross) * 100);
    var payment =
        new SubscriptionPayment(
            subscription.getId(),
            mPaymentId,
            amountCents,
            billingProperties.currency(),
            SubscriptionPayment.PaymentStatus.FAILED,
            Instant.now(),
            Map.copyOf(params));
    subscriptionPaymentRepository.save(payment);

    // TODO(422): evict SubscriptionStatusCache for orgId after status change

    log.warn("FAILED ITN processed for organization {}, payment {}", orgId, mPaymentId);
  }

  private void handleCancelled(String customStr1) {
    var orgId = UUID.fromString(customStr1);
    var subscription =
        subscriptionRepository
            .findByOrganizationId(orgId)
            .orElseThrow(
                () -> {
                  log.warn("No subscription found for organization {} in CANCELLED ITN", orgId);
                  return new IllegalStateException("No subscription for organization: " + orgId);
                });

    var status = subscription.getSubscriptionStatus();
    if (status == Subscription.SubscriptionStatus.PAST_DUE) {
      subscription.transitionTo(Subscription.SubscriptionStatus.SUSPENDED);
    } else if (status == Subscription.SubscriptionStatus.ACTIVE
        || status == Subscription.SubscriptionStatus.PENDING_CANCELLATION) {
      subscription.transitionTo(Subscription.SubscriptionStatus.GRACE_PERIOD);
      subscription.setGraceEndsAt(
          Instant.now().plus(Duration.ofDays(billingProperties.gracePeriodDays())));
    }
    subscriptionRepository.save(subscription);

    // TODO(422): evict SubscriptionStatusCache for orgId after status change

    log.info("CANCELLED ITN processed for organization {}", orgId);
  }

  boolean validateSourceIp(String sourceIp) {
    if (sourceIp == null || sourceIp.isBlank()) {
      log.warn("PayFast subscription ITN: missing source IP");
      return false;
    }

    // Handle X-Forwarded-For with multiple IPs
    var ip = sourceIp.split(",")[0].trim();

    // In sandbox mode, allow localhost
    if (payfastProperties.sandbox()) {
      if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
        return true;
      }
    }

    try {
      long ipLong = ipToLong(ip);
      if (ipLong >= PAYFAST_IP_RANGE_START && ipLong <= PAYFAST_IP_RANGE_END) {
        return true;
      }
    } catch (Exception e) {
      String safeIp = ip.replaceAll("[\\r\\n\\t]", "").substring(0, Math.min(ip.length(), 45));
      log.warn("PayFast subscription ITN: invalid IP address '{}'", safeIp);
      return false;
    }

    log.warn("PayFast subscription ITN: IP {} not in allowed range", ip);
    return false;
  }

  boolean validateSignature(Map<String, String> params) {
    String receivedSignature = params.get("signature");
    if (receivedSignature == null || receivedSignature.isBlank()) {
      log.warn("PayFast subscription ITN: missing signature");
      return false;
    }

    // Build sorted param string from all params except "signature" — raw values, no URL encoding
    var sorted = new TreeMap<>(params);
    sorted.remove("signature");

    var paramString =
        sorted.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue().trim())
            .collect(Collectors.joining("&"));

    String passphrase = payfastProperties.passphrase();
    if (passphrase != null && !passphrase.isEmpty()) {
      paramString += "&passphrase=" + passphrase.trim();
    }

    String computed = md5Hash(paramString);
    if (!computed.equals(receivedSignature)) {
      log.warn("PayFast subscription ITN: signature mismatch");
      return false;
    }

    return true;
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
