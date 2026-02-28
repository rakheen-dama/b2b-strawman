package io.b2mash.b2b.b2bstrawman.project;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.compliance.CustomerLifecycleGuard;
import io.b2mash.b2b.b2bstrawman.compliance.LifecycleAction;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.ProjectCreatedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.ProjectUpdatedEvent;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.event.ProjectArchivedEvent;
import io.b2mash.b2b.b2bstrawman.event.ProjectCompletedEvent;
import io.b2mash.b2b.b2bstrawman.event.ProjectReopenedEvent;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.CustomFieldValidator;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupMemberRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupService;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.member.ProjectMember;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.task.TaskStatus;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.time.Instant;
import java.time.LocalDate;
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
  private final TaskRepository taskRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final MemberNameResolver memberNameResolver;
  private final CustomerRepository customerRepository;
  private final CustomerLifecycleGuard customerLifecycleGuard;
  private final DocumentRepository documentRepository;
  private final InvoiceRepository invoiceRepository;

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
      FieldGroupService fieldGroupService,
      TaskRepository taskRepository,
      TimeEntryRepository timeEntryRepository,
      MemberNameResolver memberNameResolver,
      CustomerRepository customerRepository,
      CustomerLifecycleGuard customerLifecycleGuard,
      DocumentRepository documentRepository,
      InvoiceRepository invoiceRepository) {
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
    this.taskRepository = taskRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.memberNameResolver = memberNameResolver;
    this.customerRepository = customerRepository;
    this.customerLifecycleGuard = customerLifecycleGuard;
    this.documentRepository = documentRepository;
    this.invoiceRepository = invoiceRepository;
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
    // Validate customer link
    if (customerId != null) {
      var customer =
          customerRepository
              .findById(customerId)
              .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
      customerLifecycleGuard.requireActionPermitted(customer, LifecycleAction.CREATE_PROJECT);
    }

    // Validate custom fields
    Map<String, Object> validatedFields =
        customFieldValidator.validate(
            EntityType.PROJECT,
            customFields != null ? customFields : new HashMap<>(),
            appliedFieldGroups);

    var project = new Project(name, description, createdBy);
    project.setCustomFields(validatedFields);
    if (appliedFieldGroups != null) {
      project.setAppliedFieldGroups(appliedFieldGroups);
    }
    if (customerId != null) {
      project.setCustomerId(customerId);
    }
    if (dueDate != null) {
      project.setDueDate(dueDate);
    }

    // Auto-apply field groups before save so audit events capture final state
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
    }
    project = repository.save(project);

    var lead = new ProjectMember(project.getId(), createdBy, Roles.PROJECT_LEAD, null);
    projectMemberRepository.save(lead);
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
      UUID id, String name, String description, UUID memberId, String orgRole) {
    return updateProject(id, name, description, memberId, orgRole, null, null, null, null);
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
    return updateProject(
        id, name, description, memberId, orgRole, customFields, appliedFieldGroups, null, null);
  }

  @Transactional
  public ProjectWithRole updateProject(
      UUID id,
      String name,
      String description,
      UUID memberId,
      String orgRole,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups,
      UUID customerId,
      LocalDate dueDate) {
    var project =
        repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Project", id));
    var access = projectAccessService.requireEditAccess(id, memberId, orgRole);

    // Validate customer link if provided
    if (customerId != null) {
      var customer =
          customerRepository
              .findById(customerId)
              .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
      customerLifecycleGuard.requireActionPermitted(customer, LifecycleAction.CREATE_PROJECT);
    }

    // Capture old values before mutation
    String oldName = project.getName();
    String oldDescription = project.getDescription();
    UUID oldCustomerId = project.getCustomerId();
    LocalDate oldDueDate = project.getDueDate();

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

    // Use Project.update() which applies null-means-no-change convention
    project.update(name, description, customerId, dueDate);
    project = repository.save(project);

    // Build delta map -- only include changed fields (use actual values from entity)
    UUID newCustomerId = project.getCustomerId();
    LocalDate newDueDate = project.getDueDate();
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
    if (!Objects.equals(oldCustomerId, newCustomerId)) {
      details.put(
          "customerId",
          Map.of(
              "from", oldCustomerId != null ? oldCustomerId.toString() : "",
              "to", newCustomerId != null ? newCustomerId.toString() : ""));
    }
    if (!Objects.equals(oldDueDate, newDueDate)) {
      details.put(
          "dueDate",
          Map.of(
              "from", oldDueDate != null ? oldDueDate.toString() : "",
              "to", newDueDate != null ? newDueDate.toString() : ""));
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

    // Resolve one-level dependencies
    appliedFieldGroups = fieldGroupService.resolveDependencies(appliedFieldGroups);

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

    // Guard 1: Status check — only ACTIVE projects can be deleted
    if (project.getStatus() != ProjectStatus.ACTIVE) {
      throw new ResourceConflictException(
          "Cannot delete project",
          "Cannot delete a %s project. Reopen the project first."
              .formatted(project.getStatus().name().toLowerCase()));
    }

    // Guard 2: Tasks
    long taskCount = taskRepository.countByProjectId(id);
    if (taskCount > 0) {
      throw new ResourceConflictException(
          "Cannot delete project",
          "Project has %d task(s). Delete or cancel all tasks before deleting the project."
              .formatted(taskCount));
    }

    // Guard 3: Time entries
    long timeEntryCount = timeEntryRepository.countByProjectId(id);
    if (timeEntryCount > 0) {
      throw new ResourceConflictException(
          "Cannot delete project",
          "Project has %d time entry/entries. Remove all time entries before deleting the project."
              .formatted(timeEntryCount));
    }

    // Guard 4: Invoices
    long invoiceCount = invoiceRepository.countByProjectId(id);
    if (invoiceCount > 0) {
      throw new ResourceConflictException(
          "Cannot delete project",
          "Project has %d invoice(s). Void or delete all invoices before deleting the project."
              .formatted(invoiceCount));
    }

    // Guard 5: Documents
    long documentCount = documentRepository.countByProjectId(id);
    if (documentCount > 0) {
      throw new ResourceConflictException(
          "Cannot delete project",
          "Project has %d document(s). Delete all documents before deleting the project."
              .formatted(documentCount));
    }

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
      UUID projectId, boolean acknowledgeUnbilledTime, UUID memberId, String orgRole) {
    var project =
        repository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

    projectAccessService.requireEditAccess(projectId, memberId, orgRole);

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

    project.complete(memberId);
    project = repository.save(project);
    log.info("Project {} completed by member {}", projectId, memberId);

    var auditDetails = new LinkedHashMap<String, Object>();
    auditDetails.put("name", project.getName());
    auditDetails.put("completed_by", memberId.toString());
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

    String actorName = memberNameResolver.resolveName(memberId);
    String tenantId = RequestScopes.requireTenantId();
    String orgId = RequestScopes.requireOrgId();
    eventPublisher.publishEvent(
        new ProjectCompletedEvent(
            "project.completed",
            "project",
            project.getId(),
            project.getId(),
            memberId,
            actorName,
            tenantId,
            orgId,
            Instant.now(),
            Map.of("name", project.getName()),
            memberId,
            project.getName(),
            unbilledCount > 0 && acknowledgeUnbilledTime));

    return project;
  }

  @Transactional
  public Project archiveProject(UUID projectId, UUID memberId, String orgRole) {
    var project =
        repository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

    projectAccessService.requireEditAccess(projectId, memberId, orgRole);

    project.archive(memberId);
    project = repository.save(project);
    log.info("Project {} archived by member {}", projectId, memberId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("project.archived")
            .entityType("project")
            .entityId(project.getId())
            .details(
                Map.of(
                    "name", project.getName(),
                    "archived_by", memberId.toString()))
            .build());

    String actorName = memberNameResolver.resolveName(memberId);
    String tenantId = RequestScopes.requireTenantId();
    String orgId = RequestScopes.requireOrgId();
    eventPublisher.publishEvent(
        new ProjectArchivedEvent(
            "project.archived",
            "project",
            project.getId(),
            project.getId(),
            memberId,
            actorName,
            tenantId,
            orgId,
            Instant.now(),
            Map.of("name", project.getName()),
            memberId,
            project.getName()));

    return project;
  }

  @Transactional
  public Project reopenProject(UUID projectId, UUID memberId, String orgRole) {
    var project =
        repository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

    projectAccessService.requireEditAccess(projectId, memberId, orgRole);

    String previousStatus = project.getStatus().name();

    project.reopen();
    project = repository.save(project);
    log.info("Project {} reopened by member {}", projectId, memberId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("project.reopened")
            .entityType("project")
            .entityId(project.getId())
            .details(
                Map.of(
                    "name", project.getName(),
                    "reopened_by", memberId.toString(),
                    "previous_status", previousStatus))
            .build());

    String actorName = memberNameResolver.resolveName(memberId);
    String tenantId = RequestScopes.requireTenantId();
    String orgId = RequestScopes.requireOrgId();
    eventPublisher.publishEvent(
        new ProjectReopenedEvent(
            "project.reopened",
            "project",
            project.getId(),
            project.getId(),
            memberId,
            actorName,
            tenantId,
            orgId,
            Instant.now(),
            Map.of("name", project.getName()),
            memberId,
            project.getName(),
            previousStatus));

    return project;
  }
}
