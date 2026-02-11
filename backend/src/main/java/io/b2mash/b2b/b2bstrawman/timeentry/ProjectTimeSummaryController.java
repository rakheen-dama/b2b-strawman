package io.b2mash.b2b.b2bstrawman.timeentry;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProjectTimeSummaryController {

  private final TimeEntryService timeEntryService;

  public ProjectTimeSummaryController(TimeEntryService timeEntryService) {
    this.timeEntryService = timeEntryService;
  }

  @GetMapping("/api/projects/{id}/time-summary")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ProjectTimeSummaryResponse> getProjectTimeSummary(
      @PathVariable UUID id,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var summary = timeEntryService.getProjectTimeSummary(id, memberId, orgRole, from, to);
    return ResponseEntity.ok(ProjectTimeSummaryResponse.from(id, summary));
  }

  @GetMapping("/api/projects/{id}/time-summary/by-member")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<MemberTimeSummaryResponse>> getProjectTimeSummaryByMember(
      @PathVariable UUID id,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var summaries = timeEntryService.getProjectTimeSummaryByMember(id, memberId, orgRole, from, to);
    var response = summaries.stream().map(MemberTimeSummaryResponse::from).toList();
    return ResponseEntity.ok(response);
  }

  @GetMapping("/api/projects/{id}/time-summary/by-task")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<TaskTimeSummaryResponse>> getProjectTimeSummaryByTask(
      @PathVariable UUID id,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var summaries = timeEntryService.getProjectTimeSummaryByTask(id, memberId, orgRole, from, to);
    var response = summaries.stream().map(TaskTimeSummaryResponse::from).toList();
    return ResponseEntity.ok(response);
  }

  // --- DTOs ---

  public record ProjectTimeSummaryResponse(
      UUID projectId,
      long billableMinutes,
      long nonBillableMinutes,
      long totalMinutes,
      long contributorCount,
      long entryCount) {

    public static ProjectTimeSummaryResponse from(
        UUID projectId, ProjectTimeSummaryProjection projection) {
      return new ProjectTimeSummaryResponse(
          projectId,
          projection.getBillableMinutes(),
          projection.getNonBillableMinutes(),
          projection.getTotalMinutes(),
          projection.getContributorCount(),
          projection.getEntryCount());
    }
  }

  public record MemberTimeSummaryResponse(
      UUID memberId,
      String memberName,
      long billableMinutes,
      long nonBillableMinutes,
      long totalMinutes) {

    public static MemberTimeSummaryResponse from(MemberTimeSummaryProjection projection) {
      return new MemberTimeSummaryResponse(
          projection.getMemberId(),
          projection.getMemberName(),
          projection.getBillableMinutes(),
          projection.getNonBillableMinutes(),
          projection.getTotalMinutes());
    }
  }

  public record TaskTimeSummaryResponse(
      UUID taskId, String taskTitle, long billableMinutes, long totalMinutes, long entryCount) {

    public static TaskTimeSummaryResponse from(TaskTimeSummaryProjection projection) {
      return new TaskTimeSummaryResponse(
          projection.getTaskId(),
          projection.getTaskTitle(),
          projection.getBillableMinutes(),
          projection.getTotalMinutes(),
          projection.getEntryCount());
    }
  }
}
