package io.b2mash.b2b.b2bstrawman.tag;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.tag.dto.CreateTagRequest;
import io.b2mash.b2b.b2bstrawman.tag.dto.TagResponse;
import io.b2mash.b2b.b2bstrawman.tag.dto.UpdateTagRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {

  private static final Logger log = LoggerFactory.getLogger(TagService.class);

  private final TagRepository tagRepository;
  private final AuditService auditService;

  public TagService(TagRepository tagRepository, AuditService auditService) {
    this.tagRepository = tagRepository;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public List<TagResponse> listAll() {
    return tagRepository.findByOrderByNameAsc().stream().map(TagResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public List<TagResponse> search(String prefix) {
    return tagRepository.findByNameStartingWithIgnoreCaseOrderByName(prefix).stream()
        .map(TagResponse::from)
        .toList();
  }

  @Transactional
  public TagResponse create(CreateTagRequest request) {
    String baseSlug = Tag.generateSlug(request.name());
    String finalSlug = resolveUniqueSlug(baseSlug);

    var tag = new Tag(request.name(), request.color());
    tag.setSlug(finalSlug);

    try {
      tag = tagRepository.save(tag);
    } catch (DataIntegrityViolationException ex) {
      throw new ResourceConflictException(
          "Duplicate slug", "A tag with slug '" + finalSlug + "' already exists");
    }

    log.info("Created tag: id={}, name={}, slug={}", tag.getId(), tag.getName(), tag.getSlug());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("tag.created")
            .entityType("tag")
            .entityId(tag.getId())
            .details(Map.of("name", tag.getName(), "slug", tag.getSlug()))
            .build());

    return TagResponse.from(tag);
  }

  @Transactional
  public TagResponse update(UUID id, UpdateTagRequest request) {
    var tag =
        tagRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Tag", id));

    tag.updateMetadata(request.name(), request.color());
    tag = tagRepository.save(tag);

    log.info("Updated tag: id={}, name={}", tag.getId(), tag.getName());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("tag.updated")
            .entityType("tag")
            .entityId(tag.getId())
            .details(Map.of("name", tag.getName()))
            .build());

    return TagResponse.from(tag);
  }

  @Transactional
  public void delete(UUID id) {
    var tag =
        tagRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Tag", id));

    tagRepository.delete(tag);

    log.info("Deleted tag: id={}, slug={}", tag.getId(), tag.getSlug());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("tag.deleted")
            .entityType("tag")
            .entityId(tag.getId())
            .details(Map.of("slug", tag.getSlug()))
            .build());
  }

  private String resolveUniqueSlug(String baseSlug) {
    String finalSlug = baseSlug;
    int suffix = 2;
    while (tagRepository.findBySlug(finalSlug).isPresent()) {
      finalSlug = baseSlug + "_" + suffix;
      suffix++;
    }
    return finalSlug;
  }
}
