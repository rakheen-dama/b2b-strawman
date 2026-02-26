package io.b2mash.b2b.b2bstrawman.integration.payment;

import java.util.Map;

/**
 * Parsed result of a {@link PaymentGateway#handleWebhook(String, Map)} call. {@code verified} must
 * be {@code true} before any business logic is applied — controllers must reject unverified
 * webhooks.
 */
public record WebhookResult(
    boolean verified,
    String eventType,
    String sessionId,
    String paymentReference,
    PaymentStatus status,
    Map<String, String> metadata) {

  public WebhookResult {
    metadata = (metadata == null) ? Map.of() : Map.copyOf(metadata);
  }
}
