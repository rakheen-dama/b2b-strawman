package io.b2mash.b2b.b2bstrawman.compliance;

import java.util.UUID;

public record ComplianceFindingStatusChangedEvent(
    UUID findingId,
    UUID reportId,
    String findingIdCode,
    String oldStatus,
    String newStatus,
    UUID changedBy) {}
