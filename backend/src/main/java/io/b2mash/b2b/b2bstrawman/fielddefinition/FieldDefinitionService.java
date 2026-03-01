package io.b2mash.b2b.b2bstrawman.fielddefinition;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.CreateFieldDefinitionRequest;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.UpdateFieldDefinitionRequest;
import io.b2mash.b2b.b2bstrawman.prerequisite.PrerequisiteContext;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FieldDefinitionService {

  private static final Logger log = LoggerFactory.getLogger(FieldDefinitionService.class);

  private final FieldDefinitionRepository fieldDefinitionRepository;
  private final AuditService auditService;
  private final EntityManager entityManager;
  private final FieldGroupRepository fieldGroupRepository;
  private final FieldGroupMemberRepository fieldGroupMemberRepository;

  public FieldDefinitionService(
      FieldDefinitionRepository fieldDefinitionRepository,
      AuditService auditService,
      EntityManager entityManager,
      FieldGroupRepository fieldGroupRepository,
      FieldGroupMemberRepository fieldGroupMemberRepository) {
    this.fieldDefinitionRepository = fieldDefinitionRepository;
    this.auditService = auditService;
    this.entityManager = entityManager;
    this.fieldGroupRepository = fieldGroupRepository;
    this.fieldGroupMemberRepository = fieldGroupMemberRepository;
  }

  public record IntakeFieldGroup(UUID id, String name, String slug, List<FieldDefinition> fields) {}

  @Transactional(readOnly = true)
  public List<FieldDefinition> getRequiredFieldsForContext(
      EntityType entityType, PrerequisiteContext context) {
    return fieldDefinitionRepository.findRequiredForContext(
        entityType.name(), "[\"" + context.name() + "\"]");
  }

  @Transactional(readOnly = true)
  public List<IntakeFieldGroup> getIntakeFields(EntityType entityType) {
    var autoApplyGroups =
        fieldGroupRepository.findByEntityTypeAndAutoApplyTrueAndActiveTrue(entityType);
    var result = new ArrayList<IntakeFieldGroup>();
    for (var group : autoApplyGroups) {
      var memberList = fieldGroupMemberRepository.findByFieldGroupIdOrderBySortOrder(group.getId());
      var ids = memberList.stream().map(FieldGroupMember::getFieldDefinitionId).toList();
      var definitionsById =
          fieldDefinitionRepository.findAllById(ids).stream()
              .collect(Collectors.toMap(FieldDefinition::getId, fd -> fd));
      var fields =
          memberList.stream()
              .map(m -> definitionsById.get(m.getFieldDefinitionId()))
              .filter(Objects::nonNull)
              .collect(Collectors.toList());
      result.add(new IntakeFieldGroup(group.getId(), group.getName(), group.getSlug(), fields));
    }
    return result;
  }

  @Transactional(readOnly = true)
  public List<FieldDefinitionResponse> listByEntityType(EntityType entityType) {
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
            .findById(id)
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
    fd.updateMetadata(
        request.name(), request.description(), request.required(), request.validation());
    fd.setDefaultValue(request.defaultValue());
    fd.setOptions(request.options());
    fd.setSortOrder(request.sortOrder());
    validateVisibilityCondition(request.visibilityCondition(), request.entityType(), finalSlug);
    fd.setVisibilityCondition(request.visibilityCondition());

    try {
      fd = fieldDefinitionRepository.save(fd);
    } catch (DataIntegrityViolationException ex) {
      throw new ResourceConflictException(
          "Duplicate slug", "A field definition with slug '" + finalSlug + "' already exists");
    }

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
                    "entity_type", fd.getEntityType().name(),
                    "name", fd.getName(),
                    "slug", fd.getSlug(),
                    "field_type", fd.getFieldType().name()))
            .build());

    return FieldDefinitionResponse.from(fd);
  }

  @Transactional
  public FieldDefinitionResponse update(UUID id, UpdateFieldDefinitionRequest request) {
    var fd =
        fieldDefinitionRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("FieldDefinition", id));

    // Field type immutability check
    if (request.fieldType() != null && request.fieldType() != fd.getFieldType()) {
      checkFieldTypeChangeAllowed(fd);
      fd.setFieldType(request.fieldType());
    }

    fd.updateMetadata(
        request.name(), request.description(), request.required(), request.validation());
    fd.setDefaultValue(request.defaultValue());
    fd.setOptions(request.options());
    fd.setSortOrder(request.sortOrder());
    validateVisibilityCondition(request.visibilityCondition(), fd.getEntityType(), fd.getSlug());
    fd.setVisibilityCondition(request.visibilityCondition());

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
            .findById(id)
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

  private static final Set<String> VALID_OPERATORS = Set.of("eq", "neq", "in");

  private void validateVisibilityCondition(
      Map<String, Object> condition, EntityType entityType, String ownSlug) {
    if (condition == null) {
      return;
    }

    var dependsOnSlug = condition.get("dependsOnSlug");
    if (!(dependsOnSlug instanceof String slug)) {
      throw new InvalidStateException(
          "Invalid visibility condition", "dependsOnSlug must be a non-null string");
    }

    var operator = condition.get("operator");
    if (!(operator instanceof String op) || !VALID_OPERATORS.contains(op)) {
      throw new InvalidStateException(
          "Invalid visibility condition", "operator must be one of: eq, neq, in");
    }

    var value = condition.get("value");
    if (value == null) {
      throw new InvalidStateException("Invalid visibility condition", "value must not be null");
    }
    if ("in".equals(op) && !(value instanceof List<?>)) {
      throw new InvalidStateException(
          "Invalid visibility condition", "value must be a list for the 'in' operator");
    }

    if (slug.equals(ownSlug)) {
      throw new InvalidStateException(
          "Invalid visibility condition", "A field cannot depend on itself");
    }

    var target = fieldDefinitionRepository.findByEntityTypeAndSlug(entityType, slug);
    if (target.isEmpty() || !target.get().isActive()) {
      throw new InvalidStateException(
          "Invalid visibility condition",
          "dependsOnSlug '"
              + slug
              + "' does not reference an active field of the same entity type");
    }
  }

  private String resolveUniqueSlug(EntityType entityType, String baseSlug) {
    String finalSlug = baseSlug;
    int suffix = 2;
    while (fieldDefinitionRepository.findByEntityTypeAndSlug(entityType, finalSlug).isPresent()) {
      finalSlug = baseSlug + "_" + suffix;
      suffix++;
    }
    return finalSlug;
  }

  private void checkFieldTypeChangeAllowed(FieldDefinition fd) {
    String tableName = entityTableName(fd.getEntityType());
    var query =
        entityManager.createNativeQuery(
            "SELECT EXISTS (SELECT 1 FROM "
                + tableName
                + " WHERE custom_fields ->> :slug IS NOT NULL)");
    query.setParameter("slug", fd.getSlug());
    Boolean exists = (Boolean) query.getSingleResult();
    if (Boolean.TRUE.equals(exists)) {
      throw new InvalidStateException(
          "Field type cannot be changed",
          "Field type cannot be changed after values exist. Create a new field instead.");
    }
  }

  // Safe: closed enum switch — no SQL injection risk from user input.
  private String entityTableName(EntityType entityType) {
    return switch (entityType) {
      case CUSTOMER -> "customers";
      case PROJECT -> "projects";
      case TASK -> "tasks";
      case INVOICE -> "invoices";
    };
  }
}
