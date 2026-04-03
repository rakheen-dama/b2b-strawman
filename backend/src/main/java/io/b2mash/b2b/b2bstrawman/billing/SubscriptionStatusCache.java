package io.b2mash.b2b.b2bstrawman.billing;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionStatusCache {

  private static final CachedSubscriptionInfo DEFAULT_INFO =
      new CachedSubscriptionInfo(Subscription.SubscriptionStatus.TRIALING, BillingMethod.MANUAL);

  private final SubscriptionRepository subscriptionRepository;
  private final Cache<UUID, CachedSubscriptionInfo> cache;

  public SubscriptionStatusCache(SubscriptionRepository subscriptionRepository) {
    this.subscriptionRepository = subscriptionRepository;
    this.cache =
        Caffeine.newBuilder().maximumSize(1_000).expireAfterWrite(Duration.ofMinutes(5)).build();
  }

  /**
   * Returns the subscription status for the given organization. Loads from the database on cache
   * miss. Returns {@link Subscription.SubscriptionStatus#TRIALING} as a defensive default if no
   * subscription row exists.
   */
  public Subscription.SubscriptionStatus getStatus(UUID organizationId) {
    return getInfo(organizationId).status();
  }

  /**
   * Returns the full cached subscription info (status and billing method) for the given
   * organization. Loads from the database on cache miss. Returns TRIALING/MANUAL as a defensive
   * default if no subscription row exists.
   */
  public CachedSubscriptionInfo getInfo(UUID organizationId) {
    return cache.get(organizationId, this::loadFromDb);
  }

  /** Evicts the cached status for the given organization, forcing a reload on the next access. */
  public void evict(UUID organizationId) {
    cache.invalidate(organizationId);
  }

  private CachedSubscriptionInfo loadFromDb(UUID organizationId) {
    return subscriptionRepository
        .findByOrganizationId(organizationId)
        .map(sub -> new CachedSubscriptionInfo(sub.getSubscriptionStatus(), sub.getBillingMethod()))
        .orElse(DEFAULT_INFO);
  }

  public record CachedSubscriptionInfo(
      Subscription.SubscriptionStatus status, BillingMethod billingMethod) {}
}
