package io.b2mash.b2b.b2bstrawman.crm.event;

import java.util.UUID;

/**
 * Published when a deal is marked LOST (Phase 80, slice 575A).
 *
 * @param dealId the lost deal
 * @param lostReason the required reason captured at loss
 * @param tenantId current tenant schema
 * @param orgId current org id
 */
public record DealLostEvent(UUID dealId, String lostReason, String tenantId, String orgId) {}
