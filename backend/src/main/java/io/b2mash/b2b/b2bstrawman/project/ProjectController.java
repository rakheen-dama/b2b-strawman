package io.b2mash.b2b.b2bstrawman.project;

import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.SetFieldGroupsRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.project.dto.MatterListRow;
import io.b2mash.b2b.b2bstrawman.project.dto.MatterResponse;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import io.b2mash.b2b.b2bstrawman.setupstatus.MatterSetupStatus;
import io.b2mash.b2b.b2bstrawman.setupstatus.ProjectSetupStatusService;
import io.b2mash.b2b.b2bstrawman.setupstatus.UnbilledTimeSummary;
import io.b2mash.b2b.b2bstrawman.setupstatus.UnbilledTimeSummaryService;
import io.b2mash.b2b.b2bstrawman.tag.EntityTagService;
import io.b2mash.b2b.b2bstrawman.tag.dto.SetEntityTagsRequest;
import io.b2mash.b2b.b2bstrawman.tag.dto.TagResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/matters")
public class ProjectController {

  private final ProjectService projectService;
  private final EntityTagService entityTagService;
  private final ProjectSetupStatusService projectSetupStatusService;
  private final UnbilledTimeSummaryService unbilledTimeSummaryService;
  private final ProjectUpcomingDeadlinesService projectUpcomingDeadlinesService;

  public ProjectController(
      ProjectService projectService,
      EntityTagService entityTagService,
      ProjectSetupStatusService projectSetupStatusService,
      UnbilledTimeSummaryService unbilledTimeSummaryService,
      ProjectUpcomingDeadlinesService projectUpcomingDeadlinesService) {
    this.projectService = projectService;
    this.entityTagService = entityTagService;
    this.projectSetupStatusService = projectSetupStatusService;
    this.unbilledTimeSummaryService = unbilledTimeSummaryService;
    this.projectUpcomingDeadlinesService = projectUpcomingDeadlinesService;
  }

  @GetMapping
  public ResponseEntity<List<MatterListRow>> listMatters(
      @RequestParam(required = false) UUID view,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) LocalDate dueBefore,
      @RequestParam(required = false) UUID customerId,
      @RequestParam(defaultValue = "false") boolean mine,
      @RequestParam(defaultValue = "false") boolean hasUnbilled,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate inactiveSince,
      @RequestParam(required = false) Map<String, String> allParams,
      ActorContext actor) {
    return ResponseEntity.ok(
        projectService.listMatterRows(
            new MatterListQuery(
                view, status, dueBefore, customerId, mine, hasUnbilled, inactiveSince, allParams),
            actor));
  }

  @GetMapping("/{matterId}")
  public ResponseEntity<MatterResponse> getProject(
      @PathVariable UUID matterId, ActorContext actor) {
    var pwr = projectService.getProject(matterId, actor);
    // ADR-342: storage key, do not rename
    var tags = entityTagService.getEntityTags("PROJECT", matterId);
    var memberNames = projectService.resolveProjectMemberNames(List.of(pwr.matter()));
    LocalDate retentionEndsOn = projectService.computeRetentionEndsOn(pwr.matter());
    return ResponseEntity.ok(
        MatterResponse.from(pwr.matter(), pwr.matterRole(), tags, memberNames, retentionEndsOn));
  }

  @PostMapping
  @RequiresCapability("PROJECT_MANAGEMENT")
  public ResponseEntity<MatterResponse> createProject(
      @Valid @RequestBody CreateMatterRequest request) {
    UUID createdBy = RequestScopes.requireMemberId();
    var project =
        projectService.createProject(
            request.name(),
            request.description(),
            createdBy,
            request.customFields(),
            request.appliedFieldGroups(),
            request.clientId(),
            request.dueDate(),
            request.referenceNumber(),
            request.priority(),
            request.workType());
    var memberNames = projectService.resolveProjectMemberNames(List.of(project));
    return ResponseEntity.created(URI.create("/api/matters/" + project.getId()))
        .body(MatterResponse.from(project, Roles.PROJECT_LEAD, memberNames));
  }

  @PutMapping("/{matterId}")
  public ResponseEntity<MatterResponse> updateProject(
      @PathVariable UUID matterId,
      @Valid @RequestBody UpdateMatterRequest request,
      ActorContext actor) {
    var pwr =
        projectService.updateProject(
            matterId,
            request.name(),
            request.description(),
            actor,
            request.customFields(),
            request.appliedFieldGroups(),
            request.clientId(),
            request.dueDate(),
            request.referenceNumber(),
            request.priority(),
            request.workType());
    // ADR-342: storage key, do not rename
    var tags = entityTagService.getEntityTags("PROJECT", matterId);
    var memberNames = projectService.resolveProjectMemberNames(List.of(pwr.matter()));
    return ResponseEntity.ok(
        MatterResponse.from(pwr.matter(), pwr.matterRole(), tags, memberNames));
  }

  @DeleteMapping("/{matterId}")
  public ResponseEntity<Void> deleteProject(@PathVariable UUID matterId) {
    projectService.deleteProject(matterId);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{matterId}/complete")
  @RequiresCapability("PROJECT_MANAGEMENT")
  public ResponseEntity<MatterResponse> completeProject(
      @PathVariable UUID matterId,
      @RequestBody(required = false) CompleteMatterRequest request,
      ActorContext actor) {
    boolean ack = request != null && Boolean.TRUE.equals(request.acknowledgeUnbilledTime());
    var project = projectService.completeProject(matterId, ack, actor);
    var memberNames = projectService.resolveProjectMemberNames(List.of(project));
    // ADR-342: storage key, do not rename
    var tags = entityTagService.getEntityTags("PROJECT", matterId);
    return ResponseEntity.ok(MatterResponse.from(project, null, tags, memberNames));
  }

  @PatchMapping("/{matterId}/archive")
  @RequiresCapability("PROJECT_MANAGEMENT")
  public ResponseEntity<MatterResponse> archiveProject(
      @PathVariable UUID matterId, ActorContext actor) {
    var project = projectService.archiveProject(matterId, actor);
    var memberNames = projectService.resolveProjectMemberNames(List.of(project));
    // ADR-342: storage key, do not rename
    var tags = entityTagService.getEntityTags("PROJECT", matterId);
    return ResponseEntity.ok(MatterResponse.from(project, null, tags, memberNames));
  }

  @PatchMapping("/{matterId}/reopen")
  @RequiresCapability("PROJECT_MANAGEMENT")
  public ResponseEntity<MatterResponse> reopenProject(
      @PathVariable UUID matterId, ActorContext actor) {
    var project = projectService.reopenProject(matterId, actor);
    var memberNames = projectService.resolveProjectMemberNames(List.of(project));
    // ADR-342: storage key, do not rename
    var tags = entityTagService.getEntityTags("PROJECT", matterId);
    return ResponseEntity.ok(MatterResponse.from(project, null, tags, memberNames));
  }

  @PutMapping("/{matterId}/field-groups")
  @RequiresCapability("PROJECT_MANAGEMENT")
  public ResponseEntity<List<FieldDefinitionResponse>> setFieldGroups(
      @PathVariable UUID matterId,
      @Valid @RequestBody SetFieldGroupsRequest request,
      ActorContext actor) {
    var fieldDefs = projectService.setFieldGroups(matterId, request.appliedFieldGroups(), actor);
    return ResponseEntity.ok(fieldDefs);
  }

  @PostMapping("/{matterId}/tags")
  public ResponseEntity<List<TagResponse>> setProjectTags(
      @PathVariable UUID matterId,
      @Valid @RequestBody SetEntityTagsRequest request,
      ActorContext actor) {
    // Verify project access
    projectService.getProject(matterId, actor);
    // ADR-342: storage key, do not rename
    var tags = entityTagService.setEntityTags("PROJECT", matterId, request.tagIds());
    return ResponseEntity.ok(tags);
  }

  @GetMapping("/{matterId}/tags")
  public ResponseEntity<List<TagResponse>> getProjectTags(
      @PathVariable UUID matterId, ActorContext actor) {
    // Verify project access
    projectService.getProject(matterId, actor);
    // ADR-342: storage key, do not rename
    var tags = entityTagService.getEntityTags("PROJECT", matterId);
    return ResponseEntity.ok(tags);
  }

  @GetMapping("/{matterId}/setup-status")
  public ResponseEntity<MatterSetupStatus> getSetupStatus(@PathVariable UUID matterId) {
    return ResponseEntity.ok(projectSetupStatusService.getSetupStatus(matterId));
  }

  @GetMapping("/{matterId}/unbilled-summary")
  @RequiresCapability("PROJECT_MANAGEMENT")
  public ResponseEntity<UnbilledTimeSummary> getUnbilledSummary(@PathVariable UUID matterId) {
    return ResponseEntity.ok(unbilledTimeSummaryService.getProjectUnbilledSummary(matterId));
  }

  @GetMapping("/{matterId}/upcoming-deadlines")
  public ResponseEntity<List<MatterUpcomingDeadline>> getUpcomingDeadlines(
      @PathVariable UUID matterId, ActorContext actor) {
    return ResponseEntity.ok(projectUpcomingDeadlinesService.getUpcomingDeadlines(matterId, actor));
  }

  public record CompleteMatterRequest(Boolean acknowledgeUnbilledTime) {}

  public record CreateMatterRequest(
      @NotBlank(message = "name is required")
          @Size(max = 255, message = "name must be at most 255 characters")
          String name,
      @Size(max = 2000, message = "description must be at most 2000 characters") String description,
      UUID clientId,
      LocalDate dueDate,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups,
      @Size(max = 100, message = "referenceNumber must be at most 100 characters")
          String referenceNumber,
      ProjectPriority priority,
      @Size(max = 50, message = "workType must be at most 50 characters") String workType) {}

  public record UpdateMatterRequest(
      @NotBlank(message = "name is required")
          @Size(max = 255, message = "name must be at most 255 characters")
          String name,
      @Size(max = 2000, message = "description must be at most 2000 characters") String description,
      UUID clientId,
      LocalDate dueDate,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups,
      @Size(max = 100, message = "referenceNumber must be at most 100 characters")
          String referenceNumber,
      ProjectPriority priority,
      @Size(max = 50, message = "workType must be at most 50 characters") String workType) {}
}
