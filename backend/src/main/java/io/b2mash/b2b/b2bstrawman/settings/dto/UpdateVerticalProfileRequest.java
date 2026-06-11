package io.b2mash.b2b.b2bstrawman.settings.dto;

import jakarta.validation.constraints.Size;

/** Request to switch the firm's active vertical profile. */
public record UpdateVerticalProfileRequest(
    @Size(max = 50, message = "verticalProfile must be at most 50 characters")
        String verticalProfile) {}
