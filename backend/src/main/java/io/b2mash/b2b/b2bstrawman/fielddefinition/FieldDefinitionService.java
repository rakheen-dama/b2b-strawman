package io.b2mash.b2b.b2bstrawman.fielddefinition;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.CreateFieldDefinitionRequest;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.UpdateFieldDefinitionRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FieldDefinitionService {

  private static final Logger log = LoggerFactory.getLogger(FieldDefinitionService.class);

  private final FieldDefinitionRepository fieldDefinitionRepository;
  private final AuditService auditService;

  public FieldDefinitionService(
      FieldDefinitionRepository fieldDefinitionRepository, AuditService auditService) {
    this.fieldDefinitionRepository = fieldDefinitionRepository;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public List<FieldDefinitionResponse> listByEntityType(String entityType) {
    return fieldDefinitionRepository
        .findByEntityTypeAndActiveTrueOrderBySortOrder(entityType)
        .stream()
        .map(FieldDefinitionResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public FieldDefinitionResponse findById(UUID id) {
    var fd =
        fieldDefinitionRepository
            .findOneById(id)
            .orElseThrow(() -> new ResourceNotFoundException("FieldDefinition", id));
    return FieldDefinitionResponse.from(fd);
  }

  @Transactional
  public FieldDefinitionResponse create(CreateFieldDefinitionRequest request) {
    String baseSlug =
        request.slug() != null && !request.slug().isBlank()
            ? request.slug()
            : FieldDefinition.generateSlug(request.name());
    String finalSlug = resolveUniqueSlug(request.entityType(), baseSlug);

    var fd =
        new FieldDefinition(request.entityType(), request.name(), finalSlug, request.fieldType());
    fd.setDescription(request.description());
    if (request.required()) {
      fd.updateMetadata(request.name(), request.description(), true, request.validation());
    } else if (request.validation() != null) {
      fd.updateMetadata(request.name(), request.description(), false, request.validation());
    }
    fd.setDefaultValue(request.defaultValue());
    fd.setOptions(request.options());
    fd.setSortOrder(request.sortOrder());

    fd = fieldDefinitionRepository.save(fd);

    log.info(
        "Created field definition: id={}, entityType={}, slug={}",
        fd.getId(),
        fd.getEntityType(),
        fd.getSlug());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("field_definition.created")
            .entityType("field_definition")
            .entityId(fd.getId())
            .details(
                Map.of(
                    "entity_type", fd.getEntityType(),
                    "name", fd.getName(),
                    "slug", fd.getSlug(),
                    "field_type", fd.getFieldType()))
            .build());

    return FieldDefinitionResponse.from(fd);
  }

  @Transactional
  public FieldDefinitionResponse update(UUID id, UpdateFieldDefinitionRequest request) {
    var fd =
        fieldDefinitionRepository
            .findOneById(id)
            .orElseThrow(() -> new ResourceNotFoundException("FieldDefinition", id));

    fd.updateMetadata(
        request.name(), request.description(), request.required(), request.validation());
    fd.setDefaultValue(request.defaultValue());
    fd.setOptions(request.options());
    fd.setSortOrder(request.sortOrder());

    fd = fieldDefinitionRepository.save(fd);

    log.info("Updated field definition: id={}, name={}", fd.getId(), fd.getName());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("field_definition.updated")
            .entityType("field_definition")
            .entityId(fd.getId())
            .details(Map.of("name", fd.getName()))
            .build());

    return FieldDefinitionResponse.from(fd);
  }

  @Transactional
  public void deactivate(UUID id) {
    var fd =
        fieldDefinitionRepository
            .findOneById(id)
            .orElseThrow(() -> new ResourceNotFoundException("FieldDefinition", id));

    fd.deactivate();
    fieldDefinitionRepository.save(fd);

    log.info("Deactivated field definition: id={}, slug={}", fd.getId(), fd.getSlug());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("field_definition.deleted")
            .entityType("field_definition")
            .entityId(fd.getId())
            .details(Map.of("slug", fd.getSlug()))
            .build());
  }

  private String resolveUniqueSlug(String entityType, String baseSlug) {
    String finalSlug = baseSlug;
    int suffix = 2;
    while (fieldDefinitionRepository.findByEntityTypeAndSlug(entityType, finalSlug).isPresent()) {
      finalSlug = baseSlug + "_" + suffix;
      suffix++;
    }
    return finalSlug;
  }
}
