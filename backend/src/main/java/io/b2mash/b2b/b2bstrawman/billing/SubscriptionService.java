package io.b2mash.b2b.b2bstrawman.billing;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanLimits;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService.PlanSyncResult;
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
  private final PlanSyncService planSyncService;
  private final MemberRepository memberRepository;

  public SubscriptionService(
      SubscriptionRepository subscriptionRepository,
      OrganizationRepository organizationRepository,
      PlanSyncService planSyncService,
      MemberRepository memberRepository) {
    this.subscriptionRepository = subscriptionRepository;
    this.organizationRepository = organizationRepository;
    this.planSyncService = planSyncService;
    this.memberRepository = memberRepository;
  }

  /** Creates an ACTIVE subscription for a newly provisioned org. Idempotent. */
  @Transactional
  public void createSubscription(UUID organizationId, String planSlug) {
    if (subscriptionRepository.findByOrganizationId(organizationId).isPresent()) {
      log.info("Subscription already exists for organization {}", organizationId);
      return;
    }
    subscriptionRepository.save(new Subscription(organizationId, planSlug));
    log.info("Created {} subscription for organization {}", planSlug, organizationId);
  }

  /**
   * Updates the subscription plan and delegates to PlanSyncService for tier resolution + org
   * update. Returns PlanSyncResult so the caller can trigger upgrade if needed.
   */
  @Transactional
  public PlanSyncResult changePlan(String clerkOrgId, String planSlug) {
    var org =
        organizationRepository
            .findByClerkOrgId(clerkOrgId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", clerkOrgId));

    var subscription =
        subscriptionRepository
            .findByOrganizationId(org.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Subscription", clerkOrgId));

    subscription.changePlan(planSlug);
    subscriptionRepository.save(subscription);

    log.info("Updated subscription plan to {} for org {}", planSlug, clerkOrgId);
    return planSyncService.syncPlan(clerkOrgId, planSlug);
  }

  /**
   * Returns billing info for an org. Must be called within tenant context (for member count).
   * Returns a synthetic STARTER response if no subscription exists (defensive).
   */
  @Transactional(readOnly = true)
  public BillingResponse getSubscription(String clerkOrgId) {
    var org =
        organizationRepository
            .findByClerkOrgId(clerkOrgId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", clerkOrgId));

    var subscription = subscriptionRepository.findByOrganizationId(org.getId());
    long currentMembers = memberRepository.count();
    int maxMembers = PlanLimits.maxMembers(org.getTier());

    if (subscription.isPresent()) {
      var sub = subscription.get();
      return new BillingResponse(
          sub.getPlanSlug(),
          org.getTier().name(),
          sub.getStatus().name(),
          new BillingResponse.Limits(maxMembers, currentMembers));
    }

    // Defensive: synthetic STARTER response if subscription row is missing
    return new BillingResponse(
        "starter",
        org.getTier().name(),
        "ACTIVE",
        new BillingResponse.Limits(maxMembers, currentMembers));
  }

  public record BillingResponse(String planSlug, String tier, String status, Limits limits) {

    public record Limits(int maxMembers, long currentMembers) {}
  }
}
