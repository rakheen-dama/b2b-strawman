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

  @Transactional
  public void syncPlan(String clerkOrgId, String planSlug) {
    var org =
        organizationRepository
            .findByClerkOrgId(clerkOrgId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", clerkOrgId));

    Tier tier = deriveTier(planSlug);
    org.updatePlan(tier, planSlug);
    organizationRepository.save(org);

    tenantFilter.evictSchema(clerkOrgId);

    log.info("Plan synced: clerkOrgId={}, tier={}, planSlug={}", clerkOrgId, tier, planSlug);
  }

  private Tier deriveTier(String planSlug) {
    if (planSlug != null && planSlug.toLowerCase().contains("pro")) {
      return Tier.PRO;
    }
    return Tier.STARTER;
  }
}
