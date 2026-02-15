package io.b2mash.b2b.b2bstrawman.tag;

import io.b2mash.b2b.b2bstrawman.tag.dto.TagResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Shared utility methods for tag-based filtering on list endpoints. */
public final class TagFilterUtil {

  private TagFilterUtil() {}

  /** Extracts tag slugs from the "tags" query parameter (comma-separated). */
  public static List<String> extractTagSlugs(Map<String, String> allParams) {
    if (allParams == null || !allParams.containsKey("tags")) {
      return List.of();
    }
    String tagsParam = allParams.get("tags");
    if (tagsParam == null || tagsParam.isBlank()) {
      return List.of();
    }
    return Arrays.stream(tagsParam.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  /** Returns true if the entity's tags contain ALL required slugs (AND logic). */
  public static boolean matchesTagFilter(List<TagResponse> entityTags, List<String> requiredSlugs) {
    if (entityTags == null) {
      return false;
    }
    Set<String> tagSlugs = entityTags.stream().map(TagResponse::slug).collect(Collectors.toSet());
    return tagSlugs.containsAll(requiredSlugs);
  }
}
