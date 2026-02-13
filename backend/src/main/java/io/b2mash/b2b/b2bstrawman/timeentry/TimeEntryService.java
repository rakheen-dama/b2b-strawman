package io.b2mash.b2b.b2bstrawman.timeentry;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.billingrate.BillingRateService;
import io.b2mash.b2b.b2bstrawman.costrate.CostRateService;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
  private final AuditService auditService;
  private final BillingRateService billingRateService;
  private final CostRateService costRateService;

  public TimeEntryService(
      TimeEntryRepository timeEntryRepository,
      TaskRepository taskRepository,
      ProjectAccessService projectAccessService,
      AuditService auditService,
      BillingRateService billingRateService,
      CostRateService costRateService) {
    this.timeEntryRepository = timeEntryRepository;
    this.taskRepository = taskRepository;
    this.projectAccessService = projectAccessService;
    this.auditService = auditService;
    this.billingRateService = billingRateService;
    this.costRateService = costRateService;
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

    // Snapshot billing rate (ADR-040)
    var billingRate = billingRateService.resolveRate(memberId, task.getProjectId(), date);
    billingRate.ifPresent(r -> entry.snapshotBillingRate(r.hourlyRate(), r.currency()));

    // Snapshot cost rate
    var costRate = costRateService.resolveCostRate(memberId, date);
    costRate.ifPresent(r -> entry.snapshotCostRate(r.hourlyCost(), r.currency()));

    var saved = timeEntryRepository.save(entry);
    log.info("Created time entry {} for task {} by member {}", saved.getId(), taskId, memberId);

    var auditDetails = new LinkedHashMap<String, Object>();
    auditDetails.put("task_id", taskId.toString());
    auditDetails.put("duration_minutes", durationMinutes);
    auditDetails.put("billable", billable);
    auditDetails.put("project_id", task.getProjectId().toString());
    if (saved.getBillingRateSnapshot() != null) {
      auditDetails.put("billing_rate_snapshot", saved.getBillingRateSnapshot().toString());
      auditDetails.put("billing_rate_currency", saved.getBillingRateCurrency());
    }
    if (saved.getCostRateSnapshot() != null) {
      auditDetails.put("cost_rate_snapshot", saved.getCostRateSnapshot().toString());
      auditDetails.put("cost_rate_currency", saved.getCostRateCurrency());
    }

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("time_entry.created")
            .entityType("time_entry")
            .entityId(saved.getId())
            .details(auditDetails)
            .build());

    return saved;
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

    UUID entryTaskId = entry.getTaskId();
    var task =
        taskRepository
            .findOneById(entryTaskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", entryTaskId));

    if (durationMinutes != null && durationMinutes <= 0) {
      throw new InvalidStateException(
          "Invalid duration", "Duration must be greater than 0 minutes");
    }

    if (rateCents != null && rateCents < 0) {
      throw new InvalidStateException("Invalid rate", "Rate cents must be non-negative");
    }

    // Capture old values before mutation
    LocalDate oldDate = entry.getDate();
    int oldDurationMinutes = entry.getDurationMinutes();
    boolean oldBillable = entry.isBillable();
    Integer oldRateCents = entry.getRateCents();
    String oldDescription = entry.getDescription();
    BigDecimal oldBillingRateSnapshot = entry.getBillingRateSnapshot();
    String oldBillingRateCurrency = entry.getBillingRateCurrency();
    BigDecimal oldCostRateSnapshot = entry.getCostRateSnapshot();
    String oldCostRateCurrency = entry.getCostRateCurrency();

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

    // Re-snapshot rates only when date changes (ADR-040: point-in-time snapshotting).
    // Billable flag changes do NOT trigger re-snapshot — the rate captured at creation
    // (or last date change) remains valid regardless of billable status.
    boolean dateChanged = date != null && !Objects.equals(oldDate, date);
    if (dateChanged) {
      LocalDate effectiveDate = entry.getDate();
      var billingRate =
          billingRateService.resolveRate(entry.getMemberId(), task.getProjectId(), effectiveDate);
      if (billingRate.isPresent()) {
        entry.snapshotBillingRate(billingRate.get().hourlyRate(), billingRate.get().currency());
      } else {
        entry.snapshotBillingRate(null, null);
      }

      var costRate = costRateService.resolveCostRate(entry.getMemberId(), effectiveDate);
      if (costRate.isPresent()) {
        entry.snapshotCostRate(costRate.get().hourlyCost(), costRate.get().currency());
      } else {
        entry.snapshotCostRate(null, null);
      }
    }

    entry.setUpdatedAt(Instant.now());

    entry = timeEntryRepository.save(entry);
    log.info("Updated time entry {} by member {}", timeEntryId, memberId);

    // Build delta map -- only include fields that were provided AND actually changed
    var details = new LinkedHashMap<String, Object>();
    if (date != null && !Objects.equals(oldDate, date)) {
      details.put(
          "date", Map.of("from", oldDate != null ? oldDate.toString() : "", "to", date.toString()));
    }
    if (durationMinutes != null && oldDurationMinutes != durationMinutes) {
      details.put("duration_minutes", Map.of("from", oldDurationMinutes, "to", durationMinutes));
    }
    if (billable != null && oldBillable != billable) {
      details.put("billable", Map.of("from", oldBillable, "to", billable));
    }
    if (rateCents != null && !Objects.equals(oldRateCents, rateCents)) {
      details.put(
          "rate_cents", Map.of("from", oldRateCents != null ? oldRateCents : 0, "to", rateCents));
    }
    if (description != null && !Objects.equals(oldDescription, description)) {
      details.put(
          "description",
          Map.of("from", oldDescription != null ? oldDescription : "", "to", description));
    }

    // Include snapshot deltas if date changed
    if (dateChanged) {
      if (!bigDecimalEquals(oldBillingRateSnapshot, entry.getBillingRateSnapshot())) {
        details.put(
            "billing_rate_snapshot",
            Map.of(
                "from",
                oldBillingRateSnapshot != null ? oldBillingRateSnapshot.toString() : "",
                "to",
                entry.getBillingRateSnapshot() != null
                    ? entry.getBillingRateSnapshot().toString()
                    : ""));
      }
      if (!Objects.equals(oldBillingRateCurrency, entry.getBillingRateCurrency())) {
        details.put(
            "billing_rate_currency",
            Map.of(
                "from",
                oldBillingRateCurrency != null ? oldBillingRateCurrency : "",
                "to",
                entry.getBillingRateCurrency() != null ? entry.getBillingRateCurrency() : ""));
      }
      if (!bigDecimalEquals(oldCostRateSnapshot, entry.getCostRateSnapshot())) {
        details.put(
            "cost_rate_snapshot",
            Map.of(
                "from",
                oldCostRateSnapshot != null ? oldCostRateSnapshot.toString() : "",
                "to",
                entry.getCostRateSnapshot() != null ? entry.getCostRateSnapshot().toString() : ""));
      }
    }

    details.put("project_id", task.getProjectId().toString());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("time_entry.updated")
            .entityType("time_entry")
            .entityId(entry.getId())
            .details(details)
            .build());

    return entry;
  }

  @Transactional
  public void deleteTimeEntry(UUID timeEntryId, UUID memberId, String orgRole) {
    var entry =
        timeEntryRepository
            .findOneById(timeEntryId)
            .orElseThrow(() -> new ResourceNotFoundException("TimeEntry", timeEntryId));

    requireEditPermission(entry, memberId, orgRole);

    var task =
        taskRepository
            .findOneById(entry.getTaskId())
            .orElseThrow(() -> new ResourceNotFoundException("Task", entry.getTaskId()));

    timeEntryRepository.delete(entry);
    log.info("Deleted time entry {} by member {}", timeEntryId, memberId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("time_entry.deleted")
            .entityType("time_entry")
            .entityId(entry.getId())
            .details(
                Map.of(
                    "task_id", entry.getTaskId().toString(),
                    "project_id", task.getProjectId().toString()))
            .build());
  }

  // --- Project time summary aggregation methods (Epic 46A) ---

  @Transactional(readOnly = true)
  public ProjectTimeSummaryProjection getProjectTimeSummary(
      UUID projectId, UUID memberId, String orgRole, LocalDate from, LocalDate to) {
    projectAccessService.requireViewAccess(projectId, memberId, orgRole);
    return timeEntryRepository.projectTimeSummary(projectId, from, to);
  }

  @Transactional(readOnly = true)
  public List<MemberTimeSummaryProjection> getProjectTimeSummaryByMember(
      UUID projectId, UUID memberId, String orgRole, LocalDate from, LocalDate to) {
    var access = projectAccessService.requireViewAccess(projectId, memberId, orgRole);
    if (!access.canEdit()) {
      throw new ForbiddenException(
          "Cannot view member breakdown",
          "Per-member time breakdown is restricted to project leads, org admins, and org owners");
    }
    return timeEntryRepository.projectTimeSummaryByMember(projectId, from, to);
  }

  @Transactional(readOnly = true)
  public List<TaskTimeSummaryProjection> getProjectTimeSummaryByTask(
      UUID projectId, UUID memberId, String orgRole, LocalDate from, LocalDate to) {
    projectAccessService.requireViewAccess(projectId, memberId, orgRole);
    return timeEntryRepository.projectTimeSummaryByTask(projectId, from, to);
  }

  private static boolean bigDecimalEquals(BigDecimal a, BigDecimal b) {
    if (a == null && b == null) return true;
    if (a == null || b == null) return false;
    return a.compareTo(b) == 0;
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
