package io.b2mash.b2b.b2bstrawman.fielddefinition.dto;

import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateFieldGroupRequest(
    @NotNull EntityType entityType,
    @NotBlank @Size(max = 100) String name,
    @Size(max = 100) String slug,
    String description,
    int sortOrder) {}
