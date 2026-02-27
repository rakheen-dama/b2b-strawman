package io.b2mash.b2b.b2bstrawman.project;

import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.SetFieldGroupsRequest;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
  private final MemberRepository memberRepository;

  public ProjectController(
      ProjectService projectService,
      EntityTagService entityTagService,
      ViewFilterHelper viewFilterHelper,
      ProjectSetupStatusService projectSetupStatusService,
      UnbilledTimeSummaryService unbilledTimeSummaryService,
      MemberRepository memberRepository) {
    this.projectService = projectService;
    this.entityTagService = entityTagService;
    this.viewFilterHelper = viewFilterHelper;
    this.projectSetupStatusService = projectSetupStatusService;
    this.unbilledTimeSummaryService = unbilledTimeSummaryService;
    this.memberRepository = memberRepository;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<ProjectResponse>> listProjects(
      @RequestParam(required = false) UUID view,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) LocalDate dueBefore,
      @RequestParam(required = false) Map<String, String> allParams) {

    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    // --- View-based filtering (server-side SQL) ---
    if (view != null) {
      // Build access control set: regular members only see projects they have access to
      boolean isAdminOrOwner = "admin".equals(orgRole) || "owner".equals(orgRole);
      Set<UUID> accessibleIds = null;
      if (!isAdminOrOwner) {
        accessibleIds =
            projectService.listProjects(memberId, orgRole).stream()
                .map(pwr -> pwr.project().getId())
                .collect(Collectors.toSet());
      }

      List<Project> filtered =
          viewFilterHelper.applyViewFilter(
              view, "PROJECT", "projects", Project.class, accessibleIds, Project::getId);

      if (filtered != null) {
        var projectIds = filtered.stream().map(Project::getId).toList();
        var tagsByEntityId = entityTagService.getEntityTagsBatch("PROJECT", projectIds);
        var memberNames = resolveNames(filtered);

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
    var projectsWithRoles = projectService.listProjects(memberId, orgRole);

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

    // Batch-load tags for all projects (2 queries instead of 2N)
    var projectIds = projectsWithRoles.stream().map(pwr -> pwr.project().getId()).toList();
    var tagsByEntityId = entityTagService.getEntityTagsBatch("PROJECT", projectIds);
    var allProjects = projectsWithRoles.stream().map(pwr -> pwr.project()).toList();
    var memberNames = resolveNames(allProjects);

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
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ProjectResponse> getProject(@PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    var pwr = projectService.getProject(id, memberId, orgRole);
    var tags = entityTagService.getEntityTags("PROJECT", id);
    var memberNames = resolveNames(List.of(pwr.project()));
    return ResponseEntity.ok(
        ProjectResponse.from(pwr.project(), pwr.projectRole(), tags, memberNames));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ProjectResponse> createProject(
      @Valid @RequestBody CreateProjectRequest request) {
    UUID createdBy = RequestScopes.requireMemberId();
    var project =
        projectService.createProject(
            request.name(),
            request.description(),
            createdBy,
            request.customFields(),
            request.appliedFieldGroups());
    var memberNames = resolveNames(List.of(project));
    return ResponseEntity.created(URI.create("/api/projects/" + project.getId()))
        .body(ProjectResponse.from(project, Roles.PROJECT_LEAD, memberNames));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ProjectResponse> updateProject(
      @PathVariable UUID id, @Valid @RequestBody UpdateProjectRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    var pwr =
        projectService.updateProject(
            id,
            request.name(),
            request.description(),
            memberId,
            orgRole,
            request.customFields(),
            request.appliedFieldGroups());
    var tags = entityTagService.getEntityTags("PROJECT", id);
    var memberNames = resolveNames(List.of(pwr.project()));
    return ResponseEntity.ok(
        ProjectResponse.from(pwr.project(), pwr.projectRole(), tags, memberNames));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ORG_OWNER')")
  public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
    projectService.deleteProject(id);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{id}/complete")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ProjectResponse> completeProject(
      @PathVariable UUID id, @RequestBody(required = false) CompleteProjectRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    boolean ack = request != null && Boolean.TRUE.equals(request.acknowledgeUnbilledTime());
    var project = projectService.completeProject(id, ack, memberId, orgRole);
    var memberNames = resolveNames(List.of(project));
    var tags = entityTagService.getEntityTags("PROJECT", id);
    return ResponseEntity.ok(ProjectResponse.from(project, null, tags, memberNames));
  }

  @PatchMapping("/{id}/archive")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ProjectResponse> archiveProject(@PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    var project = projectService.archiveProject(id, memberId, orgRole);
    var memberNames = resolveNames(List.of(project));
    var tags = entityTagService.getEntityTags("PROJECT", id);
    return ResponseEntity.ok(ProjectResponse.from(project, null, tags, memberNames));
  }

  @PatchMapping("/{id}/reopen")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ProjectResponse> reopenProject(@PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    var project = projectService.reopenProject(id, memberId, orgRole);
    var memberNames = resolveNames(List.of(project));
    var tags = entityTagService.getEntityTags("PROJECT", id);
    return ResponseEntity.ok(ProjectResponse.from(project, null, tags, memberNames));
  }

  @PutMapping("/{id}/field-groups")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<FieldDefinitionResponse>> setFieldGroups(
      @PathVariable UUID id, @Valid @RequestBody SetFieldGroupsRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    var fieldDefs =
        projectService.setFieldGroups(id, request.appliedFieldGroups(), memberId, orgRole);
    return ResponseEntity.ok(fieldDefs);
  }

  @PostMapping("/{id}/tags")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<TagResponse>> setProjectTags(
      @PathVariable UUID id, @Valid @RequestBody SetEntityTagsRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    // Verify project access
    projectService.getProject(id, memberId, orgRole);
    var tags = entityTagService.setEntityTags("PROJECT", id, request.tagIds());
    return ResponseEntity.ok(tags);
  }

  @GetMapping("/{id}/tags")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<TagResponse>> getProjectTags(@PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    // Verify project access
    projectService.getProject(id, memberId, orgRole);
    var tags = entityTagService.getEntityTags("PROJECT", id);
    return ResponseEntity.ok(tags);
  }

  @GetMapping("/{id}/setup-status")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")
  public ResponseEntity<ProjectSetupStatus> getSetupStatus(@PathVariable UUID id) {
    return ResponseEntity.ok(projectSetupStatusService.getSetupStatus(id));
  }

  @GetMapping("/{id}/unbilled-summary")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<UnbilledTimeSummary> getUnbilledSummary(@PathVariable UUID id) {
    return ResponseEntity.ok(unbilledTimeSummaryService.getProjectUnbilledSummary(id));
  }

  private Map<UUID, String> resolveNames(List<Project> projects) {
    var ids =
        projects.stream()
            .flatMap(p -> Stream.of(p.getCreatedBy(), p.getCompletedBy()))
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    if (ids.isEmpty()) return Map.of();
    return memberRepository.findAllById(ids).stream()
        .collect(
            Collectors.toMap(
                Member::getId, m -> m.getName() != null ? m.getName() : "", (a, b) -> a));
  }

  private static List<ProjectStatus> parseProjectStatuses(String status) {
    if (status == null || status.isBlank()) {
      return List.of(ProjectStatus.ACTIVE); // Default: show only ACTIVE
    }
    if ("ALL".equalsIgnoreCase(status)) {
      return null; // null = no filter
    }
    return Arrays.stream(status.split(",")).map(String::trim).map(ProjectStatus::valueOf).toList();
  }

  public record CompleteProjectRequest(Boolean acknowledgeUnbilledTime) {}

  public record CreateProjectRequest(
      @NotBlank(message = "name is required")
          @Size(max = 255, message = "name must be at most 255 characters")
          String name,
      @Size(max = 2000, message = "description must be at most 2000 characters") String description,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups) {}

  public record UpdateProjectRequest(
      @NotBlank(message = "name is required")
          @Size(max = 255, message = "name must be at most 255 characters")
          String name,
      @Size(max = 2000, message = "description must be at most 2000 characters") String description,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups) {}

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
      List<TagResponse> tags) {

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
          List.of());
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
          List.of());
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
          tags);
    }
  }
}
