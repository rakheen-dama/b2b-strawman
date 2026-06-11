package io.b2mash.b2b.b2bstrawman.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request to update core org settings (currency, branding, module flags, project naming). */
public record UpdateSettingsRequest(
    @NotBlank(message = "defaultCurrency is required")
        @Size(min = 3, max = 3, message = "defaultCurrency must be exactly 3 characters")
        String defaultCurrency,
    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "brandColor must be a valid hex color")
        String brandColor,
    String documentFooterText,
    Boolean accountingEnabled,
    Boolean aiEnabled,
    Boolean documentSigningEnabled,
    @Size(max = 500, message = "projectNamingPattern must be at most 500 characters")
        String projectNamingPattern) {}
