package io.b2mash.b2b.b2bstrawman.crm.event;

import java.util.UUID;

/**
 * Published when a deal moves between OPEN stages or is re-opened into an OPEN stage (Phase 80,
 * slice 575A).
 *
 * @param dealId the deal that moved
 * @param stageId the target (new) pipeline stage
 * @param tenantId current tenant schema
 * @param orgId current org id
 */
public record DealStageChangedEvent(UUID dealId, UUID stageId, String tenantId, String orgId) {}
