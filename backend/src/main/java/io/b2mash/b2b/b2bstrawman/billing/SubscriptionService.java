package io.b2mash.b2b.b2bstrawman.billing;

import io.b2mash.b2b.b2bstrawman.billing.payfast.PlatformPayFastService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {

  private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);
  private static final int MAX_PAGE_SIZE = 200;

  private final SubscriptionRepository subscriptionRepository;
  private final SubscriptionPaymentRepository subscriptionPaymentRepository;
  private final OrganizationRepository organizationRepository;
  private final MemberRepository memberRepository;
  private final BillingProperties billingProperties;
  private final PlatformPayFastService platformPayFastService;
  private final SubscriptionStatusCache statusCache;

  public SubscriptionService(
      SubscriptionRepository subscriptionRepository,
      SubscriptionPaymentRepository subscriptionPaymentRepository,
      OrganizationRepository organizationRepository,
      MemberRepository memberRepository,
      BillingProperties billingProperties,
      PlatformPayFastService platformPayFastService,
      SubscriptionStatusCache statusCache) {
    this.subscriptionRepository = subscriptionRepository;
    this.subscriptionPaymentRepository = subscriptionPaymentRepository;
    this.organizationRepository = organizationRepository;
    this.memberRepository = memberRepository;
    this.billingProperties = billingProperties;
    this.platformPayFastService = platformPayFastService;
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
   * Initiates a subscription for an org. Generates a PayFast checkout form with all required fields
   * and MD5 signature.
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
    if (!status.isSubscribable()) {
      throw new InvalidStateException(
          "Cannot subscribe", "Subscription status does not allow subscribing: " + status);
    }

    return platformPayFastService.generateCheckoutForm(org.getId());
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

    if (!subscription.getSubscriptionStatus().isCancellable()) {
      throw new InvalidStateException(
          "Cannot cancel", "Only ACTIVE subscriptions can be cancelled");
    }

    // Persist PENDING_CANCELLATION before calling PayFast — if the API call fails,
    // local state is safely PENDING_CANCELLATION (can be retried or reconciled).
    subscription.transitionTo(Subscription.SubscriptionStatus.PENDING_CANCELLATION);
    subscription.setCancelledAt(Instant.now());
    subscriptionRepository.save(subscription);

    statusCache.evict(org.getId());

    if (subscription.getPayfastToken() != null) {
      platformPayFastService.cancelPayFastSubscription(subscription.getPayfastToken());
    }

    long currentMembers = memberRepository.count();
    return BillingResponse.from(subscription, currentMembers, billingProperties);
  }

  /**
   * Returns paginated payment history for an org's subscription. Handles pageable construction,
   * size capping, default sort, and DTO mapping.
   */
  @Transactional(readOnly = true)
  public Page<PaymentResponse> getPayments(String clerkOrgId, int page, int size) {
    var org =
        organizationRepository
            .findByClerkOrgId(clerkOrgId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", clerkOrgId));

    var subscription =
        subscriptionRepository
            .findByOrganizationId(org.getId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Subscription", "organization " + org.getId()));

    var pageable =
        PageRequest.of(
            page, Math.min(size, MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC, "paymentDate"));
    return subscriptionPaymentRepository
        .findBySubscriptionId(subscription.getId(), pageable)
        .map(PaymentResponse::from);
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
