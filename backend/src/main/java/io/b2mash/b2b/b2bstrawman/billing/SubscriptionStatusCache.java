package io.b2mash.b2b.b2bstrawman.billing;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionStatusCache {

  private final SubscriptionRepository subscriptionRepository;
  private final Cache<UUID, Subscription.SubscriptionStatus> cache;

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
    Subscription.SubscriptionStatus cached = cache.getIfPresent(organizationId);
    if (cached != null) {
      return cached;
    }
    Subscription.SubscriptionStatus status = loadFromDb(organizationId);
    cache.put(organizationId, status);
    return status;
  }

  /** Evicts the cached status for the given organization, forcing a reload on the next access. */
  public void evict(UUID organizationId) {
    cache.invalidate(organizationId);
  }

  private Subscription.SubscriptionStatus loadFromDb(UUID organizationId) {
    return subscriptionRepository
        .findByOrganizationId(organizationId)
        .map(Subscription::getSubscriptionStatus)
        .orElse(Subscription.SubscriptionStatus.TRIALING);
  }
}
