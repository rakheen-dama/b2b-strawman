package io.b2mash.b2b.b2bstrawman.integration.payment;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Balance;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationAdapter;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegrationRepository;
import io.b2mash.b2b.b2bstrawman.integration.secret.SecretStore;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stripe payment gateway adapter. Uses per-request API keys resolved from SecretStore for
 * multi-tenant safety. Never sets global Stripe.apiKey.
 */
@Component
@IntegrationAdapter(domain = IntegrationDomain.PAYMENT, slug = "stripe")
public class StripePaymentGateway implements PaymentGateway {

  private static final Logger log = LoggerFactory.getLogger(StripePaymentGateway.class);

  /**
   * Zero-decimal currencies where the amount is already in the smallest unit. See
   * https://docs.stripe.com/currencies#zero-decimal
   */
  private static final Set<String> ZERO_DECIMAL_CURRENCIES =
      Set.of(
          "BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA", "PYG", "RWF", "UGX", "VND", "VUV",
          "XAF", "XOF", "XPF");

  private final SecretStore secretStore;
  private final OrgIntegrationRepository orgIntegrationRepository;

  public StripePaymentGateway(
      SecretStore secretStore, OrgIntegrationRepository orgIntegrationRepository) {
    this.secretStore = secretStore;
    this.orgIntegrationRepository = orgIntegrationRepository;
  }

  @Override
  public String providerId() {
    return "stripe";
  }

  @Override
  public CreateSessionResult createCheckoutSession(CheckoutRequest request) {
    try {
      var apiKey = resolveApiKey();
      var requestOptions = RequestOptions.builder().setApiKey(apiKey).build();

      var params =
          SessionCreateParams.builder()
              .setMode(SessionCreateParams.Mode.PAYMENT)
              .setClientReferenceId(request.invoiceId().toString())
              .setCustomerEmail(request.customerEmail())
              .setSuccessUrl(request.successUrl() + "?session_id={CHECKOUT_SESSION_ID}")
              .setCancelUrl(request.cancelUrl())
              .putMetadata("tenantSchema", request.metadata().get("tenantSchema"))
              .putMetadata("invoiceId", request.invoiceId().toString())
              .addLineItem(
                  SessionCreateParams.LineItem.builder()
                      .setQuantity(1L)
                      .setPriceData(
                          SessionCreateParams.LineItem.PriceData.builder()
                              .setCurrency(request.currency().toLowerCase())
                              .setUnitAmount(toSmallestUnit(request.amount(), request.currency()))
                              .setProductData(
                                  SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                      .setName("Invoice " + request.invoiceNumber())
                                      .build())
                              .build())
                      .build())
              .build();

      var session = Session.create(params, requestOptions);
      return CreateSessionResult.success(session.getId(), session.getUrl());
    } catch (StripeException e) {
      log.error("Stripe session creation failed: {}", e.getMessage(), e);
      return CreateSessionResult.failure(e.getMessage());
    }
  }

  @Override
  public WebhookResult handleWebhook(String payload, Map<String, String> headers) {
    try {
      var signature = headers.get("Stripe-Signature");
      if (signature == null) {
        log.warn("Stripe webhook missing Stripe-Signature header");
        return new WebhookResult(false, null, null, null, null, Map.of());
      }

      var webhookSecret = resolveWebhookSecret();
      var event = Webhook.constructEvent(payload, signature, webhookSecret);
      var eventType = event.getType();

      if ("checkout.session.completed".equals(eventType)) {
        var session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
        if (session == null) {
          log.warn("Stripe webhook: could not deserialize session from checkout.session.completed");
          return new WebhookResult(false, eventType, null, null, null, Map.of());
        }
        return new WebhookResult(
            true,
            eventType,
            session.getId(),
            session.getPaymentIntent(),
            PaymentStatus.COMPLETED,
            session.getMetadata() != null ? Map.copyOf(session.getMetadata()) : Map.of());
      }

      if ("checkout.session.expired".equals(eventType)) {
        var session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
        if (session == null) {
          log.warn("Stripe webhook: could not deserialize session from checkout.session.expired");
          return new WebhookResult(false, eventType, null, null, null, Map.of());
        }
        return new WebhookResult(
            true,
            eventType,
            session.getId(),
            null,
            PaymentStatus.EXPIRED,
            session.getMetadata() != null ? Map.copyOf(session.getMetadata()) : Map.of());
      }

      log.debug("Stripe webhook: unhandled event type '{}'", eventType);
      return new WebhookResult(false, eventType, null, null, null, Map.of());

    } catch (SignatureVerificationException e) {
      log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
      return new WebhookResult(false, null, null, null, null, Map.of());
    } catch (Exception e) {
      log.error("Stripe webhook processing failed: {}", e.getMessage(), e);
      return new WebhookResult(false, null, null, null, null, Map.of());
    }
  }

  @Override
  public PaymentStatus queryPaymentStatus(String sessionId) {
    try {
      var apiKey = resolveApiKey();
      var requestOptions = RequestOptions.builder().setApiKey(apiKey).build();
      var session = Session.retrieve(sessionId, requestOptions);
      return switch (session.getStatus()) {
        case "complete" -> PaymentStatus.COMPLETED;
        case "expired" -> PaymentStatus.EXPIRED;
        case "open" -> PaymentStatus.PENDING;
        default -> PaymentStatus.PENDING;
      };
    } catch (StripeException e) {
      log.error("Stripe status query failed for session {}: {}", sessionId, e.getMessage());
      return PaymentStatus.PENDING;
    }
  }

  @Override
  public PaymentResult recordManualPayment(PaymentRequest request) {
    String reference = "MANUAL-" + UUID.randomUUID();
    log.info(
        "Stripe adapter: recorded manual payment for invoice={}, amount={} {}, reference={}",
        request.invoiceId(),
        request.amount(),
        request.currency(),
        reference);
    return new PaymentResult(true, reference, null);
  }

  @Override
  public void expireSession(String sessionId) {
    try {
      var apiKey = resolveApiKey();
      var requestOptions = RequestOptions.builder().setApiKey(apiKey).build();
      Session.retrieve(sessionId, requestOptions).expire(requestOptions);
      log.info("Stripe session {} expired successfully", sessionId);
    } catch (StripeException e) {
      log.warn("Failed to expire Stripe session {}: {}", sessionId, e.getMessage());
    }
  }

  @Override
  public ConnectionTestResult testConnection() {
    try {
      var apiKey = resolveApiKey();
      var requestOptions = RequestOptions.builder().setApiKey(apiKey).build();
      Balance.retrieve(requestOptions);
      return new ConnectionTestResult(true, "stripe", null);
    } catch (StripeException e) {
      return new ConnectionTestResult(false, "stripe", e.getMessage());
    }
  }

  /**
   * Converts a BigDecimal amount to the smallest currency unit (e.g., cents). For zero-decimal
   * currencies like JPY, returns the amount as-is.
   */
  long toSmallestUnit(BigDecimal amount, String currency) {
    if (ZERO_DECIMAL_CURRENCIES.contains(currency.toUpperCase())) {
      return amount.longValue();
    }
    return amount.multiply(BigDecimal.valueOf(100)).longValue();
  }

  private String resolveApiKey() {
    return secretStore.retrieve("payment:stripe:api_key");
  }

  private String resolveWebhookSecret() {
    return secretStore.retrieve("payment:stripe:webhook_secret");
  }
}
