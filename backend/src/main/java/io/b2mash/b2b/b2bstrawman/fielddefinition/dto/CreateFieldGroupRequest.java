package io.b2mash.b2b.b2bstrawman.fielddefinition.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFieldGroupRequest(
    @NotBlank @Size(max = 20) String entityType,
    @NotBlank @Size(max = 100) String name,
    @Size(max = 100) String slug,
    String description,
    int sortOrder) {}
