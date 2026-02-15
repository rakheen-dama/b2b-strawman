package io.b2mash.b2b.b2bstrawman.fielddefinition.dto;

import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record FieldDefinitionResponse(
    UUID id,
    EntityType entityType,
    String name,
    String slug,
    FieldType fieldType,
    String description,
    boolean required,
    Map<String, Object> defaultValue,
    List<Map<String, String>> options,
    Map<String, Object> validation,
    int sortOrder,
    String packId,
    String packFieldKey,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static FieldDefinitionResponse from(FieldDefinition fd) {
    return new FieldDefinitionResponse(
        fd.getId(),
        fd.getEntityType(),
        fd.getName(),
        fd.getSlug(),
        fd.getFieldType(),
        fd.getDescription(),
        fd.isRequired(),
        fd.getDefaultValue(),
        fd.getOptions(),
        fd.getValidation(),
        fd.getSortOrder(),
        fd.getPackId(),
        fd.getPackFieldKey(),
        fd.isActive(),
        fd.getCreatedAt(),
        fd.getUpdatedAt());
  }
}
