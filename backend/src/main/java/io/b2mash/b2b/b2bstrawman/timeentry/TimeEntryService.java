package io.b2mash.b2b.b2bstrawman.timeentry;

import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimeEntryService {

  private static final Logger log = LoggerFactory.getLogger(TimeEntryService.class);

  private final TimeEntryRepository timeEntryRepository;
  private final TaskRepository taskRepository;
  private final ProjectAccessService projectAccessService;

  public TimeEntryService(
      TimeEntryRepository timeEntryRepository,
      TaskRepository taskRepository,
      ProjectAccessService projectAccessService) {
    this.timeEntryRepository = timeEntryRepository;
    this.taskRepository = taskRepository;
    this.projectAccessService = projectAccessService;
  }

  @Transactional
  public TimeEntry createTimeEntry(
      UUID taskId,
      LocalDate date,
      int durationMinutes,
      boolean billable,
      Integer rateCents,
      String description,
      UUID memberId,
      String orgRole) {
    var task =
        taskRepository
            .findOneById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

    projectAccessService.requireViewAccess(task.getProjectId(), memberId, orgRole);

    if (durationMinutes <= 0) {
      throw new InvalidStateException(
          "Invalid duration", "Duration must be greater than 0 minutes");
    }

    if (rateCents != null && rateCents < 0) {
      throw new InvalidStateException("Invalid rate", "Rate cents must be non-negative");
    }

    var entry =
        new TimeEntry(taskId, memberId, date, durationMinutes, billable, rateCents, description);
    entry = timeEntryRepository.save(entry);
    log.info("Created time entry {} for task {} by member {}", entry.getId(), taskId, memberId);
    return entry;
  }

  @Transactional(readOnly = true)
  public List<TimeEntry> listTimeEntriesByTask(UUID taskId, UUID memberId, String orgRole) {
    var task =
        taskRepository
            .findOneById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

    projectAccessService.requireViewAccess(task.getProjectId(), memberId, orgRole);

    return timeEntryRepository.findByTaskId(taskId);
  }

  @Transactional
  public TimeEntry updateTimeEntry(
      UUID timeEntryId,
      LocalDate date,
      Integer durationMinutes,
      Boolean billable,
      Integer rateCents,
      String description,
      UUID memberId,
      String orgRole) {
    var entry =
        timeEntryRepository
            .findOneById(timeEntryId)
            .orElseThrow(() -> new ResourceNotFoundException("TimeEntry", timeEntryId));

    requireEditPermission(entry, memberId, orgRole);

    if (durationMinutes != null && durationMinutes <= 0) {
      throw new InvalidStateException(
          "Invalid duration", "Duration must be greater than 0 minutes");
    }

    if (rateCents != null && rateCents < 0) {
      throw new InvalidStateException("Invalid rate", "Rate cents must be non-negative");
    }

    if (date != null) {
      entry.setDate(date);
    }
    if (durationMinutes != null) {
      entry.setDurationMinutes(durationMinutes);
    }
    if (billable != null) {
      entry.setBillable(billable);
    }
    if (rateCents != null) {
      entry.setRateCents(rateCents);
    }
    if (description != null) {
      entry.setDescription(description);
    }
    entry.setUpdatedAt(Instant.now());

    entry = timeEntryRepository.save(entry);
    log.info("Updated time entry {} by member {}", timeEntryId, memberId);
    return entry;
  }

  @Transactional
  public void deleteTimeEntry(UUID timeEntryId, UUID memberId, String orgRole) {
    var entry =
        timeEntryRepository
            .findOneById(timeEntryId)
            .orElseThrow(() -> new ResourceNotFoundException("TimeEntry", timeEntryId));

    requireEditPermission(entry, memberId, orgRole);

    timeEntryRepository.delete(entry);
    log.info("Deleted time entry {} by member {}", timeEntryId, memberId);
  }

  /**
   * Checks that the caller has permission to edit/delete the given time entry. The creator can
   * always modify their own entries. Otherwise, the caller must have canEdit() access on the
   * entry's project (project lead, org admin, or org owner).
   */
  private void requireEditPermission(TimeEntry entry, UUID memberId, String orgRole) {
    if (entry.getMemberId().equals(memberId)) {
      return; // creator can always modify own entries
    }

    var task =
        taskRepository
            .findOneById(entry.getTaskId())
            .orElseThrow(() -> new ResourceNotFoundException("Task", entry.getTaskId()));

    var access = projectAccessService.checkAccess(task.getProjectId(), memberId, orgRole);
    if (!access.canEdit()) {
      throw new ForbiddenException(
          "Cannot modify time entry",
          "Only the creator or a project lead/admin/owner can modify this time entry");
    }
  }
}
