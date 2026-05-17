package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

import java.util.UUID;

/**
 * Published after a sync entry transitions to COMPLETED. Integration-layer event for downstream
 * listeners (notifications, audit).
 */
public record XeroSyncEntryCompletedEvent(
    UUID syncEntryId, SyncEntityType entityType, UUID entityId, String externalId) {}
