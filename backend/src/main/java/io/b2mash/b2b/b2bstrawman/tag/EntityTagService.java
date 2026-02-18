package io.b2mash.b2b.b2bstrawman.tag;

import io.b2mash.b2b.b2bstrawman.tag.dto.TagResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EntityTagService {

  private final EntityTagRepository entityTagRepository;
  private final TagRepository tagRepository;

  public EntityTagService(EntityTagRepository entityTagRepository, TagRepository tagRepository) {
    this.entityTagRepository = entityTagRepository;
    this.tagRepository = tagRepository;
  }

  @Transactional(readOnly = true)
  public List<TagResponse> getEntityTags(String entityType, UUID entityId) {
    var entityTags = entityTagRepository.findByEntityTypeAndEntityId(entityType, entityId);
    if (entityTags.isEmpty()) {
      return List.of();
    }
    var tagIds = entityTags.stream().map(EntityTag::getTagId).toList();
    return tagRepository.findAllById(tagIds).stream()
        .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
        .map(TagResponse::from)
        .toList();
  }

  @Transactional
  public List<TagResponse> setEntityTags(String entityType, UUID entityId, List<UUID> tagIds) {
    entityTagRepository.deleteByEntityTypeAndEntityId(entityType, entityId);

    if (tagIds != null && !tagIds.isEmpty()) {
      // Validate that all tag IDs exist in the current tenant (filter-safe query)
      var validTags = tagRepository.findAllById(tagIds);
      var validTagIds = validTags.stream().map(Tag::getId).collect(Collectors.toSet());

      for (UUID tagId : tagIds) {
        if (validTagIds.contains(tagId)) {
          var entityTag = new EntityTag(tagId, entityType, entityId);
          entityTagRepository.save(entityTag);
        }
      }
    }

    return getEntityTags(entityType, entityId);
  }

  /**
   * Batch-loads tags for multiple entities in two queries instead of 2N queries. Returns a map of
   * entity ID to tag response list.
   */
  @Transactional(readOnly = true)
  public Map<UUID, List<TagResponse>> getEntityTagsBatch(String entityType, List<UUID> entityIds) {
    if (entityIds == null || entityIds.isEmpty()) {
      return Map.of();
    }

    // Single query: all entity_tags for the given entity type and IDs
    var allEntityTags = entityTagRepository.findByEntityTypeAndEntityIdIn(entityType, entityIds);
    if (allEntityTags.isEmpty()) {
      return entityIds.stream().collect(Collectors.toMap(id -> id, id -> List.of()));
    }

    // Collect all unique tag IDs
    var allTagIds = allEntityTags.stream().map(EntityTag::getTagId).distinct().toList();

    // Single query: all tags by IDs (filter-safe)
    var tagsById =
        tagRepository.findAllById(allTagIds).stream()
            .collect(Collectors.toMap(Tag::getId, tag -> tag));

    // Group by entity ID
    Map<UUID, List<TagResponse>> result =
        allEntityTags.stream()
            .collect(
                Collectors.groupingBy(
                    EntityTag::getEntityId,
                    Collectors.mapping(
                        et -> tagsById.get(et.getTagId()),
                        Collectors.filtering(
                            tag -> tag != null,
                            Collectors.collectingAndThen(
                                Collectors.toCollection(ArrayList::new),
                                tags -> {
                                  tags.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                                  return tags.stream().map(TagResponse::from).toList();
                                })))));

    // Ensure all requested IDs have an entry (even if empty)
    for (UUID entityId : entityIds) {
      result.putIfAbsent(entityId, List.of());
    }

    return result;
  }
}
