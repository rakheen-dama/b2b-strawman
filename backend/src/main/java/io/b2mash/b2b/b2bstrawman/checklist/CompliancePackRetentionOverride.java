package io.b2mash.b2b.b2bstrawman.checklist;

/** Nested record for a retention policy override within a compliance pack. */
public record CompliancePackRetentionOverride(
    String recordType, String triggerEvent, int retentionDays, String action) {}
