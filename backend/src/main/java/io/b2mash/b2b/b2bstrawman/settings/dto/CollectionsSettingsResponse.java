package io.b2mash.b2b.b2bstrawman.settings.dto;

/**
 * Read model for the collections / dunning policy group (Phase 83, §4.2). Carries the enable flag
 * and the four strictly-increasing days-overdue thresholds.
 */
public record CollectionsSettingsResponse(
    boolean collectionsEnabled,
    Integer stage1DaysOverdue,
    Integer stage2DaysOverdue,
    Integer stage3DaysOverdue,
    Integer escalateDaysOverdue) {}
