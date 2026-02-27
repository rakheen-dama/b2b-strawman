package io.b2mash.b2b.b2bstrawman.clause.dto;

import io.b2mash.b2b.b2bstrawman.clause.Clause;
import io.b2mash.b2b.b2bstrawman.clause.ClauseSource;
import java.time.Instant;
import java.util.UUID;

public record ClauseResponse(
    UUID id,
    String title,
    String slug,
    String description,
    String body,
    String category,
    ClauseSource source,
    UUID sourceClauseId,
    String packId,
    boolean active,
    int sortOrder,
    Instant createdAt,
    Instant updatedAt) {

  public static ClauseResponse from(Clause clause) {
    return new ClauseResponse(
        clause.getId(),
        clause.getTitle(),
        clause.getSlug(),
        clause.getDescription(),
        clause.getBody(),
        clause.getCategory(),
        clause.getSource(),
        clause.getSourceClauseId(),
        clause.getPackId(),
        clause.isActive(),
        clause.getSortOrder(),
        clause.getCreatedAt(),
        clause.getUpdatedAt());
  }
}
