package io.b2mash.b2b.b2bstrawman.settings.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request to update the collections / dunning policy (Phase 83, §4.2). Full-replace semantics — all
 * five fields are meaningful on every call (PUT, not PATCH), so every field is {@code @NotNull}
 * (boxed {@code Boolean} included — a primitive would silently default to {@code false} when the
 * field is omitted). The {@code @Min(1)} floor is a bean pre-check; the strictly-increasing rule
 * (stage1 &lt; stage2 &lt; stage3 &lt; escalate) is enforced in {@code OrgSettingsService} so the
 * whole invariant lives in one place.
 */
public record UpdateCollectionsSettingsRequest(
    @NotNull Boolean collectionsEnabled,
    @NotNull @Min(value = 1, message = "stage1DaysOverdue must be at least 1")
        Integer stage1DaysOverdue,
    @NotNull @Min(value = 1, message = "stage2DaysOverdue must be at least 1")
        Integer stage2DaysOverdue,
    @NotNull @Min(value = 1, message = "stage3DaysOverdue must be at least 1")
        Integer stage3DaysOverdue,
    @NotNull @Min(value = 1, message = "escalateDaysOverdue must be at least 1")
        Integer escalateDaysOverdue) {}
