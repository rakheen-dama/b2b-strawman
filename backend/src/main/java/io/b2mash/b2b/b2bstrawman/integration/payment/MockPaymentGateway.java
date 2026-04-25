package io.b2mash.b2b.b2bstrawman.integration.payment;

import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationAdapter;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Dev-only mock payment gateway used to simulate a full PSP round-trip locally without any real
 * network calls. Profile-gated to {@code local}, {@code dev}, {@code keycloak}, {@code test} —
 * never loaded in {@code prod}. Wired into a tenant via an {@link
 * io.b2mash.b2b.b2bstrawman.integration.OrgIntegration} row with {@code provider_slug='mock'} — see
 * {@code PackReconciliationRunner} for legal-za auto-seed.
 *
 * <p>Behaviour: {@code createCheckoutSession} returns a redirect URL pointing to a dev-only mock
 * checkout page hosted at {@code {appBaseUrl}/portal/dev/mock-payment} with the local session id +
 * invoice id + amount + return URL embedded in query params. The mock page POSTs back to {@code
 * /api/payments/mock/complete} which drives {@link PaymentWebhookService} synchronously to flip the
 * invoice SENT→PAID and emit the payment event.
 */
@Component
@Profile({"local", "dev", "keycloak", "test"})
@IntegrationAdapter(domain = IntegrationDomain.PAYMENT, slug = "mock")
public class MockPaymentGateway implements PaymentGateway {

  private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);
  private static final String SESSION_PREFIX = "MOCK-SESS-";
  private static final String MANUAL_PREFIX = "MOCK-MANUAL-";

  /**
   * In-memory store of session id → status. Populated by {@link #handleWebhook(String,
   * java.util.Map)} so that {@link #queryPaymentStatus(String)} returns the right value for
   * missed-webhook fallback. Bounded to the JVM lifecycle of the dev backend — acceptable for a
   * dev-only adapter.
   */
  private final ConcurrentHashMap<String, PaymentStatus> sessionStatus = new ConcurrentHashMap<>();

  private final ObjectMapper objectMapper;
  private final String mockCheckoutBaseUrl;

  public MockPaymentGateway(
      ObjectMapper objectMapper,
      @Value("${docteams.payment.mock.checkout-base-url:http://localhost:8080}")
          String mockCheckoutBaseUrl) {
    this.objectMapper = objectMapper;
    // The mock checkout page is served by MockPaymentController on the backend, NOT the Next.js
    // frontend. `docteams.app.base-url` points at Next.js (port 3000) by default, so we read a
    // dedicated property here that points at the backend/gateway origin.
    this.mockCheckoutBaseUrl = mockCheckoutBaseUrl;
  }

  @Override
  public String providerId() {
    return "mock";
  }

  @Override
  public CreateSessionResult createCheckoutSession(CheckoutRequest request) {
    String sessionId = SESSION_PREFIX + UUID.randomUUID();
    sessionStatus.put(sessionId, PaymentStatus.PENDING);

    String successUrl = request.successUrl() != null ? request.successUrl() : "";
    String invoiceId = request.invoiceId() != null ? request.invoiceId().toString() : "";
    String amount = request.amount() != null ? request.amount().toPlainString() : "0";
    String currency = request.currency() != null ? request.currency() : "";

    String redirectUrl =
        mockCheckoutBaseUrl
            + "/portal/dev/mock-payment?sessionId="
            + URLEncoder.encode(sessionId, StandardCharsets.UTF_8)
            + "&invoiceId="
            + URLEncoder.encode(invoiceId, StandardCharsets.UTF_8)
            + "&amount="
            + URLEncoder.encode(amount, StandardCharsets.UTF_8)
            + "&currency="
            + URLEncoder.encode(currency, StandardCharsets.UTF_8)
            + "&returnUrl="
            + URLEncoder.encode(successUrl, StandardCharsets.UTF_8);

    log.info(
        "MockPayment: created session {} for invoice {} (amount={} {})",
        sessionId,
        invoiceId,
        amount,
        currency);

    return CreateSessionResult.success(sessionId, redirectUrl);
  }

  /**
   * Parses a JSON payload of the form {@code {"sessionId":"MOCK-SESS-...", "status":"PAID|FAILED",
   * "reference":"...", "tenantSchema":"tenant_...", "invoiceId":"..."}} and returns the
   * corresponding {@link WebhookResult}. The metadata map carries {@code tenantSchema} + {@code
   * invoiceId} as required by {@link
   * io.b2mash.b2b.b2bstrawman.invoice.PaymentReconciliationService}.
   */
  @Override
  public WebhookResult handleWebhook(String payload, Map<String, String> headers) {
    try {
      JsonNode root = objectMapper.readTree(payload);
      String sessionId = optionalText(root, "sessionId");
      String reference = optionalText(root, "reference");
      String tenantSchema = optionalText(root, "tenantSchema");
      String invoiceId = optionalText(root, "invoiceId");
      String statusStr = optionalText(root, "status");

      PaymentStatus status =
          switch (statusStr == null ? "" : statusStr.toUpperCase()) {
            case "PAID", "COMPLETED", "SUCCESS" -> PaymentStatus.COMPLETED;
            case "FAILED", "DECLINED" -> PaymentStatus.FAILED;
            case "EXPIRED" -> PaymentStatus.EXPIRED;
            case "CANCELLED", "CANCELED" -> PaymentStatus.CANCELLED;
            default -> PaymentStatus.PENDING;
          };

      if (sessionId != null && !sessionId.isBlank()) {
        sessionStatus.put(sessionId, status);
      }

      String paymentRef =
          (reference != null && !reference.isBlank())
              ? reference
              : (sessionId != null ? sessionId.replaceFirst(SESSION_PREFIX, "MOCK-PAY-") : null);

      var metadata = new java.util.HashMap<String, String>();
      metadata.put("provider", "mock");
      if (tenantSchema != null && !tenantSchema.isBlank()) {
        metadata.put("tenantSchema", tenantSchema);
      }
      if (invoiceId != null && !invoiceId.isBlank()) {
        metadata.put("invoiceId", invoiceId);
      }

      log.info(
          "MockPayment: webhook processed sessionId={} status={} reference={}",
          sessionId,
          status,
          paymentRef);

      return new WebhookResult(
          true, "payment." + status.name().toLowerCase(), sessionId, paymentRef, status, metadata);
    } catch (JacksonException e) {
      log.warn("MockPayment: failed to parse webhook payload: {}", e.getMessage());
      return new WebhookResult(false, null, null, null, null, Map.of());
    }
  }

  @Override
  public PaymentStatus queryPaymentStatus(String sessionId) {
    if (sessionId == null || !sessionId.startsWith(SESSION_PREFIX)) {
      return PaymentStatus.PENDING;
    }
    return sessionStatus.getOrDefault(sessionId, PaymentStatus.PENDING);
  }

  @Override
  public PaymentResult recordManualPayment(PaymentRequest request) {
    String reference = MANUAL_PREFIX + UUID.randomUUID();
    log.info(
        "MockPayment: recorded manual payment for invoice={}, amount={} {}, reference={}",
        request.invoiceId(),
        request.amount(),
        request.currency(),
        reference);
    return new PaymentResult(true, reference, null);
  }

  @Override
  public ConnectionTestResult testConnection() {
    return new ConnectionTestResult(true, "mock", null);
  }

  private static String optionalText(JsonNode root, String field) {
    JsonNode node = root.path(field);
    return node.isMissingNode() || node.isNull() ? null : node.asText();
  }
}
