package io.b2mash.b2b.b2bstrawman.fielddefinition;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Shared utility for validating, resolving dependencies, and collecting field definitions from
 * applied field groups. Eliminates duplicated logic across CustomerService, ProjectService,
 * TaskService, and InvoiceService.
 */
@Component
public class FieldGroupResolver {

  private final FieldGroupRepository fieldGroupRepository;
  private final FieldGroupMemberRepository fieldGroupMemberRepository;
  private final FieldDefinitionRepository fieldDefinitionRepository;
  private final FieldGroupService fieldGroupService;

  public FieldGroupResolver(
      FieldGroupRepository fieldGroupRepository,
      FieldGroupMemberRepository fieldGroupMemberRepository,
      FieldDefinitionRepository fieldDefinitionRepository,
      FieldGroupService fieldGroupService) {
    this.fieldGroupRepository = fieldGroupRepository;
    this.fieldGroupMemberRepository = fieldGroupMemberRepository;
    this.fieldDefinitionRepository = fieldDefinitionRepository;
    this.fieldGroupService = fieldGroupService;
  }

  /**
   * Validates that all group IDs exist and match the expected entity type, resolves one-level
   * dependencies, and returns the resolved list of group IDs.
   *
   * @param groupIds the field group IDs to validate
   * @param entityType the expected entity type for all groups
   * @return the resolved list including dependency groups
   */
  public List<UUID> resolveAndValidate(List<UUID> groupIds, EntityType entityType) {
    for (UUID groupId : groupIds) {
      var group =
          fieldGroupRepository
              .findById(groupId)
              .orElseThrow(() -> new ResourceNotFoundException("FieldGroup", groupId));
      if (group.getEntityType() != entityType) {
        throw new InvalidStateException(
            "Invalid field group",
            "Field group " + groupId + " is not for entity type " + entityType.name());
      }
    }
    return fieldGroupService.resolveDependencies(groupIds);
  }

  /**
   * Collects distinct field definitions from the given group IDs and returns them as response DTOs.
   *
   * @param groupIds the resolved field group IDs
   * @return the list of field definition responses
   */
  public List<FieldDefinitionResponse> collectFieldDefinitions(List<UUID> groupIds) {
    var fieldDefIds = new ArrayList<UUID>();
    for (UUID groupId : groupIds) {
      var members = fieldGroupMemberRepository.findByFieldGroupIdOrderBySortOrder(groupId);
      for (var member : members) {
        fieldDefIds.add(member.getFieldDefinitionId());
      }
    }

    return fieldDefIds.stream()
        .distinct()
        .map(fdId -> fieldDefinitionRepository.findById(fdId))
        .filter(java.util.Optional::isPresent)
        .map(java.util.Optional::get)
        .map(FieldDefinitionResponse::from)
        .toList();
  }
}
