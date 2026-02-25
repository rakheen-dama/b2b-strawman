package io.b2mash.b2b.b2bstrawman.fielddefinition.dto;

import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record CreateFieldDefinitionRequest(
    @NotNull EntityType entityType,
    @NotBlank @Size(max = 100) String name,
    @Size(max = 100) String slug,
    @NotNull FieldType fieldType,
    String description,
    boolean required,
    Map<String, Object> defaultValue,
    List<Map<String, String>> options,
    Map<String, Object> validation,
    int sortOrder,
    Map<String, Object> visibilityCondition) {}
