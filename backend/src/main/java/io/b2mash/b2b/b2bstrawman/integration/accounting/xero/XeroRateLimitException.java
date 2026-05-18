package io.b2mash.b2b.b2bstrawman.integration.accounting.xero;

import java.time.Duration;

/**
 * Thrown by {@link XeroApiClient} when the Xero API rate limit is approaching or exceeded. Carries
 * the {@code retryAfter} duration extracted from Xero's {@code Retry-After} response header. The
 * sync worker catches this to pause draining entries for the affected tenant.
 */
public class XeroRateLimitException extends RuntimeException {

  private final Duration retryAfter;

  public XeroRateLimitException(String message, Duration retryAfter) {
    super(message);
    this.retryAfter = retryAfter;
  }

  /** Duration to wait before retrying, derived from the Retry-After header. */
  public Duration getRetryAfter() {
    return retryAfter;
  }
}
