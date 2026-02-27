package io.b2mash.b2b.b2bstrawman.clause.dto;

import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ClausePreviewRequest(
    @NotNull(message = "entityId is required") UUID entityId,
    @NotNull(message = "entityType is required") TemplateEntityType entityType) {}
