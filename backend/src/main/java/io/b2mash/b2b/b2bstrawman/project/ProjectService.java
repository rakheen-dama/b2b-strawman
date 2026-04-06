package io.b2mash.b2b.b2bstrawman.project;

import io.b2mash.b2b.b2bstrawman.audit.AuditDeltaBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.CustomerProjectLinkedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.ProjectCreatedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.ProjectUpdatedEvent;
import io.b2mash.b2b.b2bstrawman.event.ProjectArchivedEvent;
import io.b2mash.b2b.b2bstrawman.event.ProjectCompletedEvent;
import io.b2mash.b2b.b2bstrawman.event.ProjectReopenedEvent;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.member.ProjectMember;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.task.TaskStatus;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
  private final MemberNameResolver memberNameResolver;
  private final TaskRepository taskRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final ProjectFieldService projectFieldService;
  private final ProjectDeletionGuard projectDeletionGuard;
  private final CustomerProjectRepository customerProjectRepository;

  public ProjectService(
      ProjectRepository repository,
      ProjectMemberRepository projectMemberRepository,
      ProjectAccessService projectAccessService,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      MemberNameResolver memberNameResolver,
      TaskRepository taskRepository,
      TimeEntryRepository timeEntryRepository,
      ProjectFieldService projectFieldService,
      ProjectDeletionGuard projectDeletionGuard,
      CustomerProjectRepository customerProjectRepository) {
    this.repository = repository;
    this.projectMemberRepository = projectMemberRepository;
    this.projectAccessService = projectAccessService;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.memberNameResolver = memberNameResolver;
    this.taskRepository = taskRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.projectFieldService = projectFieldService;
    this.projectDeletionGuard = projectDeletionGuard;
    this.customerProjectRepository = customerProjectRepository;
  }

  @Transactional(readOnly = true)
  public List<ProjectWithRole> listProjects(ActorContext actor) {
    if (Roles.ORG_OWNER.equals(actor.orgRole()) || Roles.ORG_ADMIN.equals(actor.orgRole())) {
      return repository.findAllProjectsWithRole(actor.memberId());
    }
    return repository.findProjectsForMember(actor.memberId());
  }

  @Transactional(readOnly = true)
  public ProjectWithRole getProject(UUID id, ActorContext actor) {
    var project =
        repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Project", id));
    var access = projectAccessService.requireViewAccess(id, actor);
    return new ProjectWithRole(project, access.projectRole());
  }

  @Transactional
  public Project createProject(String name, String description, UUID createdBy) {
    return createProject(name, description, createdBy, null, null, null, null);
  }

  @Transactional
  public Project createProject(
      String name,
      String description,
      UUID createdBy,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups) {
    return createProject(
        name, description, createdBy, customFields, appliedFieldGroups, null, null);
  }

  @Transactional
  public Project createProject(
      String name,
      String description,
      UUID createdBy,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups,
      UUID customerId,
      LocalDate dueDate) {
    // Validate customer link and resolve customer name for naming pattern
    String customerName = null;
    if (customerId != null) {
      customerName = projectFieldService.resolveCustomerName(customerId);
    }

    // Validate fields, apply naming pattern, resolve auto-apply groups
    var fieldResult =
        projectFieldService.prepareForCreate(name, customFields, appliedFieldGroups, customerName);

    var project = new Project(fieldResult.resolvedName(), description, createdBy);
    project.setCustomFields(fieldResult.validatedFields());
    project.setAppliedFieldGroups(fieldResult.mergedFieldGroups());
    if (customerId != null) {
      project.setCustomerId(customerId);
    }
    if (dueDate != null) {
      project.setDueDate(dueDate);
    }

    project = repository.save(project);

    var lead = new ProjectMember(project.getId(), createdBy, Roles.PROJECT_LEAD, null);
    projectMemberRepository.save(lead);

    // Create CustomerProject join record for consistency with CustomerProjectRepository queries
    if (customerId != null) {
      customerProjectRepository.save(new CustomerProject(customerId, project.getId(), createdBy));

      // Publish event for portal read model sync
      String tenantIdForLink = RequestScopes.getTenantIdOrNull();
      String orgIdForLink = RequestScopes.getOrgIdOrNull();
      eventPublisher.publishEvent(
          new CustomerProjectLinkedEvent(
              customerId, project.getId(), orgIdForLink, tenantIdForLink));
    }

    log.info("Created project {} with lead member {}", project.getId(), createdBy);

    var auditDetails = new LinkedHashMap<String, Object>();
    auditDetails.put("name", project.getName());
    if (customerId != null) {
      auditDetails.put("customerId", customerId.toString());
    }
    if (dueDate != null) {
      auditDetails.put("dueDate", dueDate.toString());
    }

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("project.created")
            .entityType("project")
            .entityId(project.getId())
            .details(auditDetails)
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
      UUID id, String name, String description, ActorContext actor) {
    return updateProject(id, name, description, actor, null, null, null, null);
  }

  @Transactional
  public ProjectWithRole updateProject(
      UUID id,
      String name,
      String description,
      ActorContext actor,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups) {
    return updateProject(
        id, name, description, actor, customFields, appliedFieldGroups, null, null);
  }

  @Transactional
  public ProjectWithRole updateProject(
      UUID id,
      String name,
      String description,
      ActorContext actor,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups,
      UUID customerId,
      LocalDate dueDate) {
    var project =
        repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Project", id));
    var access = projectAccessService.requireEditAccess(id, actor);

    // Validate customer link if provided
    if (customerId != null) {
      projectFieldService.validateCustomerLink(customerId);
    }

    // Capture old values before mutation
    String oldName = project.getName();
    String oldDescription = project.getDescription();
    UUID oldCustomerId = project.getCustomerId();
    LocalDate oldDueDate = project.getDueDate();

    // Validate and set custom fields
    if (customFields != null) {
      Map<String, Object> validatedFields =
          projectFieldService.validateFields(
              customFields,
              appliedFieldGroups != null ? appliedFieldGroups : project.getAppliedFieldGroups());
      project.setCustomFields(validatedFields);
    }
    if (appliedFieldGroups != null) {
      project.setAppliedFieldGroups(appliedFieldGroups);
    }

    // Use Project.update() which applies null-means-no-change convention
    project.update(name, description, customerId, dueDate);
    project = repository.save(project);

    // Create CustomerProject join record if customerId was set/changed and no link exists yet
    UUID newCustomerId = project.getCustomerId();
    if (newCustomerId != null
        && !customerProjectRepository.existsByCustomerIdAndProjectId(newCustomerId, id)) {
      customerProjectRepository.save(new CustomerProject(newCustomerId, id, actor.memberId()));

      // Publish event for portal read model sync
      String tenantIdForLink = RequestScopes.getTenantIdOrNull();
      String orgIdForLink = RequestScopes.getOrgIdOrNull();
      eventPublisher.publishEvent(
          new CustomerProjectLinkedEvent(newCustomerId, id, orgIdForLink, tenantIdForLink));
    }

    // Build delta map -- only include changed fields (use actual values from entity)
    LocalDate newDueDate = project.getDueDate();
    var details =
        new AuditDeltaBuilder()
            .track("name", oldName, name)
            .track("description", oldDescription, description)
            .trackAsString("customerId", oldCustomerId, newCustomerId)
            .trackAsString("dueDate", oldDueDate, newDueDate)
            .build();

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("project.updated")
            .entityType("project")
            .entityId(project.getId())
            .details(details)
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
      UUID id, List<UUID> appliedFieldGroups, ActorContext actor) {
    var project =
        repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Project", id));
    projectAccessService.requireEditAccess(id, actor);

    var result = projectFieldService.setFieldGroups(project, appliedFieldGroups);
    repository.save(project);

    return result;
  }

  @Transactional
  public void deleteProject(UUID id) {
    RequestScopes.requireOwner();
    var project =
        repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Project", id));

    // Guard 1: Status check — only ACTIVE projects can be deleted
    if (project.getStatus() != ProjectStatus.ACTIVE) {
      throw new ResourceConflictException(
          "Cannot delete project",
          "Cannot delete a %s project. Reopen the project first."
              .formatted(project.getStatus().name().toLowerCase()));
    }

    projectDeletionGuard.checkAndExecute(id);

    repository.delete(project);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("project.deleted")
            .entityType("project")
            .entityId(project.getId())
            .details(Map.of("name", project.getName()))
            .build());
  }

  // --- Project lifecycle methods (Epic 204A) ---

  @Transactional
  public Project completeProject(
      UUID projectId, boolean acknowledgeUnbilledTime, ActorContext actor) {
    var project =
        repository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

    projectAccessService.requireEditAccess(projectId, actor);

    // Guardrail 1: open tasks
    long openTaskCount =
        taskRepository.countByProjectIdAndStatusNotIn(
            projectId, List.of(TaskStatus.DONE, TaskStatus.CANCELLED));
    if (openTaskCount > 0) {
      throw new InvalidStateException(
          "Cannot complete project",
          "Project has %d open task(s). Complete or cancel all tasks before completing the project."
              .formatted(openTaskCount));
    }

    // Guardrail 2: unbilled time
    var unbilledSummary = timeEntryRepository.countUnbilledByProjectId(projectId);
    long unbilledCount = unbilledSummary.getEntryCount();
    double unbilledHours = unbilledSummary.getTotalHours();
    if (unbilledCount > 0 && !acknowledgeUnbilledTime) {
      throw new ResourceConflictException(
          "Unbilled time entries",
          "Project has %d unbilled time entry/entries (%.1f hours). Acknowledge to proceed."
              .formatted(unbilledCount, unbilledHours));
    }

    project.complete(actor.memberId());
    project = repository.save(project);
    log.info("Project {} completed by member {}", projectId, actor.memberId());

    var auditDetails = new LinkedHashMap<String, Object>();
    auditDetails.put("name", project.getName());
    auditDetails.put("completed_by", actor.memberId().toString());
    if (unbilledCount > 0 && acknowledgeUnbilledTime) {
      auditDetails.put("unbilled_time_waived", true);
      auditDetails.put("unbilled_entry_count", unbilledCount);
      auditDetails.put("unbilled_hours", unbilledHours);
    }

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("project.completed")
            .entityType("project")
            .entityId(project.getId())
            .details(auditDetails)
            .build());

    String actorName = memberNameResolver.resolveName(actor.memberId());
    String tenantId = RequestScopes.requireTenantId();
    String orgId = RequestScopes.requireOrgId();
    eventPublisher.publishEvent(
        new ProjectCompletedEvent(
            "project.completed",
            "project",
            project.getId(),
            project.getId(),
            actor.memberId(),
            actorName,
            tenantId,
            orgId,
            Instant.now(),
            Map.of("name", project.getName()),
            actor.memberId(),
            project.getName(),
            unbilledCount > 0 && acknowledgeUnbilledTime,
            RequestScopes.AUTOMATION_EXECUTION_ID.isBound()
                ? RequestScopes.AUTOMATION_EXECUTION_ID.get()
                : null));

    return project;
  }

  @Transactional
  public Project archiveProject(UUID projectId, ActorContext actor) {
    var project =
        repository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

    projectAccessService.requireEditAccess(projectId, actor);

    project.archive(actor.memberId());
    project = repository.save(project);
    log.info("Project {} archived by member {}", projectId, actor.memberId());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("project.archived")
            .entityType("project")
            .entityId(project.getId())
            .details(
                Map.of(
                    "name", project.getName(),
                    "archived_by", actor.memberId().toString()))
            .build());

    String actorName = memberNameResolver.resolveName(actor.memberId());
    String tenantId = RequestScopes.requireTenantId();
    String orgId = RequestScopes.requireOrgId();
    eventPublisher.publishEvent(
        new ProjectArchivedEvent(
            "project.archived",
            "project",
            project.getId(),
            project.getId(),
            actor.memberId(),
            actorName,
            tenantId,
            orgId,
            Instant.now(),
            Map.of("name", project.getName()),
            actor.memberId(),
            project.getName()));

    return project;
  }

  @Transactional
  public Project reopenProject(UUID projectId, ActorContext actor) {
    var project =
        repository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

    projectAccessService.requireEditAccess(projectId, actor);

    String previousStatus = project.getStatus().name();

    project.reopen();
    project = repository.save(project);
    log.info("Project {} reopened by member {}", projectId, actor.memberId());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("project.reopened")
            .entityType("project")
            .entityId(project.getId())
            .details(
                Map.of(
                    "name", project.getName(),
                    "reopened_by", actor.memberId().toString(),
                    "previous_status", previousStatus))
            .build());

    String actorName = memberNameResolver.resolveName(actor.memberId());
    String tenantId = RequestScopes.requireTenantId();
    String orgId = RequestScopes.requireOrgId();
    eventPublisher.publishEvent(
        new ProjectReopenedEvent(
            "project.reopened",
            "project",
            project.getId(),
            project.getId(),
            actor.memberId(),
            actorName,
            tenantId,
            orgId,
            Instant.now(),
            Map.of("name", project.getName()),
            actor.memberId(),
            project.getName(),
            previousStatus));

    return project;
  }

  // --- Name Resolution (moved from controller for BE-007) ---

  /**
   * Batch-loads member names for all createdBy and completedBy IDs referenced by the given
   * projects.
   */
  public Map<UUID, String> resolveProjectMemberNames(List<Project> projects) {
    var ids =
        projects.stream()
            .flatMap(p -> java.util.stream.Stream.of(p.getCreatedBy(), p.getCompletedBy()))
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();

    if (ids.isEmpty()) {
      return Map.of();
    }

    return memberNameResolver.resolveNames(ids);
  }
}
