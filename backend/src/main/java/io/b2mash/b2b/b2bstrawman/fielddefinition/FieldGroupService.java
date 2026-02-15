package io.b2mash.b2b.b2bstrawman.fielddefinition;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.CreateFieldGroupRequest;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldGroupResponse;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.UpdateFieldGroupRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FieldGroupService {

  private static final Logger log = LoggerFactory.getLogger(FieldGroupService.class);

  private final FieldGroupRepository fieldGroupRepository;
  private final AuditService auditService;

  public FieldGroupService(FieldGroupRepository fieldGroupRepository, AuditService auditService) {
    this.fieldGroupRepository = fieldGroupRepository;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public List<FieldGroupResponse> listByEntityType(EntityType entityType) {
    return fieldGroupRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(entityType).stream()
        .map(FieldGroupResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public FieldGroupResponse findById(UUID id) {
    var fg =
        fieldGroupRepository
            .findOneById(id)
            .orElseThrow(() -> new ResourceNotFoundException("FieldGroup", id));
    return FieldGroupResponse.from(fg);
  }

  @Transactional
  public FieldGroupResponse create(CreateFieldGroupRequest request) {
    String baseSlug =
        request.slug() != null && !request.slug().isBlank()
            ? request.slug()
            : FieldDefinition.generateSlug(request.name());
    String finalSlug = resolveUniqueSlug(request.entityType(), baseSlug);

    var fg = new FieldGroup(request.entityType(), request.name(), finalSlug);
    fg.setDescription(request.description());
    fg.setSortOrder(request.sortOrder());

    try {
      fg = fieldGroupRepository.save(fg);
    } catch (DataIntegrityViolationException ex) {
      throw new ResourceConflictException(
          "Duplicate slug", "A field group with slug '" + finalSlug + "' already exists");
    }

    log.info(
        "Created field group: id={}, entityType={}, slug={}",
        fg.getId(),
        fg.getEntityType(),
        fg.getSlug());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("field_group.created")
            .entityType("field_group")
            .entityId(fg.getId())
            .details(
                Map.of(
                    "entity_type", fg.getEntityType().name(),
                    "name", fg.getName(),
                    "slug", fg.getSlug()))
            .build());

    return FieldGroupResponse.from(fg);
  }

  @Transactional
  public FieldGroupResponse update(UUID id, UpdateFieldGroupRequest request) {
    var fg =
        fieldGroupRepository
            .findOneById(id)
            .orElseThrow(() -> new ResourceNotFoundException("FieldGroup", id));

    fg.updateMetadata(request.name(), request.description(), request.sortOrder());

    fg = fieldGroupRepository.save(fg);

    log.info("Updated field group: id={}, name={}", fg.getId(), fg.getName());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("field_group.updated")
            .entityType("field_group")
            .entityId(fg.getId())
            .details(Map.of("name", fg.getName()))
            .build());

    return FieldGroupResponse.from(fg);
  }

  @Transactional
  public void deactivate(UUID id) {
    var fg =
        fieldGroupRepository
            .findOneById(id)
            .orElseThrow(() -> new ResourceNotFoundException("FieldGroup", id));

    fg.deactivate();
    fieldGroupRepository.save(fg);

    log.info("Deactivated field group: id={}, slug={}", fg.getId(), fg.getSlug());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("field_group.deleted")
            .entityType("field_group")
            .entityId(fg.getId())
            .details(Map.of("slug", fg.getSlug()))
            .build());
  }

  private String resolveUniqueSlug(EntityType entityType, String baseSlug) {
    String finalSlug = baseSlug;
    int suffix = 2;
    while (fieldGroupRepository.findByEntityTypeAndSlug(entityType, finalSlug).isPresent()) {
      finalSlug = baseSlug + "_" + suffix;
      suffix++;
    }
    return finalSlug;
  }
}
