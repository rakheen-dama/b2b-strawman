package io.b2mash.b2b.b2bstrawman.fielddefinition.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record AddFieldToGroupRequest(
    @NotNull UUID fieldDefinitionId, @PositiveOrZero int sortOrder) {}
