package io.b2mash.b2b.b2bstrawman.integration.email;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailRateLimiter {

  private final int smtpLimit;
  private final int byoakLimit;
  private final int platformAggregateLimit;
  private final Cache<String, AtomicInteger> tenantCounters;
  private final Cache<String, AtomicInteger> aggregateCounter;

  private static final String PLATFORM_AGGREGATE_KEY = "platform-aggregate";

  @Autowired
  public EmailRateLimiter(
      @Value("${docteams.email.rate-limit.smtp:50}") int smtpLimit,
      @Value("${docteams.email.rate-limit.byoak:200}") int byoakLimit,
      @Value("${docteams.email.rate-limit.platform-aggregate:2000}") int platformAggregateLimit) {
    this(smtpLimit, byoakLimit, platformAggregateLimit, Ticker.systemTicker());
  }

  EmailRateLimiter(int smtpLimit, int byoakLimit, int platformAggregateLimit, Ticker ticker) {
    this.smtpLimit = smtpLimit;
    this.byoakLimit = byoakLimit;
    this.platformAggregateLimit = platformAggregateLimit;
    this.tenantCounters =
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(1))
            .maximumSize(10_000)
            .ticker(ticker)
            .build();
    this.aggregateCounter =
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(1))
            .maximumSize(10)
            .ticker(ticker)
            .build();
  }

  public boolean tryAcquire(String tenantSchema, String providerSlug) {
    int tenantLimit = getLimitForProvider(providerSlug);
    String tenantKey = "tenant:" + tenantSchema + ":" + providerSlug;

    var tenantCounter = tenantCounters.get(tenantKey, k -> new AtomicInteger(0));
    int tenantCount = tenantCounter.incrementAndGet();
    if (tenantCount > tenantLimit) {
      tenantCounter.decrementAndGet();
      return false;
    }

    if ("smtp".equals(providerSlug)) {
      var aggregate = aggregateCounter.get(PLATFORM_AGGREGATE_KEY, k -> new AtomicInteger(0));
      int aggregateCount = aggregate.incrementAndGet();
      if (aggregateCount > platformAggregateLimit) {
        aggregate.decrementAndGet();
        tenantCounter.decrementAndGet();
        return false;
      }
    }

    return true;
  }

  public RateLimitStatus getStatus(String tenantSchema, String providerSlug) {
    int limit = getLimitForProvider(providerSlug);
    String tenantKey = "tenant:" + tenantSchema + ":" + providerSlug;

    var counter = tenantCounters.getIfPresent(tenantKey);
    int currentCount = counter != null ? counter.get() : 0;
    return new RateLimitStatus(currentCount, limit, currentCount < limit);
  }

  private int getLimitForProvider(String providerSlug) {
    return "smtp".equals(providerSlug) ? smtpLimit : byoakLimit;
  }

  public record RateLimitStatus(int currentCount, int limit, boolean allowed) {}
}
