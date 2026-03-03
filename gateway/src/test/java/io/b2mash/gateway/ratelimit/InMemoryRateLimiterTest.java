package io.b2mash.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class InMemoryRateLimiterTest {

  @Test
  void allowsRequestsWithinCapacity() {
    var limiter = new InMemoryRateLimiter(5, 5, Duration.ofSeconds(1));

    for (int i = 0; i < 5; i++) {
      assertThat(limiter.tryAcquire("test-key")).isTrue();
    }
  }

  @Test
  void rejectsRequestsExceedingCapacity() {
    var limiter = new InMemoryRateLimiter(3, 3, Duration.ofSeconds(1));

    // Consume all tokens
    for (int i = 0; i < 3; i++) {
      assertThat(limiter.tryAcquire("test-key")).isTrue();
    }

    // Next request should be rejected
    assertThat(limiter.tryAcquire("test-key")).isFalse();
  }

  @Test
  void isolatesBucketsByKey() {
    var limiter = new InMemoryRateLimiter(2, 2, Duration.ofSeconds(1));

    // Exhaust key A
    assertThat(limiter.tryAcquire("key-a")).isTrue();
    assertThat(limiter.tryAcquire("key-a")).isTrue();
    assertThat(limiter.tryAcquire("key-a")).isFalse();

    // Key B should still have tokens
    assertThat(limiter.tryAcquire("key-b")).isTrue();
  }

  @Test
  void tracksBucketCount() {
    var limiter = new InMemoryRateLimiter(10, 10, Duration.ofSeconds(1));

    limiter.tryAcquire("key-1");
    limiter.tryAcquire("key-2");
    limiter.tryAcquire("key-3");

    assertThat(limiter.size()).isEqualTo(3);
  }
}
