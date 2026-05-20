package io.b2mash.b2b.b2bstrawman.integration.accounting.xero.dto;

import io.b2mash.b2b.b2bstrawman.integration.accounting.sync.AccountingSyncEntry;
import io.b2mash.b2b.b2bstrawman.integration.accounting.sync.SyncDirection;
import io.b2mash.b2b.b2bstrawman.integration.accounting.sync.SyncEntityType;
import io.b2mash.b2b.b2bstrawman.integration.accounting.sync.SyncState;
import io.b2mash.b2b.b2bstrawman.integration.accounting.sync.SyncTrigger;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for individual sync entry details. Maps from {@link AccountingSyncEntry} entity
 * fields.
 */
public record SyncEntryResponse(
    UUID id,
    SyncEntityType entityType,
    UUID entityId,
    String providerId,
    SyncDirection direction,
    SyncState state,
    int attemptCount,
    String externalReference,
    String externalId,
    String lastErrorCode,
    String lastErrorDetail,
    SyncTrigger trigger,
    Instant createdAt,
    Instant completedAt) {

  public static SyncEntryResponse from(AccountingSyncEntry entry) {
    return new SyncEntryResponse(
        entry.getId(),
        entry.getEntityType(),
        entry.getEntityId(),
        entry.getProviderId(),
        entry.getDirection(),
        entry.getState(),
        entry.getAttemptCount(),
        entry.getExternalReference(),
        entry.getExternalId(),
        entry.getLastErrorCode(),
        entry.getLastErrorDetail(),
        entry.getTrigger(),
        entry.getCreatedAt(),
        entry.getCompletedAt());
  }
}
