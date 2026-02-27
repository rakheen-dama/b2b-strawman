package io.b2mash.b2b.b2bstrawman.acceptance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateAcceptanceRequest(
    @NotNull(message = "generatedDocumentId is required") UUID generatedDocumentId,
    @NotNull(message = "portalContactId is required") UUID portalContactId,
    @Min(value = 1, message = "expiryDays must be at least 1")
        @Max(value = 365, message = "expiryDays must not exceed 365")
        Integer expiryDays) {}
