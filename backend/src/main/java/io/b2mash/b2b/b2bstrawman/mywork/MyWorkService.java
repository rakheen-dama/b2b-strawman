package io.b2mash.b2b.b2bstrawman.mywork;

import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.MyWorkMemberTimeSummaryProjection;
import io.b2mash.b2b.b2bstrawman.timeentry.MyWorkProjectTimeSummaryProjection;
import io.b2mash.b2b.b2bstrawman.timeentry.TaskDurationProjection;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MyWorkService {

  private static final Logger log = LoggerFactory.getLogger(MyWorkService.class);

  private final TaskRepository taskRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final MemberRepository memberRepository;
  private final ProjectRepository projectRepository;

  public MyWorkService(
      TaskRepository taskRepository,
      TimeEntryRepository timeEntryRepository,
      MemberRepository memberRepository,
      ProjectRepository projectRepository) {
    this.taskRepository = taskRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.memberRepository = memberRepository;
    this.projectRepository = projectRepository;
  }

  /**
   * Returns the member's tasks — assigned tasks and/or unassigned tasks in their projects. No
   * ProjectAccessService call needed: query structure provides authorization (ADR-023).
   */
  @Transactional(readOnly = true)
  public MyWorkController.MyWorkTasksResponse getMyTasks(
      UUID memberId, String filter, String status, UUID projectId) {

    List<Task> assigned = Collections.emptyList();
    List<Task> unassigned = Collections.emptyList();

    boolean includeAssigned = filter == null || "all".equals(filter) || "assigned".equals(filter);
    boolean includeUnassigned =
        filter == null || "all".equals(filter) || "unassigned".equals(filter);

    if (includeAssigned) {
      assigned = taskRepository.findAssignedToMember(memberId);
    }
    if (includeUnassigned) {
      unassigned = taskRepository.findUnassignedInMemberProjects(memberId);
    }

    // Apply optional status filter
    if (status != null) {
      assigned = assigned.stream().filter(t -> status.equals(t.getStatus())).toList();
      unassigned = unassigned.stream().filter(t -> status.equals(t.getStatus())).toList();
    }

    // Apply optional projectId filter
    if (projectId != null) {
      assigned = assigned.stream().filter(t -> projectId.equals(t.getProjectId())).toList();
      unassigned = unassigned.stream().filter(t -> projectId.equals(t.getProjectId())).toList();
    }

    // Batch-load project names
    var allTasks = Stream.concat(assigned.stream(), unassigned.stream()).toList();
    var projectNames = resolveProjectNames(allTasks);

    // Batch-load total time per task (Task 48.5)
    var taskDurations = resolveTaskDurations(allTasks);

    var assignedItems =
        assigned.stream()
            .map(t -> MyWorkController.MyWorkTaskItem.from(t, projectNames, taskDurations))
            .toList();
    var unassignedItems =
        unassigned.stream()
            .map(t -> MyWorkController.MyWorkTaskItem.from(t, projectNames, taskDurations))
            .toList();

    return new MyWorkController.MyWorkTasksResponse(assignedItems, unassignedItems);
  }

  /**
   * Returns the member's time entries within the given date range. Enriched with task title and
   * project name.
   */
  @Transactional(readOnly = true)
  public List<MyWorkController.MyWorkTimeEntryItem> getMyTimeEntries(
      UUID memberId, LocalDate from, LocalDate to) {
    var entries = timeEntryRepository.findByMemberIdAndDateBetween(memberId, from, to);

    if (entries.isEmpty()) {
      return List.of();
    }

    // Batch-load task titles
    var taskIds = entries.stream().map(TimeEntry::getTaskId).distinct().toList();
    var tasks = taskRepository.findAllById(taskIds);
    var taskMap = tasks.stream().collect(Collectors.toMap(Task::getId, t -> t, (a, b) -> a));

    // Batch-load project names from the tasks' projects
    var projectIds =
        tasks.stream().map(Task::getProjectId).filter(Objects::nonNull).distinct().toList();
    var projectMap = batchLoadProjectNames(projectIds);

    return entries.stream()
        .map(
            e -> {
              var task = taskMap.get(e.getTaskId());
              String taskTitle = task != null ? task.getTitle() : null;
              UUID taskProjectId = task != null ? task.getProjectId() : null;
              String projectName =
                  taskProjectId != null ? projectMap.getOrDefault(taskProjectId, null) : null;
              return new MyWorkController.MyWorkTimeEntryItem(
                  e.getId(),
                  e.getTaskId(),
                  taskTitle,
                  taskProjectId,
                  projectName,
                  e.getDate(),
                  e.getDurationMinutes(),
                  e.isBillable(),
                  e.getDescription());
            })
        .toList();
  }

  /** Returns the member's time summary — total and by-project breakdown. */
  @Transactional(readOnly = true)
  public MyWorkController.MyWorkTimeSummaryResponse getMyTimeSummary(
      UUID memberId, LocalDate from, LocalDate to) {
    MyWorkMemberTimeSummaryProjection total =
        timeEntryRepository.memberTimeSummary(memberId, from, to);
    List<MyWorkProjectTimeSummaryProjection> byProject =
        timeEntryRepository.memberTimeSummaryByProject(memberId, from, to);

    var projectSummaries =
        byProject.stream()
            .map(
                p ->
                    new MyWorkController.MyWorkProjectSummary(
                        p.getProjectId(),
                        p.getProjectName(),
                        p.getBillableMinutes(),
                        p.getNonBillableMinutes(),
                        p.getTotalMinutes()))
            .toList();

    return new MyWorkController.MyWorkTimeSummaryResponse(
        memberId,
        from,
        to,
        total.getBillableMinutes(),
        total.getNonBillableMinutes(),
        total.getTotalMinutes(),
        projectSummaries);
  }

  /** Batch-loads project names for the given tasks. */
  private Map<UUID, String> resolveProjectNames(List<Task> tasks) {
    var projectIds =
        tasks.stream().map(Task::getProjectId).filter(Objects::nonNull).distinct().toList();
    return batchLoadProjectNames(projectIds);
  }

  private Map<UUID, String> batchLoadProjectNames(List<UUID> projectIds) {
    if (projectIds.isEmpty()) {
      return Map.of();
    }
    return projectRepository.findAllById(projectIds).stream()
        .collect(Collectors.toMap(Project::getId, Project::getName, (a, b) -> a));
  }

  /** Batch-loads total logged time per task (Task 48.5). */
  private Map<UUID, Long> resolveTaskDurations(List<Task> tasks) {
    var taskIds = tasks.stream().map(Task::getId).filter(Objects::nonNull).distinct().toList();
    if (taskIds.isEmpty()) {
      return Map.of();
    }
    return timeEntryRepository.sumDurationByTaskIds(taskIds).stream()
        .collect(
            Collectors.toMap(
                TaskDurationProjection::getTaskId,
                TaskDurationProjection::getTotalMinutes,
                (a, b) -> a));
  }
}
