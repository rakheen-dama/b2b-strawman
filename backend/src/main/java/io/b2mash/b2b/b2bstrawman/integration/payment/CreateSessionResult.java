package io.b2mash.b2b.b2bstrawman.integration.payment;

/**
 * Result of a {@link PaymentGateway#createCheckoutSession(CheckoutRequest)} call.
 *
 * <p>Use the static factories:
 *
 * <ul>
 *   <li>{@link #notSupported(String)} — provider does not support online checkout
 *   <li>{@link #success(String, String)} — session created successfully
 *   <li>{@link #failure(String)} — session creation failed
 * </ul>
 */
public record CreateSessionResult(
    boolean success, boolean supported, String sessionId, String redirectUrl, String errorMessage) {

  /** Provider does not support online checkout (e.g., NoOp). */
  public static CreateSessionResult notSupported(String reason) {
    return new CreateSessionResult(false, false, null, null, reason);
  }

  /** Session created successfully. Client should be redirected to {@code redirectUrl}. */
  public static CreateSessionResult success(String sessionId, String redirectUrl) {
    return new CreateSessionResult(true, true, sessionId, redirectUrl, null);
  }

  /** Session creation failed (e.g., invalid API key, network error). */
  public static CreateSessionResult failure(String errorMessage) {
    return new CreateSessionResult(false, true, null, null, errorMessage);
  }
}
