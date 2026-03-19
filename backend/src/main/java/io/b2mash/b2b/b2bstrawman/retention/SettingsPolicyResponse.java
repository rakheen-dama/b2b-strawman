package io.b2mash.b2b.b2bstrawman.retention;

import java.time.Instant;
import java.util.UUID;

public record SettingsPolicyResponse(
    UUID id,
    String recordType,
    int retentionDays,
    String triggerEvent,
    String action,
    boolean active,
    String description,
    Instant lastEvaluatedAt,
    Instant createdAt,
    Instant updatedAt) {

  public static SettingsPolicyResponse from(RetentionPolicy p) {
    return new SettingsPolicyResponse(
        p.getId(),
        p.getRecordType(),
        p.getRetentionDays(),
        p.getTriggerEvent(),
        p.getAction(),
        p.isActive(),
        p.getDescription(),
        p.getLastEvaluatedAt(),
        p.getCreatedAt(),
        p.getUpdatedAt());
  }
}
