package io.b2mash.b2b.b2bstrawman.schedule.event;

import java.time.Instant;
import java.util.UUID;

public record ScheduleSkippedEvent(
    UUID scheduleId,
    String templateName,
    String customerName,
    String lifecycleStatus,
    String reason,
    String tenantId,
    String orgId,
    Instant occurredAt) {}
