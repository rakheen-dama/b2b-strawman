package io.b2mash.b2b.b2bstrawman.projecttemplate;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.ProjectCreatedEvent;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.ProjectMember;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.CreateTemplateRequest;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.InstantiateTemplateRequest;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.ProjectTemplateResponse;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.SaveFromProjectRequest;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.TagResponse;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.TemplateTaskResponse;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.UpdateTemplateRequest;
import io.b2mash.b2b.b2bstrawman.projecttemplate.event.TemplateCreatedEvent;
import io.b2mash.b2b.b2bstrawman.schedule.RecurringScheduleRepository;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import io.b2mash.b2b.b2bstrawman.tag.EntityTag;
import io.b2mash.b2b.b2bstrawman.tag.EntityTagRepository;
import io.b2mash.b2b.b2bstrawman.tag.TagRepository;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectTemplateService {

  private static final Logger log = LoggerFactory.getLogger(ProjectTemplateService.class);

  private final ProjectTemplateRepository templateRepository;
  private final TemplateTaskRepository templateTaskRepository;
  private final TemplateTagRepository templateTagRepository;
  private final TagRepository tagLookupRepository;
  private final TaskRepository projectTaskRepository;
  private final ProjectRepository projectRepository;
  private final ProjectMemberRepository projectMemberRepository;
  private final RecurringScheduleRepository scheduleRepository;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final CustomerRepository customerRepository;
  private final CustomerProjectRepository customerProjectRepository;
  private final EntityTagRepository entityTagRepository;
  private final NameTokenResolver nameTokenResolver;

  public ProjectTemplateService(
      ProjectTemplateRepository templateRepository,
      TemplateTaskRepository templateTaskRepository,
      TemplateTagRepository templateTagRepository,
      TagRepository tagLookupRepository,
      TaskRepository projectTaskRepository,
      ProjectRepository projectRepository,
      ProjectMemberRepository projectMemberRepository,
      RecurringScheduleRepository scheduleRepository,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      CustomerRepository customerRepository,
      CustomerProjectRepository customerProjectRepository,
      EntityTagRepository entityTagRepository,
      NameTokenResolver nameTokenResolver) {
    this.templateRepository = templateRepository;
    this.templateTaskRepository = templateTaskRepository;
    this.templateTagRepository = templateTagRepository;
    this.tagLookupRepository = tagLookupRepository;
    this.projectTaskRepository = projectTaskRepository;
    this.projectRepository = projectRepository;
    this.projectMemberRepository = projectMemberRepository;
    this.scheduleRepository = scheduleRepository;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.customerRepository = customerRepository;
    this.customerProjectRepository = customerProjectRepository;
    this.entityTagRepository = entityTagRepository;
    this.nameTokenResolver = nameTokenResolver;
  }

  @Transactional
  public ProjectTemplateResponse create(CreateTemplateRequest request, UUID memberId) {
    var template =
        new ProjectTemplate(
            request.name(),
            request.namePattern(),
            request.description(),
            request.billableDefault(),
            "MANUAL",
            null,
            memberId);
    template = templateRepository.save(template);

    int taskCount = 0;
    if (request.tasks() != null) {
      for (var taskReq : request.tasks()) {
        var task =
            new TemplateTask(
                template.getId(),
                taskReq.name(),
                taskReq.description(),
                taskReq.estimatedHours(),
                taskReq.sortOrder(),
                taskReq.billable(),
                taskReq.assigneeRole());
        templateTaskRepository.save(task);
        taskCount++;
      }
    }

    if (request.tagIds() != null) {
      for (UUID tagId : request.tagIds()) {
        templateTagRepository.save(template.getId(), tagId);
      }
    }

    log.info("Created template {}", template.getId());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("template.created")
            .entityType("project_template")
            .entityId(template.getId())
            .details(
                Map.of(
                    "name",
                    template.getName(),
                    "source",
                    "MANUAL",
                    "task_count",
                    String.valueOf(taskCount)))
            .build());

    publishCreatedEvent(template, memberId);
    return buildResponse(template);
  }

  @Transactional
  public ProjectTemplateResponse update(UUID id, UpdateTemplateRequest request) {
    var template =
        templateRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ProjectTemplate", id));

    template.update(
        request.name(), request.namePattern(), request.description(), request.billableDefault());
    template = templateRepository.save(template);

    // Replace tasks: delete all + re-insert
    templateTaskRepository.deleteByTemplateId(id);
    if (request.tasks() != null) {
      for (var taskReq : request.tasks()) {
        var task =
            new TemplateTask(
                template.getId(),
                taskReq.name(),
                taskReq.description(),
                taskReq.estimatedHours(),
                taskReq.sortOrder(),
                taskReq.billable(),
                taskReq.assigneeRole());
        templateTaskRepository.save(task);
      }
    }

    // Replace tags: delete all + re-insert
    templateTagRepository.deleteByTemplateId(id);
    if (request.tagIds() != null) {
      for (UUID tagId : request.tagIds()) {
        templateTagRepository.save(template.getId(), tagId);
      }
    }

    log.info("Updated template {}", id);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("template.updated")
            .entityType("project_template")
            .entityId(template.getId())
            .details(Map.of("name", template.getName(), "changes", "tasks and tags replaced"))
            .build());

    return buildResponse(template);
  }

  @Transactional
  public void delete(UUID id) {
    var template =
        templateRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ProjectTemplate", id));

    if (scheduleRepository.existsByTemplateId(id)) {
      throw new ResourceConflictException(
          "Template in use",
          "Template has active recurring schedules. Deactivate or delete them first.");
    }

    String templateName = template.getName();
    templateRepository.deleteById(id);

    log.info("Deleted template {}", id);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("template.deleted")
            .entityType("project_template")
            .entityId(id)
            .details(Map.of("name", templateName))
            .build());
  }

  @Transactional
  public ProjectTemplateResponse duplicate(UUID id, UUID memberId) {
    var source =
        templateRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ProjectTemplate", id));

    var tasks = templateTaskRepository.findByTemplateIdOrderBySortOrder(id);
    var tagIds = templateTagRepository.findTagIdsByTemplateId(id);

    var copy =
        new ProjectTemplate(
            source.getName() + " (Copy)",
            source.getNamePattern(),
            source.getDescription(),
            source.isBillableDefault(),
            "MANUAL",
            null,
            memberId);
    copy = templateRepository.save(copy);

    for (var task : tasks) {
      var taskCopy =
          new TemplateTask(
              copy.getId(),
              task.getName(),
              task.getDescription(),
              task.getEstimatedHours(),
              task.getSortOrder(),
              task.isBillable(),
              task.getAssigneeRole());
      templateTaskRepository.save(taskCopy);
    }

    for (UUID tagId : tagIds) {
      templateTagRepository.save(copy.getId(), tagId);
    }

    log.info("Duplicated template {} -> {}", id, copy.getId());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("template.duplicated")
            .entityType("project_template")
            .entityId(copy.getId())
            .details(
                Map.of(
                    "source_template_name", source.getName(), "new_template_name", copy.getName()))
            .build());

    return buildResponse(copy);
  }

  @Transactional(readOnly = true)
  public List<ProjectTemplateResponse> list() {
    return templateRepository.findAllByOrderByNameAsc().stream().map(this::buildResponse).toList();
  }

  @Transactional(readOnly = true)
  public List<ProjectTemplateResponse> listActive() {
    return templateRepository.findByActiveOrderByNameAsc(true).stream()
        .map(this::buildResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public ProjectTemplateResponse get(UUID id) {
    var template =
        templateRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ProjectTemplate", id));
    return buildResponse(template);
  }

  @Transactional
  public ProjectTemplateResponse saveFromProject(
      UUID projectId, SaveFromProjectRequest request, UUID memberId, String orgRole) {
    // 1. Load project
    projectRepository
        .findById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

    // 2. Permission check: admin/owner always allowed; member must be project lead
    requireAdminOwnerOrProjectLead(orgRole, projectId, memberId);

    // 3. Create template
    var template =
        new ProjectTemplate(
            request.name(),
            request.namePattern(),
            request.description(),
            true,
            "FROM_PROJECT",
            projectId,
            memberId);
    template = templateRepository.save(template);

    // 4. Copy selected tasks in taskIds list order
    List<Task> projectTasks = projectTaskRepository.findByProjectId(projectId);
    Map<UUID, Task> taskMap = projectTasks.stream().collect(Collectors.toMap(Task::getId, t -> t));

    int sortOrder = 0;
    if (request.taskIds() != null) {
      for (UUID taskId : request.taskIds()) {
        Task task = taskMap.get(taskId);
        if (task == null) continue; // silently skip unknown task IDs
        String role =
            request.taskRoles() != null
                ? request.taskRoles().getOrDefault(taskId, "UNASSIGNED")
                : "UNASSIGNED";
        var templateTask =
            new TemplateTask(
                template.getId(),
                task.getTitle(),
                task.getDescription(),
                null,
                sortOrder++,
                template.isBillableDefault(),
                role);
        templateTaskRepository.save(templateTask);
      }
    }

    // 5. Copy tags
    if (request.tagIds() != null) {
      for (UUID tagId : request.tagIds()) {
        templateTagRepository.save(template.getId(), tagId);
      }
    }

    log.info("Saved project {} as template {}", projectId, template.getId());

    // 6. Audit
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("template.created")
            .entityType("project_template")
            .entityId(template.getId())
            .details(
                Map.of(
                    "name",
                    template.getName(),
                    "source",
                    "FROM_PROJECT",
                    "source_project_id",
                    projectId.toString(),
                    "task_count",
                    String.valueOf(sortOrder)))
            .build());

    publishCreatedEvent(template, memberId);
    return buildResponse(template);
  }

  @Transactional
  public Project instantiateTemplate(
      UUID templateId, InstantiateTemplateRequest request, UUID memberId) {
    // 1. Load and validate template
    var template =
        templateRepository
            .findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("ProjectTemplate", templateId));
    if (!template.isActive()) {
      throw new InvalidStateException(
          "Template inactive", "Cannot create project from an inactive template.");
    }

    // 2. Load customer (if requested)
    Customer customer = null;
    if (request.customerId() != null) {
      customer =
          customerRepository
              .findById(request.customerId())
              .orElseThrow(() -> new ResourceNotFoundException("Customer", request.customerId()));
    }

    // 3. Resolve project name
    String resolvedName =
        request.name() != null
            ? request.name()
            : nameTokenResolver.resolveNameTokens(
                template.getNamePattern(), customer, LocalDate.now(), null, null);

    // 4. Resolve description
    String description =
        request.description() != null ? request.description() : template.getDescription();

    // 5. Create project
    var project = new Project(resolvedName, description, memberId);
    project = projectRepository.save(project);

    // 6. Link to customer
    if (customer != null) {
      customerProjectRepository.save(
          new CustomerProject(customer.getId(), project.getId(), memberId));
    }

    // 7. Set project lead
    if (request.projectLeadMemberId() != null) {
      projectMemberRepository.save(
          new ProjectMember(project.getId(), request.projectLeadMemberId(), "LEAD", memberId));
    }

    // 8. Create tasks from template (snapshot — no live references)
    // TemplateTask.getName() maps to Task.title (different field names!)
    var templateTasks = templateTaskRepository.findByTemplateIdOrderBySortOrder(templateId);
    for (var tt : templateTasks) {
      UUID assigneeId = resolveAssignee(tt.getAssigneeRole(), request.projectLeadMemberId());
      // Task constructor: (projectId, title, description, priority, type, dueDate, createdBy)
      var task =
          new Task(
              project.getId(), tt.getName(), tt.getDescription(), "MEDIUM", null, null, memberId);
      if (assigneeId != null) {
        // Set assignee without changing status (stays OPEN)
        task.update(
            task.getTitle(),
            task.getDescription(),
            task.getPriority(),
            task.getStatus(),
            task.getType(),
            task.getDueDate(),
            assigneeId);
      }
      projectTaskRepository.save(task);
    }

    // 9. Apply tags
    var tagIds = templateTagRepository.findTagIdsByTemplateId(templateId);
    for (UUID tagId : tagIds) {
      entityTagRepository.save(new EntityTag(tagId, "PROJECT", project.getId()));
    }

    log.info(
        "Instantiated template {} -> project {} (name={})",
        templateId,
        project.getId(),
        resolvedName);

    // 10. Audit
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("project.created_from_template")
            .entityType("project")
            .entityId(project.getId())
            .details(
                Map.of(
                    "template_name",
                    template.getName(),
                    "project_name",
                    resolvedName,
                    "customer_name",
                    customer != null ? customer.getName() : "none"))
            .build());

    // 11. Publish notification event (informational — reuses ProjectCreatedEvent)
    String tenantId = RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : null;
    String orgId = RequestScopes.ORG_ID.isBound() ? RequestScopes.ORG_ID.get() : null;
    eventPublisher.publishEvent(
        new ProjectCreatedEvent(
            project.getId(), project.getName(), project.getDescription(), null, orgId, tenantId));

    return project;
  }

  private UUID resolveAssignee(String assigneeRole, UUID projectLeadMemberId) {
    return switch (assigneeRole) {
      case "PROJECT_LEAD" -> projectLeadMemberId; // may be null if no lead provided
      case "ANY_MEMBER", "UNASSIGNED" -> null;
      default -> null;
    };
  }

  // --- Private helpers ---

  private void requireAdminOwnerOrProjectLead(String orgRole, UUID projectId, UUID memberId) {
    if (Roles.ORG_OWNER.equals(orgRole) || Roles.ORG_ADMIN.equals(orgRole)) {
      return;
    }
    var pm =
        projectMemberRepository
            .findByProjectIdAndMemberId(projectId, memberId)
            .orElseThrow(
                () ->
                    new ForbiddenException(
                        "Cannot save project as template",
                        "Only admins, owners, or project leads may save a project as a"
                            + " template."));
    if (!Roles.PROJECT_LEAD.equals(pm.getProjectRole())) {
      throw new ForbiddenException(
          "Cannot save project as template",
          "Only admins, owners, or project leads may save a project as a template.");
    }
  }

  private void publishCreatedEvent(ProjectTemplate template, UUID memberId) {
    String tenantId = RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : null;
    String orgId = RequestScopes.ORG_ID.isBound() ? RequestScopes.ORG_ID.get() : null;
    eventPublisher.publishEvent(
        new TemplateCreatedEvent(
            template.getId(),
            template.getName(),
            template.getSource(),
            memberId,
            tenantId,
            orgId,
            Instant.now()));
  }

  private ProjectTemplateResponse buildResponse(ProjectTemplate template) {
    var tasks =
        templateTaskRepository.findByTemplateIdOrderBySortOrder(template.getId()).stream()
            .map(
                t ->
                    new TemplateTaskResponse(
                        t.getId(),
                        t.getName(),
                        t.getDescription(),
                        t.getEstimatedHours(),
                        t.getSortOrder(),
                        t.isBillable(),
                        t.getAssigneeRole()))
            .toList();

    var tagIds = templateTagRepository.findTagIdsByTemplateId(template.getId());
    var tags =
        tagLookupRepository.findAllById(tagIds).stream()
            .map(tag -> new TagResponse(tag.getId(), tag.getName(), tag.getColor()))
            .toList();

    return new ProjectTemplateResponse(
        template.getId(),
        template.getName(),
        template.getNamePattern(),
        template.getDescription(),
        template.isBillableDefault(),
        template.getSource(),
        template.getSourceProjectId(),
        template.isActive(),
        tasks.size(),
        tags.size(),
        tasks,
        tags,
        template.getCreatedAt(),
        template.getUpdatedAt());
  }
}
