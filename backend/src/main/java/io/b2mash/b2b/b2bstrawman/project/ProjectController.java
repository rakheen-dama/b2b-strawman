package io.b2mash.b2b.b2bstrawman.project;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.SetFieldGroupsRequest;
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
import io.b2mash.b2b.b2bstrawman.view.SavedViewRepository;
import io.b2mash.b2b.b2bstrawman.view.ViewFilterService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
  private final SavedViewRepository savedViewRepository;
  private final ViewFilterService viewFilterService;
  private final ProjectSetupStatusService projectSetupStatusService;
  private final UnbilledTimeSummaryService unbilledTimeSummaryService;

  public ProjectController(
      ProjectService projectService,
      EntityTagService entityTagService,
      SavedViewRepository savedViewRepository,
      ViewFilterService viewFilterService,
      ProjectSetupStatusService projectSetupStatusService,
      UnbilledTimeSummaryService unbilledTimeSummaryService) {
    this.projectService = projectService;
    this.entityTagService = entityTagService;
    this.savedViewRepository = savedViewRepository;
    this.viewFilterService = viewFilterService;
    this.projectSetupStatusService = projectSetupStatusService;
    this.unbilledTimeSummaryService = unbilledTimeSummaryService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<ProjectResponse>> listProjects(
      @RequestParam(required = false) UUID view,
      @RequestParam(required = false) Map<String, String> allParams) {

    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    // --- View-based filtering (server-side SQL) ---
    if (view != null) {
      var savedView =
          savedViewRepository
              .findById(view)
              .orElseThrow(() -> new ResourceNotFoundException("SavedView", view));

      if (!"PROJECT".equals(savedView.getEntityType())) {
        throw new InvalidStateException(
            "View type mismatch", "Expected PROJECT view but got " + savedView.getEntityType());
      }

      List<Project> filtered =
          viewFilterService.executeFilterQuery(
              "projects", Project.class, savedView.getFilters(), "PROJECT");

      if (filtered != null) {
        // Apply project access control: regular members only see projects they have access to.
        // Admin/owner see all projects; members must be in project_members.
        boolean isAdminOrOwner = "admin".equals(orgRole) || "owner".equals(orgRole);
        if (!isAdminOrOwner) {
          Set<UUID> accessibleIds =
              projectService.listProjects(memberId, orgRole).stream()
                  .map(pwr -> pwr.project().getId())
                  .collect(Collectors.toSet());
          filtered = filtered.stream().filter(p -> accessibleIds.contains(p.getId())).toList();
        }

        var projectIds = filtered.stream().map(Project::getId).toList();
        var tagsByEntityId = entityTagService.getEntityTagsBatch("PROJECT", projectIds);

        var responses =
            filtered.stream()
                .map(
                    p ->
                        ProjectResponse.from(
                            p, null, tagsByEntityId.getOrDefault(p.getId(), List.of())))
                .toList();
        return ResponseEntity.ok(responses);
      }
    }

    // --- Fallback: existing in-memory filtering ---
    var projectsWithRoles = projectService.listProjects(memberId, orgRole);

    // Batch-load tags for all projects (2 queries instead of 2N)
    var projectIds = projectsWithRoles.stream().map(pwr -> pwr.project().getId()).toList();
    var tagsByEntityId = entityTagService.getEntityTagsBatch("PROJECT", projectIds);

    var projects =
        projectsWithRoles.stream()
            .map(
                pwr ->
                    ProjectResponse.from(
                        pwr.project(),
                        pwr.projectRole(),
                        tagsByEntityId.getOrDefault(pwr.project().getId(), List.of())))
            .toList();

    // Apply custom field filtering if present
    Map<String, String> customFieldFilters = extractCustomFieldFilters(allParams);
    if (!customFieldFilters.isEmpty()) {
      projects =
          projects.stream()
              .filter(p -> matchesCustomFieldFilters(p.customFields(), customFieldFilters))
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
    return ResponseEntity.ok(ProjectResponse.from(pwr.project(), pwr.projectRole(), tags));
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
    return ResponseEntity.created(URI.create("/api/projects/" + project.getId()))
        .body(ProjectResponse.from(project, Roles.PROJECT_LEAD, List.of()));
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
    return ResponseEntity.ok(ProjectResponse.from(pwr.project(), pwr.projectRole(), tags));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ORG_OWNER')")
  public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
    projectService.deleteProject(id);
    return ResponseEntity.noContent().build();
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
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")
  public ResponseEntity<UnbilledTimeSummary> getUnbilledSummary(@PathVariable UUID id) {
    return ResponseEntity.ok(unbilledTimeSummaryService.getProjectUnbilledSummary(id));
  }

  private Map<String, String> extractCustomFieldFilters(Map<String, String> allParams) {
    var filters = new HashMap<String, String>();
    if (allParams != null) {
      allParams.forEach(
          (key, value) -> {
            if (key.startsWith("customField[") && key.endsWith("]")) {
              String slug = key.substring("customField[".length(), key.length() - 1);
              filters.put(slug, value);
            }
          });
    }
    return filters;
  }

  private boolean matchesCustomFieldFilters(
      Map<String, Object> customFields, Map<String, String> filters) {
    if (customFields == null) {
      return filters.isEmpty();
    }
    for (var entry : filters.entrySet()) {
      Object fieldValue = customFields.get(entry.getKey());
      if (fieldValue == null || !fieldValue.toString().equals(entry.getValue())) {
        return false;
      }
    }
    return true;
  }

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
      UUID createdBy,
      Instant createdAt,
      Instant updatedAt,
      String projectRole,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups,
      List<TagResponse> tags) {

    public static ProjectResponse from(Project project) {
      return new ProjectResponse(
          project.getId(),
          project.getName(),
          project.getDescription(),
          project.getCreatedBy(),
          project.getCreatedAt(),
          project.getUpdatedAt(),
          null,
          project.getCustomFields(),
          project.getAppliedFieldGroups(),
          List.of());
    }

    public static ProjectResponse from(Project project, String projectRole) {
      return new ProjectResponse(
          project.getId(),
          project.getName(),
          project.getDescription(),
          project.getCreatedBy(),
          project.getCreatedAt(),
          project.getUpdatedAt(),
          projectRole,
          project.getCustomFields(),
          project.getAppliedFieldGroups(),
          List.of());
    }

    public static ProjectResponse from(
        Project project, String projectRole, List<TagResponse> tags) {
      return new ProjectResponse(
          project.getId(),
          project.getName(),
          project.getDescription(),
          project.getCreatedBy(),
          project.getCreatedAt(),
          project.getUpdatedAt(),
          projectRole,
          project.getCustomFields(),
          project.getAppliedFieldGroups(),
          tags);
    }
  }
}
