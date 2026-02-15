package io.b2mash.b2b.b2bstrawman.fielddefinition.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record CreateFieldDefinitionRequest(
    @NotBlank @Size(max = 20) String entityType,
    @NotBlank @Size(max = 100) String name,
    @Size(max = 100) String slug,
    @NotBlank @Size(max = 20) String fieldType,
    String description,
    boolean required,
    Map<String, Object> defaultValue,
    List<Map<String, String>> options,
    Map<String, Object> validation,
    int sortOrder) {}
