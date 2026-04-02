package io.b2mash.b2b.b2bstrawman.provisioning;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PlanSyncService previously managed Tier/plan_slug syncing. The Tier concept was removed in Epic
 * 419A — subscription lifecycle is now managed via SubscriptionStatus. This service is retained as
 * a stub to avoid breaking callers; full lifecycle logic is in Epic 420.
 */
@Service
public class PlanSyncService {

  private static final Logger log = LoggerFactory.getLogger(PlanSyncService.class);

  private final TenantFilter tenantFilter;

  public PlanSyncService(TenantFilter tenantFilter) {
    this.tenantFilter = tenantFilter;
  }

  /** No-op stub. Plan syncing is replaced by subscription status transitions (Epic 420). */
  @Transactional
  public void syncPlan(String clerkOrgId, String planSlug) {
    tenantFilter.evictSchema(clerkOrgId);
    log.info(
        "Plan sync called for org {} with planSlug={} (no-op — Tier model removed)",
        clerkOrgId,
        planSlug);
  }
}
