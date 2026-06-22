package io.b2mash.b2b.b2bstrawman.crm.event;

import java.util.UUID;

/**
 * Published (within the transition transaction) when a deal is marked WON. The DEAL_WON
 * notification to the owner is sent post-commit by {@link DealWonEventHandler} (Phase 80, slice
 * 575A).
 *
 * @param dealId the won deal
 * @param customerId the deal's customer (nudged PROSPECT→ONBOARDING by the transition service)
 * @param ownerId the deal owner notified post-commit
 * @param tenantId current tenant schema (re-bound by the AFTER_COMMIT handler)
 * @param orgId current org id (re-bound by the AFTER_COMMIT handler)
 * @param shardId the tenant's shard, captured at publish time so the AFTER_COMMIT handler can
 *     re-bind shard-aware scope (D5 / ADR-T008 — new code must route to the correct shard)
 */
public record DealWonEvent(
    UUID dealId, UUID customerId, UUID ownerId, String tenantId, String orgId, String shardId) {}
