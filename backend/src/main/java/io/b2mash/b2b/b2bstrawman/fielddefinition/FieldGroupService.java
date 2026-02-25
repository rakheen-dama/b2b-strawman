package io.b2mash.b2b.b2bstrawman.fielddefinition;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.CreateFieldGroupRequest;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldGroupResponse;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.UpdateFieldGroupRequest;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
  private final EntityManager entityManager;

  public FieldGroupService(
      FieldGroupRepository fieldGroupRepository,
      FieldDefinitionRepository fieldDefinitionRepository,
      FieldGroupMemberRepository fieldGroupMemberRepository,
      AuditService auditService,
      EntityManager entityManager) {
    this.fieldGroupRepository = fieldGroupRepository;
    this.fieldDefinitionRepository = fieldDefinitionRepository;
    this.fieldGroupMemberRepository = fieldGroupMemberRepository;
    this.auditService = auditService;
    this.entityManager = entityManager;
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
            .findById(id)
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
    fg.setAutoApply(request.autoApplyOrDefault());

    if (request.dependsOn() != null && !request.dependsOn().isEmpty()) {
      validateDependsOn(null, request.entityType(), request.dependsOn());
      fg.setDependsOn(request.dependsOn());
    }

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
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("FieldGroup", id));

    fg.updateMetadata(request.name(), request.description(), request.sortOrder());
    if (request.autoApply() != null) {
      fg.setAutoApply(request.autoApply());
    }

    if (request.dependsOn() != null) {
      validateDependsOn(fg.getId(), fg.getEntityType(), request.dependsOn());
      fg.setDependsOn(request.dependsOn().isEmpty() ? null : request.dependsOn());
    }

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
            .findById(id)
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
            .findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("FieldGroup", groupId));
    var field =
        fieldDefinitionRepository
            .findById(fieldId)
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
        .findById(groupId)
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
        .findById(groupId)
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
        .findById(groupId)
        .orElseThrow(() -> new ResourceNotFoundException("FieldGroup", groupId));

    return fieldGroupMemberRepository.findByFieldGroupIdOrderBySortOrder(groupId);
  }

  // --- Auto-apply methods ---

  /** Toggles the autoApply flag on a field group. When toggling to true, retroactively applies. */
  @Transactional
  public FieldGroupResponse toggleAutoApply(UUID groupId, boolean autoApply) {
    var group =
        fieldGroupRepository
            .findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("FieldGroup", groupId));

    boolean wasAutoApply = group.isAutoApply();
    group.setAutoApply(autoApply);
    group = fieldGroupRepository.save(group);

    // Retroactive apply: only when toggling from false to true
    if (!wasAutoApply && autoApply) {
      retroactiveApply(group);
    }

    log.info("Toggled autoApply on field group: id={}, autoApply={}", groupId, autoApply);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("field_group.auto_apply_toggled")
            .entityType("field_group")
            .entityId(group.getId())
            .details(Map.of("auto_apply", String.valueOf(autoApply)))
            .build());

    return FieldGroupResponse.from(group);
  }

  private void retroactiveApply(FieldGroup group) {
    String tableName = entityTableName(group.getEntityType());
    String groupIdJson = "[\"" + group.getId().toString() + "\"]";

    entityManager
        .createNativeQuery(
            "UPDATE "
                + tableName
                + " SET applied_field_groups = applied_field_groups || CAST(:groupJson AS jsonb)"
                + " WHERE NOT applied_field_groups @> CAST(:groupJson AS jsonb)")
        .setParameter("groupJson", groupIdJson)
        .executeUpdate();

    // Also retroactively apply dependency groups (for 161B — safe to include now, no-op if
    // dependsOn is null). Validate each dependency exists and is active before applying.
    if (group.getDependsOn() != null) {
      for (UUID depId : group.getDependsOn()) {
        var depGroup = fieldGroupRepository.findById(depId);
        if (depGroup.isEmpty() || !depGroup.get().isActive()) {
          log.warn(
              "Skipping invalid/inactive dependency group {} for group {}", depId, group.getId());
          continue;
        }
        String depIdJson = "[\"" + depId.toString() + "\"]";
        entityManager
            .createNativeQuery(
                "UPDATE "
                    + tableName
                    + " SET applied_field_groups = applied_field_groups || CAST(:depJson AS jsonb)"
                    + " WHERE applied_field_groups @> CAST(:groupJson AS jsonb)"
                    + " AND NOT applied_field_groups @> CAST(:depJson AS jsonb)")
            .setParameter("groupJson", groupIdJson)
            .setParameter("depJson", depIdJson)
            .executeUpdate();
      }
    }
  }

  // Safe: closed enum switch with default throw — no SQL injection risk from user input.
  private String entityTableName(EntityType entityType) {
    return switch (entityType) {
      case CUSTOMER -> "customers";
      case PROJECT -> "projects";
      case TASK -> "tasks";
      case INVOICE -> "invoices";
    };
  }

  /**
   * Resolves all group IDs that should be auto-applied to a new entity of the given type. Includes
   * direct auto-apply groups plus one level of dependsOn dependencies.
   */
  public List<UUID> resolveAutoApplyGroupIds(EntityType entityType) {
    var autoGroups = fieldGroupRepository.findByEntityTypeAndAutoApplyTrueAndActiveTrue(entityType);
    var groupIds = new LinkedHashSet<>(autoGroups.stream().map(FieldGroup::getId).toList());
    // Resolve one-level dependencies — only include existing, active groups
    for (var group : autoGroups) {
      if (group.getDependsOn() != null) {
        for (UUID depId : group.getDependsOn()) {
          var depGroup = fieldGroupRepository.findById(depId);
          if (depGroup.isPresent() && depGroup.get().isActive()) {
            groupIds.add(depId);
          } else {
            log.warn(
                "Skipping invalid/inactive dependency group {} for auto-apply group {}",
                depId,
                group.getId());
          }
        }
      }
    }
    return new ArrayList<>(groupIds);
  }

  // --- Dependency validation and resolution ---

  /**
   * Validates the dependsOn list for a field group.
   *
   * @param groupId The ID of the group being created/updated (null for create)
   * @param entityType The entity type of the group
   * @param dependsOn The list of dependency group IDs
   */
  private void validateDependsOn(UUID groupId, EntityType entityType, List<UUID> dependsOn) {
    if (dependsOn == null || dependsOn.isEmpty()) {
      return;
    }

    for (UUID depId : dependsOn) {
      // Self-reference check
      if (depId.equals(groupId)) {
        throw new InvalidStateException("Self-reference", "A field group cannot depend on itself");
      }

      // Existence and entity type check
      var depGroup =
          fieldGroupRepository
              .findById(depId)
              .orElseThrow(
                  () ->
                      new InvalidStateException(
                          "Invalid dependency", "Dependency group " + depId + " does not exist"));

      if (!depGroup.isActive()) {
        throw new InvalidStateException(
            "Invalid dependency", "Dependency group " + depId + " is not active");
      }

      if (depGroup.getEntityType() != entityType) {
        throw new InvalidStateException(
            "Invalid dependency",
            "Dependency group "
                + depId
                + " has entity type "
                + depGroup.getEntityType()
                + " but expected "
                + entityType);
      }

      // Mutual dependency check (A depends on B and B depends on A)
      if (groupId != null
          && depGroup.getDependsOn() != null
          && depGroup.getDependsOn().contains(groupId)) {
        throw new InvalidStateException(
            "Circular dependency",
            "Mutual dependency detected: group " + depId + " already depends on " + groupId);
      }
    }
  }

  /**
   * Resolves one-level dependencies for the given list of applied field groups. Returns the
   * expanded list including dependency group IDs.
   */
  public List<UUID> resolveDependencies(List<UUID> appliedFieldGroups) {
    var resolved = new LinkedHashSet<>(appliedFieldGroups);

    // Batch-fetch all applied groups in a single query
    var appliedGroups = fieldGroupRepository.findAllById(appliedFieldGroups);
    var groupMap = new java.util.HashMap<UUID, FieldGroup>();
    for (var g : appliedGroups) {
      groupMap.put(g.getId(), g);
    }

    // Collect all dependency IDs that need resolving
    var depIds = new LinkedHashSet<UUID>();
    for (UUID groupId : appliedFieldGroups) {
      var group = groupMap.get(groupId);
      if (group != null && group.getDependsOn() != null) {
        depIds.addAll(group.getDependsOn());
      }
    }

    // Batch-fetch all dependency groups and resolve from in-memory map
    if (!depIds.isEmpty()) {
      var depGroups = fieldGroupRepository.findAllById(depIds);
      for (var depGroup : depGroups) {
        if (depGroup.isActive()) {
          resolved.add(depGroup.getId());
        }
      }
    }

    return new ArrayList<>(resolved);
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
