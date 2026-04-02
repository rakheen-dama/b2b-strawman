package io.b2mash.b2b.b2bstrawman.billing;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanLimits;
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

  public SubscriptionService(
      SubscriptionRepository subscriptionRepository,
      OrganizationRepository organizationRepository,
      MemberRepository memberRepository) {
    this.subscriptionRepository = subscriptionRepository;
    this.organizationRepository = organizationRepository;
    this.memberRepository = memberRepository;
  }

  /** Creates a TRIALING subscription for a newly provisioned org. Idempotent. */
  @Transactional
  public void createSubscription(UUID organizationId) {
    if (subscriptionRepository.findByOrganizationId(organizationId).isPresent()) {
      log.info("Subscription already exists for organization {}", organizationId);
      return;
    }
    subscriptionRepository.save(new Subscription(organizationId));
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
      var sub = subscription.get();
      return new BillingResponse(
          sub.getSubscriptionStatus().name(),
          new BillingResponse.Limits(PlanLimits.DEFAULT_MAX_MEMBERS, currentMembers));
    }

    // Defensive: synthetic TRIALING response if subscription row is missing
    return new BillingResponse(
        "TRIALING", new BillingResponse.Limits(PlanLimits.DEFAULT_MAX_MEMBERS, currentMembers));
  }

  /**
   * Billing response DTO. The {@code tier} and {@code planSlug} fields are retained for
   * backward-compatibility with the frontend until Epic 426 (Frontend Cleanup) removes them.
   */
  public record BillingResponse(String status, Limits limits, String tier, String planSlug) {

    /** Compact constructor — fills backward-compat fields with sensible defaults. */
    public BillingResponse(String status, Limits limits) {
      this(status, limits, "pro", "pro");
    }

    public record Limits(int maxMembers, long currentMembers) {}
  }
}
