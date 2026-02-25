package io.b2mash.b2b.b2bstrawman.fielddefinition.dto;

import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroup;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FieldGroupResponse(
    UUID id,
    EntityType entityType,
    String name,
    String slug,
    String description,
    String packId,
    int sortOrder,
    boolean active,
    boolean autoApply,
    List<UUID> dependsOn,
    Instant createdAt,
    Instant updatedAt) {

  public static FieldGroupResponse from(FieldGroup fg) {
    return new FieldGroupResponse(
        fg.getId(),
        fg.getEntityType(),
        fg.getName(),
        fg.getSlug(),
        fg.getDescription(),
        fg.getPackId(),
        fg.getSortOrder(),
        fg.isActive(),
        fg.isAutoApply(),
        fg.getDependsOn(),
        fg.getCreatedAt(),
        fg.getUpdatedAt());
  }
}
