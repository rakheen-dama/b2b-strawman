package io.b2mash.b2b.b2bstrawman.billing;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {

  private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

  private final SubscriptionRepository subscriptionRepository;
  private final SubscriptionPaymentRepository subscriptionPaymentRepository;
  private final OrganizationRepository organizationRepository;
  private final MemberRepository memberRepository;
  private final BillingProperties billingProperties;

  public SubscriptionService(
      SubscriptionRepository subscriptionRepository,
      SubscriptionPaymentRepository subscriptionPaymentRepository,
      OrganizationRepository organizationRepository,
      MemberRepository memberRepository,
      BillingProperties billingProperties) {
    this.subscriptionRepository = subscriptionRepository;
    this.subscriptionPaymentRepository = subscriptionPaymentRepository;
    this.organizationRepository = organizationRepository;
    this.memberRepository = memberRepository;
    this.billingProperties = billingProperties;
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
   * Initiates a subscription for an org. Returns a placeholder response — full PayFast wiring comes
   * in Epic 421.
   */
  @Transactional
  public SubscribeResponse initiateSubscribe(String clerkOrgId) {
    var org =
        organizationRepository
            .findByClerkOrgId(clerkOrgId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", clerkOrgId));

    var subscription =
        subscriptionRepository
            .findByOrganizationId(org.getId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Subscription", "organization " + org.getId()));

    var status = subscription.getSubscriptionStatus();
    boolean allowed =
        switch (status) {
          case TRIALING, EXPIRED, GRACE_PERIOD, SUSPENDED, LOCKED -> true;
          default -> false;
        };

    if (!allowed) {
      throw new InvalidStateException(
          "Cannot subscribe", "Subscription status does not allow subscribing: " + status);
    }

    // Placeholder — full PayFast form data wiring is Epic 421
    return new SubscribeResponse(null, Map.of());
  }

  /** Cancels an ACTIVE subscription by transitioning to PENDING_CANCELLATION. */
  @Transactional
  public BillingResponse cancelSubscription(String clerkOrgId) {
    var org =
        organizationRepository
            .findByClerkOrgId(clerkOrgId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", clerkOrgId));

    var subscription =
        subscriptionRepository
            .findByOrganizationId(org.getId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Subscription", "organization " + org.getId()));

    if (subscription.getSubscriptionStatus() != Subscription.SubscriptionStatus.ACTIVE) {
      throw new InvalidStateException(
          "Cannot cancel", "Only ACTIVE subscriptions can be cancelled");
    }

    subscription.transitionTo(Subscription.SubscriptionStatus.PENDING_CANCELLATION);
    subscription.setCancelledAt(Instant.now());
    subscriptionRepository.save(subscription);

    long currentMembers = memberRepository.count();
    return BillingResponse.from(subscription, currentMembers, billingProperties);
  }

  /** Returns paginated payment history for an org's subscription. */
  @Transactional(readOnly = true)
  public Page<SubscriptionPayment> getPayments(String clerkOrgId, Pageable pageable) {
    var org =
        organizationRepository
            .findByClerkOrgId(clerkOrgId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", clerkOrgId));

    var subscription =
        subscriptionRepository
            .findByOrganizationId(org.getId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Subscription", "organization " + org.getId()));

    return subscriptionPaymentRepository.findBySubscriptionId(subscription.getId(), pageable);
  }
}
