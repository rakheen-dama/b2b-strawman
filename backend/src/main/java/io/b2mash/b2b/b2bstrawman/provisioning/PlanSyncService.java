package io.b2mash.b2b.b2bstrawman.provisioning;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanSyncService {

  private static final Logger log = LoggerFactory.getLogger(PlanSyncService.class);

  private final OrganizationRepository organizationRepository;
  private final TenantFilter tenantFilter;

  public PlanSyncService(OrganizationRepository organizationRepository, TenantFilter tenantFilter) {
    this.organizationRepository = organizationRepository;
    this.tenantFilter = tenantFilter;
  }

  /**
   * Updates the organization's tier and plan slug. Returns the result indicating whether a tier
   * change occurred (STARTER → PRO transition). All tenants use dedicated schemas, so no schema
   * migration is required on tier change.
   */
  @Transactional
  public PlanSyncResult syncPlan(String externalOrgId, String planSlug) {
    var org =
        organizationRepository
            .findByExternalOrgId(externalOrgId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", externalOrgId));

    Tier previousTier = org.getTier();
    Tier newTier = deriveTier(planSlug);
    org.updatePlan(newTier, planSlug);
    organizationRepository.save(org);

    tenantFilter.evictSchema(externalOrgId);

    boolean upgradeNeeded = previousTier == Tier.STARTER && newTier == Tier.PRO;
    log.info(
        "Plan synced: externalOrgId={}, tier={} → {}, planSlug={}, upgradeNeeded={}",
        externalOrgId,
        previousTier,
        newTier,
        planSlug,
        upgradeNeeded);

    return new PlanSyncResult(previousTier, newTier, upgradeNeeded);
  }

  public record PlanSyncResult(Tier previousTier, Tier newTier, boolean upgradeNeeded) {}

  private Tier deriveTier(String planSlug) {
    if (planSlug != null && planSlug.toLowerCase().contains("pro")) {
      return Tier.PRO;
    }
    return Tier.STARTER;
  }
}
