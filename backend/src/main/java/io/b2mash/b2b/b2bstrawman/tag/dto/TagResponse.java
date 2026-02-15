package io.b2mash.b2b.b2bstrawman.tag.dto;

import io.b2mash.b2b.b2bstrawman.tag.Tag;
import java.time.Instant;
import java.util.UUID;

public record TagResponse(
    UUID id, String name, String slug, String color, Instant createdAt, Instant updatedAt) {

  public static TagResponse from(Tag tag) {
    return new TagResponse(
        tag.getId(),
        tag.getName(),
        tag.getSlug(),
        tag.getColor(),
        tag.getCreatedAt(),
        tag.getUpdatedAt());
  }
}
