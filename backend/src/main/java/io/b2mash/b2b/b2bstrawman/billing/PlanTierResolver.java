package io.b2mash.b2b.b2bstrawman.billing;

import io.b2mash.b2b.b2bstrawman.exception.MissingOrganizationContextException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Stub plan-tier source used by Phase 70 specialist visibility. Maps the existing {@link
 * SubscriptionStatusCache} status to a {@link PlanTier} so callers don't have to reason about
 * billing internals.
 *
 * <p>NOTE: this is a deliberate placeholder. There is no first-class {@code PlanTier} column on
 * {@link Subscription} today; a future billing slice will introduce one and replace this resolver.
 * Until then we map "write-enabled" subscription states (TRIALING, ACTIVE, PENDING_CANCELLATION,
 * PAST_DUE) to {@link PlanTier#PRO} and everything else to {@link PlanTier#STARTER}. This matches
 * the way AI features are otherwise gated.
 */
@Service
public class PlanTierResolver {

  private final SubscriptionStatusCache subscriptionStatusCache;
  private final OrganizationRepository organizationRepository;

  public PlanTierResolver(
      SubscriptionStatusCache subscriptionStatusCache,
      OrganizationRepository organizationRepository) {
    this.subscriptionStatusCache = subscriptionStatusCache;
    this.organizationRepository = organizationRepository;
  }

  /** Resolves the plan tier for the given organization id. */
  public PlanTier resolveForOrganization(UUID organizationId) {
    var status = subscriptionStatusCache.getStatus(organizationId);
    return status.isWriteEnabled() ? PlanTier.PRO : PlanTier.STARTER;
  }

  /**
   * Resolves the plan tier for the current request's organization (looked up via {@link
   * RequestScopes#requireOrgId()}).
   */
  public PlanTier resolveForCurrentOrg() {
    var clerkOrgId = RequestScopes.requireOrgId();
    var org =
        organizationRepository
            .findByClerkOrgId(clerkOrgId)
            .orElseThrow(MissingOrganizationContextException::new);
    return resolveForOrganization(org.getId());
  }
}
