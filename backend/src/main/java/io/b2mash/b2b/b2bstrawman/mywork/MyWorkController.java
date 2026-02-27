package io.b2mash.b2b.b2bstrawman.mywork;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.task.Task;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/my-work")
public class MyWorkController {

  private final MyWorkService myWorkService;

  public MyWorkController(MyWorkService myWorkService) {
    this.myWorkService = myWorkService;
  }

  @GetMapping("/tasks")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<MyWorkTasksResponse> getMyTasks(
      @RequestParam(required = false) String filter,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) UUID projectId) {
    UUID memberId = RequestScopes.requireMemberId();

    var response = myWorkService.getMyTasks(memberId, filter, status, projectId);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/time-entries")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<MyWorkTimeEntryItem>> getMyTimeEntries(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    UUID memberId = RequestScopes.requireMemberId();

    // Default to today if not specified
    if (from == null) {
      from = LocalDate.now();
    }
    if (to == null) {
      to = LocalDate.now();
    }

    var entries = myWorkService.getMyTimeEntries(memberId, from, to);
    return ResponseEntity.ok(entries);
  }

  @GetMapping("/time-summary")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<MyWorkTimeSummaryResponse> getMyTimeSummary(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    UUID memberId = RequestScopes.requireMemberId();

    var summary = myWorkService.getMyTimeSummary(memberId, from, to);
    return ResponseEntity.ok(summary);
  }

  // --- DTOs ---

  public record MyWorkTasksResponse(
      List<MyWorkTaskItem> assigned, List<MyWorkTaskItem> unassigned) {}

  public record MyWorkTaskItem(
      UUID id,
      UUID projectId,
      String projectName,
      String title,
      String status,
      String priority,
      LocalDate dueDate,
      long totalTimeMinutes) {

    public static MyWorkTaskItem from(
        Task task, Map<UUID, String> projectNames, Map<UUID, Long> taskDurations) {
      return new MyWorkTaskItem(
          task.getId(),
          task.getProjectId(),
          projectNames.getOrDefault(task.getProjectId(), null),
          task.getTitle(),
          task.getStatus().name(),
          task.getPriority().name(),
          task.getDueDate(),
          taskDurations.getOrDefault(task.getId(), 0L));
    }
  }

  public record MyWorkTimeEntryItem(
      UUID id,
      UUID taskId,
      String taskTitle,
      UUID projectId,
      String projectName,
      LocalDate date,
      int durationMinutes,
      boolean billable,
      String description) {}

  public record MyWorkTimeSummaryResponse(
      UUID memberId,
      LocalDate fromDate,
      LocalDate toDate,
      long billableMinutes,
      long nonBillableMinutes,
      long totalMinutes,
      List<MyWorkProjectSummary> byProject) {}

  public record MyWorkProjectSummary(
      UUID projectId,
      String projectName,
      long billableMinutes,
      long nonBillableMinutes,
      long totalMinutes) {}
}
