package io.b2mash.gateway.ratelimit;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple in-memory token bucket rate limiter. Each key (IP address or tenant ID) gets an
 * independent bucket with a configurable capacity and refill rate.
 *
 * <p>Thread-safe via CAS operations on AtomicLong. Buckets are lazily created and never explicitly
 * removed (acceptable for gateway lifecycles — restarts clear all state).
 *
 * <p>For horizontal scaling (multiple gateway instances), replace with Redis-backed rate limiting.
 */
public class InMemoryRateLimiter {

  private final int capacity;
  private final int refillTokens;
  private final long refillIntervalNanos;
  private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

  /**
   * @param capacity Maximum tokens per bucket (burst size)
   * @param refillTokens Tokens added per refill interval
   * @param refillInterval Duration between refills
   */
  public InMemoryRateLimiter(int capacity, int refillTokens, Duration refillInterval) {
    this.capacity = capacity;
    this.refillTokens = refillTokens;
    this.refillIntervalNanos = refillInterval.toNanos();
  }

  /** Returns true if the request is allowed, false if rate-limited. */
  public boolean tryAcquire(String key) {
    TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(capacity));
    return bucket.tryConsume();
  }

  /** Returns the approximate number of tracked keys (for monitoring). */
  public int size() {
    return buckets.size();
  }

  private class TokenBucket {
    private final AtomicLong tokens;
    private final AtomicLong lastRefillNanos;

    TokenBucket(int initialTokens) {
      this.tokens = new AtomicLong(initialTokens);
      this.lastRefillNanos = new AtomicLong(System.nanoTime());
    }

    boolean tryConsume() {
      refill();
      while (true) {
        long current = tokens.get();
        if (current <= 0) {
          return false;
        }
        if (tokens.compareAndSet(current, current - 1)) {
          return true;
        }
        // CAS failed — another thread consumed a token, retry
      }
    }

    private void refill() {
      long now = System.nanoTime();
      long lastRefill = lastRefillNanos.get();
      long elapsed = now - lastRefill;

      if (elapsed < refillIntervalNanos) {
        return;
      }

      long periods = elapsed / refillIntervalNanos;
      long tokensToAdd = periods * refillTokens;
      long newLastRefill = lastRefill + periods * refillIntervalNanos;

      if (lastRefillNanos.compareAndSet(lastRefill, newLastRefill)) {
        long current = tokens.get();
        long newTokens = Math.min(capacity, current + tokensToAdd);
        // Slight race: brief under-granting (rejecting during refill window) and
        // over-granting (overwriting concurrent consumption) are both possible but
        // self-correcting within one refill interval. Acceptable for gateway rate limiting.
        tokens.set(newTokens);
      }
    }
  }
}
