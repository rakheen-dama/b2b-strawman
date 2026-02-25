package io.b2mash.b2b.b2bstrawman.project;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.ProjectCreatedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.ProjectUpdatedEvent;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.CustomFieldValidator;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupMemberRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupService;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.member.ProjectMember;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

  private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

  private final ProjectRepository repository;
  private final ProjectMemberRepository projectMemberRepository;
  private final ProjectAccessService projectAccessService;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final CustomFieldValidator customFieldValidator;
  private final FieldGroupRepository fieldGroupRepository;
  private final FieldGroupMemberRepository fieldGroupMemberRepository;
  private final FieldDefinitionRepository fieldDefinitionRepository;
  private final FieldGroupService fieldGroupService;

  public ProjectService(
      ProjectRepository repository,
      ProjectMemberRepository projectMemberRepository,
      ProjectAccessService projectAccessService,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      CustomFieldValidator customFieldValidator,
      FieldGroupRepository fieldGroupRepository,
      FieldGroupMemberRepository fieldGroupMemberRepository,
      FieldDefinitionRepository fieldDefinitionRepository,
      FieldGroupService fieldGroupService) {
    this.repository = repository;
    this.projectMemberRepository = projectMemberRepository;
    this.projectAccessService = projectAccessService;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.customFieldValidator = customFieldValidator;
    this.fieldGroupRepository = fieldGroupRepository;
    this.fieldGroupMemberRepository = fieldGroupMemberRepository;
    this.fieldDefinitionRepository = fieldDefinitionRepository;
    this.fieldGroupService = fieldGroupService;
  }

  @Transactional(readOnly = true)
  public List<ProjectWithRole> listProjects(UUID memberId, String orgRole) {
    if (Roles.ORG_OWNER.equals(orgRole) || Roles.ORG_ADMIN.equals(orgRole)) {
      return repository.findAllProjectsWithRole(memberId);
    }
    return repository.findProjectsForMember(memberId);
  }

  @Transactional(readOnly = true)
  public ProjectWithRole getProject(UUID id, UUID memberId, String orgRole) {
    var project =
        repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Project", id));
    var access = projectAccessService.requireViewAccess(id, memberId, orgRole);
    return new ProjectWithRole(project, access.projectRole());
  }

  @Transactional
  public Project createProject(String name, String description, UUID createdBy) {
    return createProject(name, description, createdBy, null, null);
  }

  @Transactional
  public Project createProject(
      String name,
      String description,
      UUID createdBy,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups) {
    // Validate custom fields
    Map<String, Object> validatedFields =
        customFieldValidator.validate(
            EntityType.PROJECT,
            customFields != null ? customFields : new HashMap<>(),
            appliedFieldGroups);

    var project = repository.save(new Project(name, description, createdBy));
    project.setCustomFields(validatedFields);
    if (appliedFieldGroups != null) {
      project.setAppliedFieldGroups(appliedFieldGroups);
    }
    project = repository.save(project);

    // Auto-apply field groups
    var autoApplyIds = fieldGroupService.resolveAutoApplyGroupIds(EntityType.PROJECT);
    if (!autoApplyIds.isEmpty()) {
      var merged =
          new ArrayList<>(
              project.getAppliedFieldGroups() != null
                  ? project.getAppliedFieldGroups()
                  : List.of());
      for (UUID id : autoApplyIds) {
        if (!merged.contains(id)) {
          merged.add(id);
        }
      }
      project.setAppliedFieldGroups(merged);
      project = repository.save(project);
    }

    var lead = new ProjectMember(project.getId(), createdBy, Roles.PROJECT_LEAD, null);
    projectMemberRepository.save(lead);
    log.info("Created project {} with lead member {}", project.getId(), createdBy);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("project.created")
            .entityType("project")
            .entityId(project.getId())
            .details(Map.of("name", project.getName()))
            .build());

    String tenantId = RequestScopes.getTenantIdOrNull();
    String orgId = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new ProjectCreatedEvent(
            project.getId(), project.getName(), project.getDescription(), null, orgId, tenantId));

    return project;
  }

  @Transactional
  public ProjectWithRole updateProject(
      UUID id, String name, String description, UUID memberId, String orgRole) {
    return updateProject(id, name, description, memberId, orgRole, null, null);
  }

  @Transactional
  public ProjectWithRole updateProject(
      UUID id,
      String name,
      String description,
      UUID memberId,
      String orgRole,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups) {
    var project =
        repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Project", id));
    var access = projectAccessService.requireEditAccess(id, memberId, orgRole);

    // Capture old values before mutation
    String oldName = project.getName();
    String oldDescription = project.getDescription();

    // Validate and set custom fields
    if (customFields != null) {
      Map<String, Object> validatedFields =
          customFieldValidator.validate(
              EntityType.PROJECT,
              customFields,
              appliedFieldGroups != null ? appliedFieldGroups : project.getAppliedFieldGroups());
      project.setCustomFields(validatedFields);
    }
    if (appliedFieldGroups != null) {
      project.setAppliedFieldGroups(appliedFieldGroups);
    }

    project.update(name, description);
    project = repository.save(project);

    // Build delta map -- only include changed fields
    var details = new LinkedHashMap<String, Object>();
    if (!Objects.equals(oldName, name)) {
      details.put("name", Map.of("from", oldName, "to", name));
    }
    if (!Objects.equals(oldDescription, description)) {
      details.put(
          "description",
          Map.of(
              "from", oldDescription != null ? oldDescription : "",
              "to", description != null ? description : ""));
    }

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("project.updated")
            .entityType("project")
            .entityId(project.getId())
            .details(details.isEmpty() ? null : details)
            .build());

    String tenantId = RequestScopes.getTenantIdOrNull();
    String orgId = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new ProjectUpdatedEvent(
            project.getId(), project.getName(), project.getDescription(), null, orgId, tenantId));

    return new ProjectWithRole(project, access.projectRole());
  }

  @Transactional
  public List<FieldDefinitionResponse> setFieldGroups(
      UUID id, List<UUID> appliedFieldGroups, UUID memberId, String orgRole) {
    var project =
        repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Project", id));
    projectAccessService.requireEditAccess(id, memberId, orgRole);

    // Validate all field groups exist and match entity type
    for (UUID groupId : appliedFieldGroups) {
      var group =
          fieldGroupRepository
              .findById(groupId)
              .orElseThrow(() -> new ResourceNotFoundException("FieldGroup", groupId));
      if (group.getEntityType() != EntityType.PROJECT) {
        throw new io.b2mash.b2b.b2bstrawman.exception.InvalidStateException(
            "Invalid field group", "Field group " + groupId + " is not for entity type PROJECT");
      }
    }

    project.setAppliedFieldGroups(appliedFieldGroups);
    repository.save(project);

    // Collect field definition IDs from applied groups
    var fieldDefIds = new ArrayList<UUID>();
    for (UUID groupId : appliedFieldGroups) {
      var members = fieldGroupMemberRepository.findByFieldGroupIdOrderBySortOrder(groupId);
      for (var member : members) {
        fieldDefIds.add(member.getFieldDefinitionId());
      }
    }

    // Load and return field definitions
    return fieldDefIds.stream()
        .distinct()
        .map(fdId -> fieldDefinitionRepository.findById(fdId))
        .filter(java.util.Optional::isPresent)
        .map(java.util.Optional::get)
        .map(FieldDefinitionResponse::from)
        .toList();
  }

  @Transactional
  public void deleteProject(UUID id) {
    var project =
        repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Project", id));
    repository.delete(project);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("project.deleted")
            .entityType("project")
            .entityId(project.getId())
            .details(Map.of("name", project.getName()))
            .build());
  }
}
