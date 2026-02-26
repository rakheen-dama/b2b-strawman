package io.b2mash.b2b.b2bstrawman.integration.payment;

import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;
import java.util.Map;

/**
 * Port interface for online payment collection. Tenant-scoped: each org can configure their own PSP
 * (Stripe, PayFast, etc.) or fall back to the NoOp adapter for manual-only recording.
 *
 * <p>Aligned with the integration port pattern established in Phase 21 (see ADR-098).
 */
public interface PaymentGateway {

  /** Unique provider identifier (e.g., "stripe", "payfast", "noop"). */
  String providerId();

  /**
   * Creates a checkout session with the PSP and returns a redirect URL. For providers that don't
   * support online payments (NoOp), returns {@link CreateSessionResult#notSupported(String)}.
   */
  CreateSessionResult createCheckoutSession(CheckoutRequest request);

  /**
   * Validates and processes an incoming webhook payload from the PSP. Returns parsed event data
   * including payment status and references.
   */
  WebhookResult handleWebhook(String payload, Map<String, String> headers);

  /** Queries the PSP for the current status of a payment session. Fallback for missed webhooks. */
  PaymentStatus queryPaymentStatus(String sessionId);

  /** Records a manual payment (bank transfer, cheque, cash, etc.). Returns a payment reference. */
  PaymentResult recordManualPayment(PaymentRequest request);

  /**
   * Expires/cancels a checkout session on the PSP. Called when manual payment is recorded while a
   * session is active. No-op for providers that don't support session expiry (PayFast, NoOp).
   */
  default void expireSession(String sessionId) {
    // no-op by default
  }

  /** Tests the PSP connection (e.g., Stripe Balance.retrieve()). */
  ConnectionTestResult testConnection();
}
