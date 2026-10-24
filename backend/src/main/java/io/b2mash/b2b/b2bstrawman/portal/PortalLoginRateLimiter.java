package io.b2mash.b2b.b2bstrawman.portal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import io.b2mash.b2b.b2bstrawman.exception.TooManyRequestsException;
import jakarta.annotation.Nullable;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Per-email cap on portal sign-in link requests, across every firm (Phase 88 U11, ADR-351, §13.6.3
 * point 4). Sits beside the per-contact cap in {@link MagicLinkService} (3 tokens / contact / 5
 * min). Counted BEFORE any directory or tenant lookup, for every request — known or unknown email,
 * with or without {@code orgId} — so hitting the cap reveals nothing about which firms know the
 * email. Fixed window from the first request (in-place increments are not cache writes). In-memory
 * per JVM, like {@code EmailRateLimiter}. Never logs the email.
 */
@Service
public class PortalLoginRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(PortalLoginRateLimiter.class);

  private final int maxRequestsPerEmail;
  private final Cache<String, AtomicInteger> emailCounters;

  // @Autowired needed: two constructors (Spring + test with a custom Ticker), as in
  // EmailRateLimiter.
  @Autowired
  public PortalLoginRateLimiter(
      @Value("${docteams.portal.auth.email-rate-limit.max-requests:3}") int maxRequestsPerEmail,
      @Value("${docteams.portal.auth.email-rate-limit.window-minutes:15}") long windowMinutes) {
    this(maxRequestsPerEmail, Duration.ofMinutes(windowMinutes), Ticker.systemTicker());
  }

  PortalLoginRateLimiter(int maxRequestsPerEmail, Duration window, Ticker ticker) {
    this.maxRequestsPerEmail = maxRequestsPerEmail;
    this.emailCounters =
        Caffeine.newBuilder().expireAfterWrite(window).maximumSize(100_000).ticker(ticker).build();
  }

  /**
   * Counts one sign-in link request for {@code emailLower} (already normalised by the caller with
   * {@code PortalContactDirectoryService.normalise}). Throws once the cap is exceeded; a rejected
   * request does not count.
   *
   * @param ip the caller's address — used for logging only, never part of the key
   * @throws TooManyRequestsException (429) when the email has used its requests for this window
   */
  public void checkEmail(String emailLower, @Nullable String ip) {
    AtomicInteger counter = emailCounters.get(emailLower, k -> new AtomicInteger());
    if (counter.incrementAndGet() > maxRequestsPerEmail) {
      counter.decrementAndGet();
      log.info("Portal sign-in link request refused: per-email cap reached (ip {})", ip);
      throw new TooManyRequestsException(
          "Too many login attempts",
          "Too many sign-in link requests for this email. Please try again later.");
    }
  }
}
