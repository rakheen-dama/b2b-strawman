package io.b2mash.b2b.b2bstrawman.retention;

import jakarta.validation.constraints.PositiveOrZero;

public record RetentionPolicyUpdateRequest(
    @PositiveOrZero Integer retentionDays, String action, Boolean enabled, String description) {}
