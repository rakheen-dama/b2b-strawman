package io.b2mash.b2b.b2bstrawman.tag;

import io.b2mash.b2b.b2bstrawman.tag.dto.TagResponse;
import java.util.List;
import java.util.UUID;
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
      for (UUID tagId : tagIds) {
        var entityTag = new EntityTag(tagId, entityType, entityId);
        entityTagRepository.save(entityTag);
      }
    }

    return getEntityTags(entityType, entityId);
  }
}
