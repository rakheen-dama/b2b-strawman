package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

import java.util.UUID;

/**
 * Published after a sync entry transitions to DEAD_LETTER. Integration-layer event for downstream
 * listeners (notifications, alerting).
 */
public record XeroSyncEntryDeadLetteredEvent(
    UUID syncEntryId, SyncEntityType entityType, UUID entityId, String errorCode) {}
