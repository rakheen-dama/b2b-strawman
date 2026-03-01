package io.b2mash.b2b.b2bstrawman.calendar;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import io.b2mash.b2b.b2bstrawman.task.Task;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarService {

  private static final Set<String> VALID_TYPES = Set.of("TASK", "PROJECT");

  private final EntityManager entityManager;

  public CalendarService(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  // --- DTOs ---

  public record CalendarItemDto(
      UUID id,
      String name,
      String itemType,
      LocalDate dueDate,
      String status,
      String priority,
      UUID assigneeId,
      UUID projectId,
      String projectName) {}

  public record CalendarResponse(List<CalendarItemDto> items, int overdueCount) {}

  @Transactional(readOnly = true)
  public CalendarResponse getCalendarItems(
      UUID memberId,
      String orgRole,
      LocalDate from,
      LocalDate to,
      UUID filterProjectId,
      String filterType,
      UUID filterAssigneeId,
      boolean includeOverdue) {

    validateDateRange(from, to);
    validateType(filterType);

    boolean isAdminOrOwner = Roles.ORG_OWNER.equals(orgRole) || Roles.ORG_ADMIN.equals(orgRole);

    List<CalendarItemDto> items = new ArrayList<>();

    // Query tasks in date range
    if (filterType == null || "TASK".equals(filterType)) {
      List<Task> tasks = queryTasks(from, to, memberId, isAdminOrOwner);
      items.addAll(toTaskDtos(tasks));
    }

    // Query projects in date range
    if (filterType == null || "PROJECT".equals(filterType)) {
      List<Project> projects = queryProjects(from, to, memberId, isAdminOrOwner);
      items.addAll(toProjectDtos(projects));
    }

    // Query overdue items
    List<CalendarItemDto> overdueItems = new ArrayList<>();
    if (includeOverdue) {
      if (filterType == null || "TASK".equals(filterType)) {
        List<Task> overdueTasks = queryOverdueTasks(from, memberId, isAdminOrOwner);
        overdueItems.addAll(toTaskDtos(overdueTasks));
      }

      if (filterType == null || "PROJECT".equals(filterType)) {
        List<Project> overdueProjects = queryOverdueProjects(from, memberId, isAdminOrOwner);
        overdueItems.addAll(toProjectDtos(overdueProjects));
      }

      items.addAll(overdueItems);
    }

    // Apply optional filters (projectId, assigneeId)
    var filtered =
        items.stream()
            .filter(item -> filterProjectId == null || filterProjectId.equals(item.projectId()))
            .filter(item -> filterAssigneeId == null || filterAssigneeId.equals(item.assigneeId()))
            .sorted(Comparator.comparing(CalendarItemDto::dueDate))
            .toList();

    // Calculate overdueCount AFTER applying the same filters
    int overdueCount = 0;
    if (includeOverdue) {
      overdueCount =
          (int)
              overdueItems.stream()
                  .filter(
                      item -> filterProjectId == null || filterProjectId.equals(item.projectId()))
                  .filter(
                      item ->
                          filterAssigneeId == null || filterAssigneeId.equals(item.assigneeId()))
                  .count();
    }

    return new CalendarResponse(filtered, overdueCount);
  }

  private void validateDateRange(LocalDate from, LocalDate to) {
    if (from.isAfter(to)) {
      throw new InvalidStateException(
          "Invalid date range", "'from' date must be on or before 'to' date");
    }
    if (ChronoUnit.DAYS.between(from, to) > 366) {
      throw new InvalidStateException("Invalid date range", "Date range must not exceed 366 days");
    }
  }

  private void validateType(String type) {
    if (type != null && !VALID_TYPES.contains(type)) {
      throw new InvalidStateException(
          "Invalid type filter",
          "Type must be one of: " + VALID_TYPES + ", but got: '" + type + "'");
    }
  }

  // --- Task Queries ---

  private List<Task> queryTasks(LocalDate from, LocalDate to, UUID memberId, boolean isAdmin) {
    if (isAdmin) {
      return entityManager
          .createQuery(
              """
              SELECT t FROM Task t
              WHERE t.dueDate BETWEEN :from AND :to
                AND t.status NOT IN ('DONE', 'CANCELLED')
              ORDER BY t.dueDate ASC
              """,
              Task.class)
          .setParameter("from", from)
          .setParameter("to", to)
          .getResultList();
    }
    return entityManager
        .createQuery(
            """
            SELECT t FROM Task t
            WHERE t.dueDate BETWEEN :from AND :to
              AND t.status NOT IN ('DONE', 'CANCELLED')
              AND t.projectId IN (
                SELECT pm.projectId FROM ProjectMember pm WHERE pm.memberId = :memberId
              )
            ORDER BY t.dueDate ASC
            """,
            Task.class)
        .setParameter("from", from)
        .setParameter("to", to)
        .setParameter("memberId", memberId)
        .getResultList();
  }

  private List<Task> queryOverdueTasks(LocalDate from, UUID memberId, boolean isAdmin) {
    if (isAdmin) {
      return entityManager
          .createQuery(
              """
              SELECT t FROM Task t
              WHERE t.dueDate < :from
                AND t.status IN ('OPEN', 'IN_PROGRESS')
              ORDER BY t.dueDate ASC
              """,
              Task.class)
          .setParameter("from", from)
          .getResultList();
    }
    return entityManager
        .createQuery(
            """
            SELECT t FROM Task t
            WHERE t.dueDate < :from
              AND t.status IN ('OPEN', 'IN_PROGRESS')
              AND t.projectId IN (
                SELECT pm.projectId FROM ProjectMember pm WHERE pm.memberId = :memberId
              )
            ORDER BY t.dueDate ASC
            """,
            Task.class)
        .setParameter("from", from)
        .setParameter("memberId", memberId)
        .getResultList();
  }

  // --- Project Queries ---

  private List<Project> queryProjects(
      LocalDate from, LocalDate to, UUID memberId, boolean isAdmin) {
    if (isAdmin) {
      return entityManager
          .createQuery(
              """
              SELECT p FROM Project p
              WHERE p.dueDate BETWEEN :from AND :to
                AND p.status <> 'ARCHIVED'
              ORDER BY p.dueDate ASC
              """,
              Project.class)
          .setParameter("from", from)
          .setParameter("to", to)
          .getResultList();
    }
    return entityManager
        .createQuery(
            """
            SELECT p FROM Project p
            WHERE p.dueDate BETWEEN :from AND :to
              AND p.status <> 'ARCHIVED'
              AND p.id IN (
                SELECT pm.projectId FROM ProjectMember pm WHERE pm.memberId = :memberId
              )
            ORDER BY p.dueDate ASC
            """,
            Project.class)
        .setParameter("from", from)
        .setParameter("to", to)
        .setParameter("memberId", memberId)
        .getResultList();
  }

  private List<Project> queryOverdueProjects(LocalDate from, UUID memberId, boolean isAdmin) {
    if (isAdmin) {
      return entityManager
          .createQuery(
              """
              SELECT p FROM Project p
              WHERE p.dueDate < :from
                AND p.status = 'ACTIVE'
              ORDER BY p.dueDate ASC
              """,
              Project.class)
          .setParameter("from", from)
          .getResultList();
    }
    return entityManager
        .createQuery(
            """
            SELECT p FROM Project p
            WHERE p.dueDate < :from
              AND p.status = 'ACTIVE'
              AND p.id IN (
                SELECT pm.projectId FROM ProjectMember pm WHERE pm.memberId = :memberId
              )
            ORDER BY p.dueDate ASC
            """,
            Project.class)
        .setParameter("from", from)
        .setParameter("memberId", memberId)
        .getResultList();
  }

  // --- DTO Mapping ---

  private List<CalendarItemDto> toTaskDtos(List<Task> tasks) {
    if (tasks.isEmpty()) {
      return List.of();
    }

    // Batch load project names
    var projectIds = tasks.stream().map(Task::getProjectId).distinct().toList();
    Map<UUID, String> projectNames = loadProjectNames(projectIds);

    return tasks.stream()
        .map(
            t ->
                new CalendarItemDto(
                    t.getId(),
                    t.getTitle(),
                    "TASK",
                    t.getDueDate(),
                    t.getStatus().name(),
                    t.getPriority().name(),
                    t.getAssigneeId(),
                    t.getProjectId(),
                    projectNames.getOrDefault(t.getProjectId(), null)))
        .toList();
  }

  private List<CalendarItemDto> toProjectDtos(List<Project> projects) {
    return projects.stream()
        .map(
            p ->
                new CalendarItemDto(
                    p.getId(),
                    p.getName(),
                    "PROJECT",
                    p.getDueDate(),
                    p.getStatus().name(),
                    null,
                    null,
                    p.getId(),
                    p.getName()))
        .toList();
  }

  private Map<UUID, String> loadProjectNames(List<UUID> projectIds) {
    if (projectIds.isEmpty()) {
      return Map.of();
    }
    return entityManager
        .createQuery("SELECT p FROM Project p WHERE p.id IN :ids", Project.class)
        .setParameter("ids", projectIds)
        .getResultList()
        .stream()
        .collect(Collectors.toMap(Project::getId, Project::getName, (a, b) -> a));
  }
}
