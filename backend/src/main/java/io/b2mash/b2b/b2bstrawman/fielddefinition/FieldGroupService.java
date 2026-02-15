package io.b2mash.b2b.b2bstrawman.fielddefinition;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
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
  private final FieldDefinitionRepository fieldDefinitionRepository;
  private final FieldGroupMemberRepository fieldGroupMemberRepository;
  private final AuditService auditService;

  public FieldGroupService(
      FieldGroupRepository fieldGroupRepository,
      FieldDefinitionRepository fieldDefinitionRepository,
      FieldGroupMemberRepository fieldGroupMemberRepository,
      AuditService auditService) {
    this.fieldGroupRepository = fieldGroupRepository;
    this.fieldDefinitionRepository = fieldDefinitionRepository;
    this.fieldGroupMemberRepository = fieldGroupMemberRepository;
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

  // --- Membership methods ---

  /**
   * Adds a field definition to a field group. Validates that the field's entityType matches the
   * group's entityType.
   */
  @Transactional
  public void addFieldToGroup(UUID groupId, UUID fieldId, int sortOrder) {
    var group =
        fieldGroupRepository
            .findOneById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("FieldGroup", groupId));
    var field =
        fieldDefinitionRepository
            .findOneById(fieldId)
            .orElseThrow(() -> new ResourceNotFoundException("FieldDefinition", fieldId));

    if (field.getEntityType() != group.getEntityType()) {
      throw new InvalidStateException(
          "Entity type mismatch",
          "Field definition entity type '"
              + field.getEntityType()
              + "' does not match group entity type '"
              + group.getEntityType()
              + "'");
    }

    var member = new FieldGroupMember(groupId, fieldId, sortOrder);
    try {
      fieldGroupMemberRepository.saveAndFlush(member);
    } catch (DataIntegrityViolationException ex) {
      throw new ResourceConflictException(
          "Duplicate membership",
          "Field definition " + fieldId + " is already a member of group " + groupId);
    }

    log.info("Added field {} to group {} at sortOrder {}", fieldId, groupId, sortOrder);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("field_group_member.added")
            .entityType("field_group")
            .entityId(groupId)
            .details(
                Map.of("field_definition_id", fieldId.toString(), "sort_order", sortOrder + ""))
            .build());
  }

  /** Removes a field definition from a field group. */
  @Transactional
  public void removeFieldFromGroup(UUID groupId, UUID fieldId) {
    fieldGroupRepository
        .findOneById(groupId)
        .orElseThrow(() -> new ResourceNotFoundException("FieldGroup", groupId));

    fieldGroupMemberRepository.deleteByFieldGroupIdAndFieldDefinitionId(groupId, fieldId);

    log.info("Removed field {} from group {}", fieldId, groupId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("field_group_member.removed")
            .entityType("field_group")
            .entityId(groupId)
            .details(Map.of("field_definition_id", fieldId.toString()))
            .build());
  }

  /** Reorders fields within a group based on position in the provided list. */
  @Transactional
  public void reorderFields(UUID groupId, List<UUID> fieldIds) {
    fieldGroupRepository
        .findOneById(groupId)
        .orElseThrow(() -> new ResourceNotFoundException("FieldGroup", groupId));

    var members = fieldGroupMemberRepository.findByFieldGroupIdOrderBySortOrder(groupId);
    var memberByFieldId = new java.util.HashMap<UUID, FieldGroupMember>();
    for (var member : members) {
      memberByFieldId.put(member.getFieldDefinitionId(), member);
    }

    for (int i = 0; i < fieldIds.size(); i++) {
      var member = memberByFieldId.get(fieldIds.get(i));
      if (member != null) {
        member.setSortOrder(i);
        fieldGroupMemberRepository.save(member);
      }
    }

    log.info("Reordered {} fields in group {}", fieldIds.size(), groupId);
  }

  /** Returns all members of a group, ordered by sortOrder. */
  @Transactional(readOnly = true)
  public List<FieldGroupMember> getGroupMembers(UUID groupId) {
    fieldGroupRepository
        .findOneById(groupId)
        .orElseThrow(() -> new ResourceNotFoundException("FieldGroup", groupId));

    return fieldGroupMemberRepository.findByFieldGroupIdOrderBySortOrder(groupId);
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
