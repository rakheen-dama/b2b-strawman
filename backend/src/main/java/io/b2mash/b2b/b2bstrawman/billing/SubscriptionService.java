package io.b2mash.b2b.b2bstrawman.billing;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {

  private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

  private final SubscriptionRepository subscriptionRepository;
  private final OrganizationRepository organizationRepository;
  private final MemberRepository memberRepository;
  private final BillingProperties billingProperties;
  private final SubscriptionStatusCache statusCache;

  public SubscriptionService(
      SubscriptionRepository subscriptionRepository,
      OrganizationRepository organizationRepository,
      MemberRepository memberRepository,
      BillingProperties billingProperties,
      SubscriptionStatusCache statusCache) {
    this.subscriptionRepository = subscriptionRepository;
    this.organizationRepository = organizationRepository;
    this.memberRepository = memberRepository;
    this.billingProperties = billingProperties;
    this.statusCache = statusCache;
  }

  /** Creates a TRIALING subscription for a newly provisioned org. Idempotent. */
  @Transactional
  public void createSubscription(UUID organizationId) {
    if (subscriptionRepository.findByOrganizationId(organizationId).isPresent()) {
      log.info("Subscription already exists for organization {}", organizationId);
      return;
    }
    subscriptionRepository.save(new Subscription(organizationId, billingProperties.trialDays()));
    log.info("Created TRIALING subscription for organization {}", organizationId);
  }

  /**
   * Returns billing info for an org. Must be called within tenant context (for member count).
   * Returns a synthetic TRIALING response if no subscription exists (defensive).
   */
  @Transactional(readOnly = true)
  public BillingResponse getSubscription(String clerkOrgId) {
    var org =
        organizationRepository
            .findByClerkOrgId(clerkOrgId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", clerkOrgId));

    var subscription = subscriptionRepository.findByOrganizationId(org.getId());
    long currentMembers = memberRepository.count();

    if (subscription.isPresent()) {
      return BillingResponse.from(subscription.get(), currentMembers, billingProperties);
    }

    // Defensive: synthetic TRIALING response if subscription row is missing
    return BillingResponse.syntheticTrialing(currentMembers, billingProperties);
  }

  /**
   * Extends the trial period for a TRIALING subscription. Admin-only operation. Throws
   * InvalidStateException if subscription is not TRIALING.
   */
  @Transactional
  public BillingResponse extendTrial(UUID organizationId, int additionalDays) {
    var subscription =
        subscriptionRepository
            .findByOrganizationId(organizationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Subscription", organizationId.toString()));

    if (subscription.getSubscriptionStatus() != Subscription.SubscriptionStatus.TRIALING) {
      throw new InvalidStateException(
          "Cannot extend trial",
          "Subscription must be in TRIALING status, current: "
              + subscription.getSubscriptionStatus());
    }

    var current = subscription.getTrialEndsAt();
    var extended =
        current != null
            ? current.plus(Duration.ofDays(additionalDays))
            : Instant.now().plus(Duration.ofDays(additionalDays));
    subscription.setTrialEndsAt(extended);
    subscriptionRepository.save(subscription);
    statusCache.evict(organizationId);

    log.info(
        "Extended trial for organization {} by {} days, new end: {}",
        organizationId,
        additionalDays,
        extended);
    // Admin endpoints run without tenant context — member count unavailable
    return BillingResponse.from(subscription, 0, billingProperties);
  }

  /**
   * Manually activates a subscription. Admin-only operation. Uses transitionTo() which enforces
   * valid transition rules.
   */
  @Transactional
  public BillingResponse activate(UUID organizationId) {
    var subscription =
        subscriptionRepository
            .findByOrganizationId(organizationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Subscription", organizationId.toString()));

    subscription.transitionTo(Subscription.SubscriptionStatus.ACTIVE);
    subscriptionRepository.save(subscription);
    statusCache.evict(organizationId);

    log.info("Manually activated subscription for organization {}", organizationId);
    // Admin endpoints run without tenant context — member count unavailable
    return BillingResponse.from(subscription, 0, billingProperties);
  }
}
