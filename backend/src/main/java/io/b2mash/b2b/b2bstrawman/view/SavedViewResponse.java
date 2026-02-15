package io.b2mash.b2b.b2bstrawman.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SavedViewResponse(
    UUID id,
    String entityType,
    String name,
    Map<String, Object> filters,
    List<String> columns,
    boolean shared,
    UUID createdBy,
    int sortOrder,
    Instant createdAt,
    Instant updatedAt) {

  public static SavedViewResponse from(SavedView v) {
    return new SavedViewResponse(
        v.getId(),
        v.getEntityType(),
        v.getName(),
        v.getFilters(),
        v.getColumns(),
        v.isShared(),
        v.getCreatedBy(),
        v.getSortOrder(),
        v.getCreatedAt(),
        v.getUpdatedAt());
  }
}
