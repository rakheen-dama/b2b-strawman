package io.b2mash.b2b.b2bstrawman.project;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.SetFieldGroupsRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import io.b2mash.b2b.b2bstrawman.setupstatus.ProjectSetupStatus;
import io.b2mash.b2b.b2bstrawman.setupstatus.ProjectSetupStatusService;
import io.b2mash.b2b.b2bstrawman.setupstatus.UnbilledTimeSummary;
import io.b2mash.b2b.b2bstrawman.setupstatus.UnbilledTimeSummaryService;
import io.b2mash.b2b.b2bstrawman.tag.EntityTagService;
import io.b2mash.b2b.b2bstrawman.tag.TagFilterUtil;
import io.b2mash.b2b.b2bstrawman.tag.dto.SetEntityTagsRequest;
import io.b2mash.b2b.b2bstrawman.tag.dto.TagResponse;
import io.b2mash.b2b.b2bstrawman.view.CustomFieldFilterUtil;
import io.b2mash.b2b.b2bstrawman.view.ViewFilterHelper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

  private final ProjectService projectService;
  private final EntityTagService entityTagService;
  private final ViewFilterHelper viewFilterHelper;
  private final ProjectSetupStatusService projectSetupStatusService;
  private final UnbilledTimeSummaryService unbilledTimeSummaryService;

  public ProjectController(
      ProjectService projectService,
      EntityTagService entityTagService,
      ViewFilterHelper viewFilterHelper,
      ProjectSetupStatusService projectSetupStatusService,
      UnbilledTimeSummaryService unbilledTimeSummaryService) {
    this.projectService = projectService;
    this.entityTagService = entityTagService;
    this.viewFilterHelper = viewFilterHelper;
    this.projectSetupStatusService = projectSetupStatusService;
    this.unbilledTimeSummaryService = unbilledTimeSummaryService;
  }

  @GetMapping
  public ResponseEntity<List<ProjectResponse>> listProjects(
      @RequestParam(required = false) UUID view,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) LocalDate dueBefore,
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false) Map<String, String> allParams,
      ActorContext actor) {

    // --- View-based filtering (server-side SQL) ---
    if (view != null) {
      // Build access control set: regular members only see projects they have access to
      boolean isAdminOrOwner = actor.isOwnerOrAdmin();
      Set<UUID> accessibleIds = null;
      if (!isAdminOrOwner) {
        accessibleIds =
            projectService.listProjects(actor).stream()
                .map(pwr -> pwr.project().getId())
                .collect(Collectors.toSet());
      }

      List<Project> filtered =
          viewFilterHelper.applyViewFilter(
              view, "PROJECT", "projects", Project.class, accessibleIds, Project::getId);

      if (filtered != null) {
        // Apply customerId filter to view-based results
        if (customerId != null) {
          filtered = filtered.stream().filter(p -> customerId.equals(p.getCustomerId())).toList();
        }
        var projectIds = filtered.stream().map(Project::getId).toList();
        var tagsByEntityId = entityTagService.getEntityTagsBatch("PROJECT", projectIds);
        var memberNames = projectService.resolveProjectMemberNames(filtered);

        var responses =
            filtered.stream()
                .map(
                    p ->
                        ProjectResponse.from(
                            p,
                            null,
                            tagsByEntityId.getOrDefault(p.getId(), List.of()),
                            memberNames))
                .toList();
        return ResponseEntity.ok(responses);
      }
    }

    // --- Fallback: existing in-memory filtering ---
    var projectsWithRoles = projectService.listProjects(actor);

    // Apply status filter (default: ACTIVE only)
    List<ProjectStatus> statusFilter = parseProjectStatuses(status);
    if (statusFilter != null) {
      projectsWithRoles =
          projectsWithRoles.stream()
              .filter(pwr -> statusFilter.contains(pwr.project().getStatus()))
              .toList();
    }

    // Apply dueBefore filter
    if (dueBefore != null) {
      projectsWithRoles =
          projectsWithRoles.stream()
              .filter(
                  pwr ->
                      pwr.project().getDueDate() != null
                          && pwr.project().getDueDate().isBefore(dueBefore))
              .toList();
    }

    // Apply customerId filter
    if (customerId != null) {
      projectsWithRoles =
          projectsWithRoles.stream()
              .filter(pwr -> customerId.equals(pwr.project().getCustomerId()))
              .toList();
    }

    // Batch-load tags for all projects (2 queries instead of 2N)
    var projectIds = projectsWithRoles.stream().map(pwr -> pwr.project().getId()).toList();
    var tagsByEntityId = entityTagService.getEntityTagsBatch("PROJECT", projectIds);
    var allProjects = projectsWithRoles.stream().map(pwr -> pwr.project()).toList();
    var memberNames = projectService.resolveProjectMemberNames(allProjects);

    var projects =
        projectsWithRoles.stream()
            .map(
                pwr ->
                    ProjectResponse.from(
                        pwr.project(),
                        pwr.projectRole(),
                        tagsByEntityId.getOrDefault(pwr.project().getId(), List.of()),
                        memberNames))
            .toList();

    // Apply custom field filtering if present
    Map<String, String> customFieldFilters =
        CustomFieldFilterUtil.extractCustomFieldFilters(allParams);
    if (!customFieldFilters.isEmpty()) {
      projects =
          projects.stream()
              .filter(
                  p ->
                      CustomFieldFilterUtil.matchesCustomFieldFilters(
                          p.customFields(), customFieldFilters))
              .toList();
    }

    // Apply tag filtering if present
    List<String> tagSlugs = TagFilterUtil.extractTagSlugs(allParams);
    if (!tagSlugs.isEmpty()) {
      projects =
          projects.stream()
              .filter(p -> TagFilterUtil.matchesTagFilter(p.tags(), tagSlugs))
              .toList();
    }

    return ResponseEntity.ok(projects);
  }

  @GetMapping("/{id}")
  public ResponseEntity<ProjectResponse> getProject(@PathVariable UUID id, ActorContext actor) {
    var pwr = projectService.getProject(id, actor);
    var tags = entityTagService.getEntityTags("PROJECT", id);
    var memberNames = projectService.resolveProjectMemberNames(List.of(pwr.project()));
    return ResponseEntity.ok(
        ProjectResponse.from(pwr.project(), pwr.projectRole(), tags, memberNames));
  }

  @PostMapping
  @RequiresCapability("PROJECT_MANAGEMENT")
  public ResponseEntity<ProjectResponse> createProject(
      @Valid @RequestBody CreateProjectRequest request) {
    UUID createdBy = RequestScopes.requireMemberId();
    var project =
        projectService.createProject(
            request.name(),
            request.description(),
            createdBy,
            request.customFields(),
            request.appliedFieldGroups(),
            request.customerId(),
            request.dueDate(),
            request.referenceNumber(),
            request.priority(),
            request.workType());
    var memberNames = projectService.resolveProjectMemberNames(List.of(project));
    return ResponseEntity.created(URI.create("/api/projects/" + project.getId()))
        .body(ProjectResponse.from(project, Roles.PROJECT_LEAD, memberNames));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ProjectResponse> updateProject(
      @PathVariable UUID id, @Valid @RequestBody UpdateProjectRequest request, ActorContext actor) {
    var pwr =
        projectService.updateProject(
            id,
            request.name(),
            request.description(),
            actor,
            request.customFields(),
            request.appliedFieldGroups(),
            request.customerId(),
            request.dueDate(),
            request.referenceNumber(),
            request.priority(),
            request.workType());
    var tags = entityTagService.getEntityTags("PROJECT", id);
    var memberNames = projectService.resolveProjectMemberNames(List.of(pwr.project()));
    return ResponseEntity.ok(
        ProjectResponse.from(pwr.project(), pwr.projectRole(), tags, memberNames));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
    projectService.deleteProject(id);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{id}/complete")
  @RequiresCapability("PROJECT_MANAGEMENT")
  public ResponseEntity<ProjectResponse> completeProject(
      @PathVariable UUID id,
      @RequestBody(required = false) CompleteProjectRequest request,
      ActorContext actor) {
    boolean ack = request != null && Boolean.TRUE.equals(request.acknowledgeUnbilledTime());
    var project = projectService.completeProject(id, ack, actor);
    var memberNames = projectService.resolveProjectMemberNames(List.of(project));
    var tags = entityTagService.getEntityTags("PROJECT", id);
    return ResponseEntity.ok(ProjectResponse.from(project, null, tags, memberNames));
  }

  @PatchMapping("/{id}/archive")
  @RequiresCapability("PROJECT_MANAGEMENT")
  public ResponseEntity<ProjectResponse> archiveProject(@PathVariable UUID id, ActorContext actor) {
    var project = projectService.archiveProject(id, actor);
    var memberNames = projectService.resolveProjectMemberNames(List.of(project));
    var tags = entityTagService.getEntityTags("PROJECT", id);
    return ResponseEntity.ok(ProjectResponse.from(project, null, tags, memberNames));
  }

  @PatchMapping("/{id}/reopen")
  @RequiresCapability("PROJECT_MANAGEMENT")
  public ResponseEntity<ProjectResponse> reopenProject(@PathVariable UUID id, ActorContext actor) {
    var project = projectService.reopenProject(id, actor);
    var memberNames = projectService.resolveProjectMemberNames(List.of(project));
    var tags = entityTagService.getEntityTags("PROJECT", id);
    return ResponseEntity.ok(ProjectResponse.from(project, null, tags, memberNames));
  }

  @PutMapping("/{id}/field-groups")
  @RequiresCapability("PROJECT_MANAGEMENT")
  public ResponseEntity<List<FieldDefinitionResponse>> setFieldGroups(
      @PathVariable UUID id,
      @Valid @RequestBody SetFieldGroupsRequest request,
      ActorContext actor) {
    var fieldDefs = projectService.setFieldGroups(id, request.appliedFieldGroups(), actor);
    return ResponseEntity.ok(fieldDefs);
  }

  @PostMapping("/{id}/tags")
  public ResponseEntity<List<TagResponse>> setProjectTags(
      @PathVariable UUID id, @Valid @RequestBody SetEntityTagsRequest request, ActorContext actor) {
    // Verify project access
    projectService.getProject(id, actor);
    var tags = entityTagService.setEntityTags("PROJECT", id, request.tagIds());
    return ResponseEntity.ok(tags);
  }

  @GetMapping("/{id}/tags")
  public ResponseEntity<List<TagResponse>> getProjectTags(
      @PathVariable UUID id, ActorContext actor) {
    // Verify project access
    projectService.getProject(id, actor);
    var tags = entityTagService.getEntityTags("PROJECT", id);
    return ResponseEntity.ok(tags);
  }

  @GetMapping("/{id}/setup-status")
  public ResponseEntity<ProjectSetupStatus> getSetupStatus(@PathVariable UUID id) {
    return ResponseEntity.ok(projectSetupStatusService.getSetupStatus(id));
  }

  @GetMapping("/{id}/unbilled-summary")
  @RequiresCapability("PROJECT_MANAGEMENT")
  public ResponseEntity<UnbilledTimeSummary> getUnbilledSummary(@PathVariable UUID id) {
    return ResponseEntity.ok(unbilledTimeSummaryService.getProjectUnbilledSummary(id));
  }

  private static List<ProjectStatus> parseProjectStatuses(String status) {
    if (status == null || status.isBlank()) {
      return List.of(ProjectStatus.ACTIVE); // Default: show only ACTIVE
    }
    if ("ALL".equalsIgnoreCase(status)) {
      return null; // null = no filter
    }
    return Arrays.stream(status.split(","))
        .map(String::trim)
        .map(
            s -> {
              try {
                return ProjectStatus.valueOf(s.toUpperCase());
              } catch (IllegalArgumentException e) {
                throw new InvalidStateException(
                    "Invalid project status",
                    "Invalid project status: '"
                        + s
                        + "'. Valid values: "
                        + Arrays.toString(ProjectStatus.values()));
              }
            })
        .toList();
  }

  public record CompleteProjectRequest(Boolean acknowledgeUnbilledTime) {}

  public record CreateProjectRequest(
      @NotBlank(message = "name is required")
          @Size(max = 255, message = "name must be at most 255 characters")
          String name,
      @Size(max = 2000, message = "description must be at most 2000 characters") String description,
      UUID customerId,
      LocalDate dueDate,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups,
      @Size(max = 100, message = "referenceNumber must be at most 100 characters")
          String referenceNumber,
      ProjectPriority priority,
      @Size(max = 50, message = "workType must be at most 50 characters") String workType) {}

  public record UpdateProjectRequest(
      @NotBlank(message = "name is required")
          @Size(max = 255, message = "name must be at most 255 characters")
          String name,
      @Size(max = 2000, message = "description must be at most 2000 characters") String description,
      UUID customerId,
      LocalDate dueDate,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups,
      @Size(max = 100, message = "referenceNumber must be at most 100 characters")
          String referenceNumber,
      ProjectPriority priority,
      @Size(max = 50, message = "workType must be at most 50 characters") String workType) {}

  public record ProjectResponse(
      UUID id,
      String name,
      String description,
      String status,
      UUID customerId,
      LocalDate dueDate,
      UUID createdBy,
      String createdByName,
      Instant createdAt,
      Instant updatedAt,
      Instant completedAt,
      UUID completedBy,
      String completedByName,
      Instant archivedAt,
      String projectRole,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups,
      List<TagResponse> tags,
      String referenceNumber,
      ProjectPriority priority,
      String workType) {

    public static ProjectResponse from(Project project) {
      return from(project, Map.of());
    }

    public static ProjectResponse from(Project project, Map<UUID, String> memberNames) {
      return new ProjectResponse(
          project.getId(),
          project.getName(),
          project.getDescription(),
          project.getStatus().name(),
          project.getCustomerId(),
          project.getDueDate(),
          project.getCreatedBy(),
          project.getCreatedBy() != null ? memberNames.get(project.getCreatedBy()) : null,
          project.getCreatedAt(),
          project.getUpdatedAt(),
          project.getCompletedAt(),
          project.getCompletedBy(),
          project.getCompletedBy() != null ? memberNames.get(project.getCompletedBy()) : null,
          project.getArchivedAt(),
          null,
          project.getCustomFields(),
          project.getAppliedFieldGroups(),
          List.of(),
          project.getReferenceNumber(),
          project.getPriority(),
          project.getWorkType());
    }

    public static ProjectResponse from(
        Project project, String projectRole, Map<UUID, String> memberNames) {
      return new ProjectResponse(
          project.getId(),
          project.getName(),
          project.getDescription(),
          project.getStatus().name(),
          project.getCustomerId(),
          project.getDueDate(),
          project.getCreatedBy(),
          project.getCreatedBy() != null ? memberNames.get(project.getCreatedBy()) : null,
          project.getCreatedAt(),
          project.getUpdatedAt(),
          project.getCompletedAt(),
          project.getCompletedBy(),
          project.getCompletedBy() != null ? memberNames.get(project.getCompletedBy()) : null,
          project.getArchivedAt(),
          projectRole,
          project.getCustomFields(),
          project.getAppliedFieldGroups(),
          List.of(),
          project.getReferenceNumber(),
          project.getPriority(),
          project.getWorkType());
    }

    public static ProjectResponse from(
        Project project,
        String projectRole,
        List<TagResponse> tags,
        Map<UUID, String> memberNames) {
      return new ProjectResponse(
          project.getId(),
          project.getName(),
          project.getDescription(),
          project.getStatus().name(),
          project.getCustomerId(),
          project.getDueDate(),
          project.getCreatedBy(),
          project.getCreatedBy() != null ? memberNames.get(project.getCreatedBy()) : null,
          project.getCreatedAt(),
          project.getUpdatedAt(),
          project.getCompletedAt(),
          project.getCompletedBy(),
          project.getCompletedBy() != null ? memberNames.get(project.getCompletedBy()) : null,
          project.getArchivedAt(),
          projectRole,
          project.getCustomFields(),
          project.getAppliedFieldGroups(),
          tags,
          project.getReferenceNumber(),
          project.getPriority(),
          project.getWorkType());
    }
  }
}
