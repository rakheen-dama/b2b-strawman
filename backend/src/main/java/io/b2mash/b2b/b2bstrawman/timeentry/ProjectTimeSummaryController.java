package io.b2mash.b2b.b2bstrawman.timeentry;

import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
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

  @GetMapping("/api/matters/{matterId}/time-summary")
  public ResponseEntity<MatterTimeSummaryResponse> getProjectTimeSummary(
      @PathVariable UUID matterId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      ActorContext actor) {

    var summary = timeEntryService.getProjectTimeSummary(matterId, actor, from, to);
    return ResponseEntity.ok(MatterTimeSummaryResponse.from(matterId, summary));
  }

  @GetMapping("/api/matters/{matterId}/time-summary/by-member")
  public ResponseEntity<List<MemberTimeSummaryResponse>> getProjectTimeSummaryByMember(
      @PathVariable UUID matterId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      ActorContext actor) {

    var summaries = timeEntryService.getProjectTimeSummaryByMember(matterId, actor, from, to);
    var response = summaries.stream().map(MemberTimeSummaryResponse::from).toList();
    return ResponseEntity.ok(response);
  }

  @GetMapping("/api/matters/{matterId}/time-summary/by-task")
  public ResponseEntity<List<TaskTimeSummaryResponse>> getProjectTimeSummaryByTask(
      @PathVariable UUID matterId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      ActorContext actor) {

    var summaries = timeEntryService.getProjectTimeSummaryByTask(matterId, actor, from, to);
    var response = summaries.stream().map(TaskTimeSummaryResponse::from).toList();
    return ResponseEntity.ok(response);
  }

  // --- DTOs ---

  public record MatterTimeSummaryResponse(
      UUID matterId,
      long billableMinutes,
      long nonBillableMinutes,
      long totalMinutes,
      long contributorCount,
      long entryCount) {

    public static MatterTimeSummaryResponse from(
        UUID matterId, ProjectTimeSummaryProjection projection) {
      return new MatterTimeSummaryResponse(
          matterId,
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
