package io.b2mash.b2b.b2bstrawman.integration.email;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Ticker;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class EmailRateLimiterTest {

  @Test
  void tryAcquire_succeedsWithinLimit() {
    var limiter = new EmailRateLimiter(5, 200, 2000, Ticker.systemTicker());

    boolean result = limiter.tryAcquire("tenant_a", "smtp");

    assertThat(result).isTrue();
    var status = limiter.getStatus("tenant_a", "smtp");
    assertThat(status.currentCount()).isEqualTo(1);
    assertThat(status.allowed()).isTrue();
  }

  @Test
  void tryAcquire_failsWhenTenantLimitExceeded() {
    var limiter = new EmailRateLimiter(3, 200, 2000, Ticker.systemTicker());

    assertThat(limiter.tryAcquire("tenant_b", "smtp")).isTrue();
    assertThat(limiter.tryAcquire("tenant_b", "smtp")).isTrue();
    assertThat(limiter.tryAcquire("tenant_b", "smtp")).isTrue();
    // 4th should fail
    assertThat(limiter.tryAcquire("tenant_b", "smtp")).isFalse();

    var status = limiter.getStatus("tenant_b", "smtp");
    assertThat(status.currentCount()).isEqualTo(3);
    assertThat(status.allowed()).isFalse();
  }

  @Test
  void tryAcquire_failsWhenPlatformAggregateExceeded() {
    var limiter = new EmailRateLimiter(100, 200, 3, Ticker.systemTicker());

    // Use different tenants to stay within per-tenant limit but hit platform aggregate
    assertThat(limiter.tryAcquire("tenant_c1", "smtp")).isTrue();
    assertThat(limiter.tryAcquire("tenant_c2", "smtp")).isTrue();
    assertThat(limiter.tryAcquire("tenant_c3", "smtp")).isTrue();
    // 4th should fail due to platform aggregate
    assertThat(limiter.tryAcquire("tenant_c4", "smtp")).isFalse();
  }

  @Test
  void byoak_hasHigherLimitThanSmtp() {
    var limiter = new EmailRateLimiter(2, 5, 2000, Ticker.systemTicker());

    // SMTP limit is 2
    assertThat(limiter.tryAcquire("tenant_d", "smtp")).isTrue();
    assertThat(limiter.tryAcquire("tenant_d", "smtp")).isTrue();
    assertThat(limiter.tryAcquire("tenant_d", "smtp")).isFalse();

    // BYOAK limit is 5 — same tenant different provider
    assertThat(limiter.tryAcquire("tenant_d", "sendgrid")).isTrue();
    assertThat(limiter.tryAcquire("tenant_d", "sendgrid")).isTrue();
    assertThat(limiter.tryAcquire("tenant_d", "sendgrid")).isTrue();
    assertThat(limiter.tryAcquire("tenant_d", "sendgrid")).isTrue();
    assertThat(limiter.tryAcquire("tenant_d", "sendgrid")).isTrue();
    assertThat(limiter.tryAcquire("tenant_d", "sendgrid")).isFalse();
  }

  @Test
  void counters_resetAfterCacheExpiry() {
    var fakeTicker = new FakeTicker();
    var limiter = new EmailRateLimiter(2, 200, 2000, fakeTicker);

    assertThat(limiter.tryAcquire("tenant_e", "smtp")).isTrue();
    assertThat(limiter.tryAcquire("tenant_e", "smtp")).isTrue();
    assertThat(limiter.tryAcquire("tenant_e", "smtp")).isFalse();

    // Advance time past the 1-hour expiry
    fakeTicker.advance(61 * 60 * 1_000_000_000L); // 61 minutes in nanos

    // After expiry, counter should be reset
    assertThat(limiter.tryAcquire("tenant_e", "smtp")).isTrue();
    var status = limiter.getStatus("tenant_e", "smtp");
    assertThat(status.currentCount()).isEqualTo(1);
  }

  @Test
  void platformAggregate_notAppliedToByoak() {
    var limiter = new EmailRateLimiter(100, 100, 2, Ticker.systemTicker());

    // Exhaust platform aggregate via SMTP
    assertThat(limiter.tryAcquire("tenant_f1", "smtp")).isTrue();
    assertThat(limiter.tryAcquire("tenant_f2", "smtp")).isTrue();
    assertThat(limiter.tryAcquire("tenant_f3", "smtp")).isFalse(); // platform aggregate hit

    // BYOAK should still work (platform aggregate only applies to SMTP)
    assertThat(limiter.tryAcquire("tenant_f4", "sendgrid")).isTrue();
  }

  /** Fake ticker for simulating time passage in Caffeine caches. */
  private static class FakeTicker implements Ticker {
    private final AtomicLong nanos = new AtomicLong(System.nanoTime());

    void advance(long deltaNanos) {
      nanos.addAndGet(deltaNanos);
    }

    @Override
    public long read() {
      return nanos.get();
    }
  }
}
