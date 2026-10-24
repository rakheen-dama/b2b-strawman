package io.b2mash.b2b.b2bstrawman.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.benmanes.caffeine.cache.Ticker;
import io.b2mash.b2b.b2bstrawman.exception.TooManyRequestsException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Unit tests for the per-email portal sign-in cap (Phase 88 U11, ADR-351). No Spring context. */
class PortalLoginRateLimiterTest {

  private static final long MINUTE_NANOS = 60L * 1_000_000_000L;

  private final FakeTicker ticker = new FakeTicker();
  private final PortalLoginRateLimiter limiter =
      new PortalLoginRateLimiter(3, Duration.ofMinutes(15), ticker);

  @Test
  void allowsThreeRequestsThenRejectsTheFourthWith429() {
    allow("a@x.com", 3);

    assertThatThrownBy(() -> limiter.checkEmail("a@x.com", "127.0.0.1"))
        .isInstanceOfSatisfying(
            TooManyRequestsException.class,
            e -> {
              assertThat(e.getStatusCode().value()).isEqualTo(429);
              assertThat(e.getBody().getTitle()).isEqualTo("Too many login attempts");
            });
  }

  @Test
  void rejectedRequestDoesNotConsumeAndWindowExpiryRestoresAccess() {
    allow("b@x.com", 3);
    assertRejected("b@x.com");
    assertRejected("b@x.com");

    ticker.advance(16 * MINUTE_NANOS);

    allow("b@x.com", 3);
    assertRejected("b@x.com");
  }

  @Test
  void keysAreIndependent() {
    allow("c@x.com", 3);
    assertRejected("c@x.com");

    allow("d@x.com", 3);
  }

  @Test
  void windowIsFixedFromTheFirstRequest() {
    allow("e@x.com", 3);

    ticker.advance(10 * MINUTE_NANOS);
    assertRejected("e@x.com"); // still inside the 15-min window opened by the first request

    ticker.advance(6 * MINUTE_NANOS); // 16 min after the first request
    allow("e@x.com", 1);
  }

  @Test
  void ipIsNotPartOfTheKey() {
    limiter.checkEmail("f@x.com", "10.0.0.1");
    limiter.checkEmail("f@x.com", "10.0.0.2");
    limiter.checkEmail("f@x.com", null);

    assertThatThrownBy(() -> limiter.checkEmail("f@x.com", "10.0.0.3"))
        .isInstanceOf(TooManyRequestsException.class);
  }

  private void allow(String email, int times) {
    for (int i = 0; i < times; i++) {
      assertThatCode(() -> limiter.checkEmail(email, "127.0.0.1")).doesNotThrowAnyException();
    }
  }

  private void assertRejected(String email) {
    assertThatThrownBy(() -> limiter.checkEmail(email, "127.0.0.1"))
        .isInstanceOf(TooManyRequestsException.class);
  }

  /** Fake ticker for simulating time passage in Caffeine caches (as EmailRateLimiterTest). */
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
