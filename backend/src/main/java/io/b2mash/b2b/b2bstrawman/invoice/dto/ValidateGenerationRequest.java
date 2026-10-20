package io.b2mash.b2b.b2bstrawman.invoice.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ValidateGenerationRequest(
    @NotNull UUID clientId, List<UUID> timeEntryIds, UUID templateId) {}
