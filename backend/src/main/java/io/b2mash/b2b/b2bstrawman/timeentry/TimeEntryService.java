package io.b2mash.b2b.b2bstrawman.timeentry;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.budget.BudgetCheckService;
import io.b2mash.b2b.b2bstrawman.event.TimeEntryChangedEvent;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimeEntryService {

  private static final Logger log = LoggerFactory.getLogger(TimeEntryService.class);

  private final TimeEntryRepository timeEntryRepository;
  private final TaskRepository taskRepository;
  private final ProjectAccessService projectAccessService;
  private final AuditService auditService;
  private final BudgetCheckService budgetCheckService;
  private final MemberNameResolver memberNameResolver;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final TimeEntryValidationService timeEntryValidationService;
  private final RateSnapshotService rateSnapshotService;
  private final io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository invoiceRepository;

  public TimeEntryService(
      TimeEntryRepository timeEntryRepository,
      TaskRepository taskRepository,
      ProjectAccessService projectAccessService,
      AuditService auditService,
      BudgetCheckService budgetCheckService,
      MemberNameResolver memberNameResolver,
      ApplicationEventPublisher applicationEventPublisher,
      TimeEntryValidationService timeEntryValidationService,
      RateSnapshotService rateSnapshotService,
      io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository invoiceRepository) {
    this.timeEntryRepository = timeEntryRepository;
    this.taskRepository = taskRepository;
    this.projectAccessService = projectAccessService;
    this.auditService = auditService;
    this.budgetCheckService = budgetCheckService;
    this.memberNameResolver = memberNameResolver;
    this.invoiceRepository = invoiceRepository;
    this.applicationEventPublisher = applicationEventPublisher;
    this.timeEntryValidationService = timeEntryValidationService;
    this.rateSnapshotService = rateSnapshotService;
  }

  @Transactional
  public CreateTimeEntryResult createTimeEntry(
      UUID taskId,
      LocalDate date,
      int durationMinutes,
      boolean billable,
      Integer rateCents,
      String description,
      ActorContext actor) {
    var task =
        taskRepository
            .findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

    projectAccessService.requireViewAccess(task.getProjectId(), actor);
    timeEntryValidationService.validateProjectAndCustomer(task.getProjectId());

    if (durationMinutes <= 0) {
      throw new InvalidStateException(
          "Invalid duration", "Duration must be greater than 0 minutes");
    }

    if (rateCents != null && rateCents < 0) {
      throw new InvalidStateException("Invalid rate", "Rate cents must be non-negative");
    }

    var entry =
        new TimeEntry(
            taskId, actor.memberId(), date, durationMinutes, billable, rateCents, description);

    String rateWarning =
        rateSnapshotService.snapshotRates(
            entry, task.getProjectId(), actor.memberId(), date, billable);

    var saved = timeEntryRepository.save(entry);
    log.info(
        "Created time entry {} for task {} by member {}", saved.getId(), taskId, actor.memberId());

    var auditDetails = new LinkedHashMap<String, Object>();
    auditDetails.put("task_id", taskId.toString());
    auditDetails.put("title", task.getTitle());
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

    // Check budget thresholds after time entry creation
    var actorName = memberNameResolver.resolveName(actor.memberId());
    var tenantId = RequestScopes.getTenantIdOrNull();
    var orgId = RequestScopes.getOrgIdOrNull();
    budgetCheckService.checkAndAlert(
        task.getProjectId(), actor.memberId(), actorName, tenantId, orgId);

    publishTimeEntryChangedEvent(saved.getId(), task.getProjectId(), "CREATED");

    return new CreateTimeEntryResult(saved, rateWarning);
  }

  @Transactional(readOnly = true)
  public List<TimeEntry> listTimeEntriesByTask(
      UUID taskId, ActorContext actor, Boolean billable, BillingStatus billingStatus) {
    var task =
        taskRepository
            .findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

    projectAccessService.requireViewAccess(task.getProjectId(), actor);

    if (billingStatus != null) {
      return timeEntryRepository.findByTaskIdAndBillingStatus(taskId, billingStatus.name());
    }
    if (billable != null) {
      return timeEntryRepository.findByTaskIdAndBillable(taskId, billable);
    }
    return timeEntryRepository.findByTaskId(taskId);
  }

  @Transactional
  public TimeEntry toggleBillable(
      UUID projectId, UUID timeEntryId, boolean billable, ActorContext actor) {
    var entry =
        timeEntryRepository
            .findById(timeEntryId)
            .orElseThrow(() -> new ResourceNotFoundException("TimeEntry", timeEntryId));

    // Verify the time entry belongs to the specified project (defense in depth)
    var task =
        taskRepository
            .findById(entry.getTaskId())
            .orElseThrow(() -> new ResourceNotFoundException("Task", entry.getTaskId()));
    if (!task.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("TimeEntry", timeEntryId);
    }

    // Block toggle when time entry is part of an invoice (Epic 86A review)
    if (entry.getInvoiceId() != null) {
      throw new ResourceConflictException(
          "Time entry is billed", "Time entry is part of an invoice. Void the invoice to unlock.");
    }

    requireEditPermission(entry, actor);

    boolean oldBillable = entry.isBillable();
    entry.setBillable(billable);
    entry.setUpdatedAt(Instant.now());

    var saved = timeEntryRepository.save(entry);
    log.info(
        "Toggled billable to {} on time entry {} by member {}",
        billable,
        timeEntryId,
        actor.memberId());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("time_entry.updated")
            .entityType("time_entry")
            .entityId(saved.getId())
            .details(Map.of("billable", Map.of("from", oldBillable, "to", billable)))
            .build());

    // Check budget thresholds after billable toggle (affects budget consumption)
    var actorName = memberNameResolver.resolveName(actor.memberId());
    var tenantId = RequestScopes.getTenantIdOrNull();
    var orgId = RequestScopes.getOrgIdOrNull();
    budgetCheckService.checkAndAlert(
        task.getProjectId(), actor.memberId(), actorName, tenantId, orgId);

    publishTimeEntryChangedEvent(saved.getId(), task.getProjectId(), "UPDATED");

    return saved;
  }

  @Transactional
  public TimeEntry updateTimeEntry(
      UUID timeEntryId,
      LocalDate date,
      Integer durationMinutes,
      Boolean billable,
      Integer rateCents,
      String description,
      ActorContext actor) {
    var entry =
        timeEntryRepository
            .findById(timeEntryId)
            .orElseThrow(() -> new ResourceNotFoundException("TimeEntry", timeEntryId));

    // Block edit when time entry is part of an invoice (Epic 82B)
    if (entry.getInvoiceId() != null) {
      throw new ResourceConflictException(
          "Time entry is billed", "Time entry is part of an invoice. Void the invoice to unlock.");
    }

    requireEditPermission(entry, actor);

    UUID entryTaskId = entry.getTaskId();
    var task =
        taskRepository
            .findById(entryTaskId)
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
      rateSnapshotService.reSnapshotOnDateChange(entry, task.getProjectId(), entry.getDate());
    }

    entry.setUpdatedAt(Instant.now());

    entry = timeEntryRepository.save(entry);
    log.info("Updated time entry {} by member {}", timeEntryId, actor.memberId());

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

    details.put("title", task.getTitle());
    details.put("project_id", task.getProjectId().toString());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("time_entry.updated")
            .entityType("time_entry")
            .entityId(entry.getId())
            .details(details)
            .build());

    publishTimeEntryChangedEvent(entry.getId(), task.getProjectId(), "UPDATED");

    // Check budget thresholds if duration, date, or billable changed (affects budget consumption)
    boolean durationChanged = durationMinutes != null && oldDurationMinutes != durationMinutes;
    boolean billableChanged = billable != null && !billable.equals(oldBillable);
    if (dateChanged || durationChanged || billableChanged) {
      var actorName = memberNameResolver.resolveName(actor.memberId());
      var tenantId = RequestScopes.getTenantIdOrNull();
      var orgId = RequestScopes.getOrgIdOrNull();
      budgetCheckService.checkAndAlert(
          task.getProjectId(), actor.memberId(), actorName, tenantId, orgId);
    }

    return entry;
  }

  @Transactional
  public void deleteTimeEntry(UUID timeEntryId, ActorContext actor) {
    var entry =
        timeEntryRepository
            .findById(timeEntryId)
            .orElseThrow(() -> new ResourceNotFoundException("TimeEntry", timeEntryId));

    // Block delete when time entry is part of an invoice (Epic 82B)
    if (entry.getInvoiceId() != null) {
      throw new ResourceConflictException(
          "Time entry is billed", "Time entry is part of an invoice. Void the invoice to unlock.");
    }

    requireEditPermission(entry, actor);

    var task =
        taskRepository
            .findById(entry.getTaskId())
            .orElseThrow(() -> new ResourceNotFoundException("Task", entry.getTaskId()));

    timeEntryRepository.delete(entry);
    log.info("Deleted time entry {} by member {}", timeEntryId, actor.memberId());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("time_entry.deleted")
            .entityType("time_entry")
            .entityId(entry.getId())
            .details(
                Map.of(
                    "task_id", entry.getTaskId().toString(),
                    "title", task.getTitle(),
                    "project_id", task.getProjectId().toString()))
            .build());

    publishTimeEntryChangedEvent(entry.getId(), task.getProjectId(), "DELETED");
  }

  // --- Project time summary aggregation methods (Epic 46A) ---

  @Transactional(readOnly = true)
  public ProjectTimeSummaryProjection getProjectTimeSummary(
      UUID projectId, ActorContext actor, LocalDate from, LocalDate to) {
    projectAccessService.requireViewAccess(projectId, actor);
    return timeEntryRepository.projectTimeSummary(projectId, from, to);
  }

  @Transactional(readOnly = true)
  public List<MemberTimeSummaryProjection> getProjectTimeSummaryByMember(
      UUID projectId, ActorContext actor, LocalDate from, LocalDate to) {
    var access = projectAccessService.requireViewAccess(projectId, actor);
    if (!access.canEdit()) {
      throw new ForbiddenException(
          "Cannot view member breakdown",
          "Per-member time breakdown is restricted to project leads, org admins, and org owners");
    }
    return timeEntryRepository.projectTimeSummaryByMember(projectId, from, to);
  }

  @Transactional(readOnly = true)
  public List<TaskTimeSummaryProjection> getProjectTimeSummaryByTask(
      UUID projectId, ActorContext actor, LocalDate from, LocalDate to) {
    projectAccessService.requireViewAccess(projectId, actor);
    return timeEntryRepository.projectTimeSummaryByTask(projectId, from, to);
  }

  @Transactional
  public ReSnapshotResult reSnapshotRates(
      UUID projectId, UUID memberId, LocalDate fromDate, LocalDate toDate) {
    var entries = timeEntryRepository.findByFilters(projectId, memberId, fromDate, toDate);

    int processed = 0;
    int updated = 0;
    int skipped = 0;

    for (var entry : entries) {
      processed++;

      // Look up the task to get projectId for billing rate resolution
      var task = taskRepository.findById(entry.getTaskId()).orElse(null);
      if (task == null) {
        log.warn(
            "Skipping re-snapshot for time entry {}: task {} not found",
            entry.getId(),
            entry.getTaskId());
        skipped++;
        continue;
      }

      var rates =
          rateSnapshotService.resolveRates(
              entry.getMemberId(), task.getProjectId(), entry.getDate());

      boolean billingChanged =
          !bigDecimalEquals(entry.getBillingRateSnapshot(), rates.billingRate())
              || !Objects.equals(entry.getBillingRateCurrency(), rates.billingCurrency());
      boolean costChanged =
          !bigDecimalEquals(entry.getCostRateSnapshot(), rates.costRate())
              || !Objects.equals(entry.getCostRateCurrency(), rates.costCurrency());

      if (billingChanged || costChanged) {
        entry.snapshotBillingRate(rates.billingRate(), rates.billingCurrency());
        entry.snapshotCostRate(rates.costRate(), rates.costCurrency());
        entry.setUpdatedAt(Instant.now());
        timeEntryRepository.save(entry);
        updated++;
      } else {
        skipped++;
      }
    }

    log.info(
        "Re-snapshot completed: processed={}, updated={}, skipped={}", processed, updated, skipped);

    if (!entries.isEmpty()) {
      auditService.log(
          AuditEventBuilder.builder()
              .eventType("time_entry.rate_re_snapshot")
              .entityType("time_entry")
              .entityId(entries.getFirst().getId())
              .details(
                  Map.of(
                      "project_id", projectId != null ? projectId.toString() : "all",
                      "member_id", memberId != null ? memberId.toString() : "all",
                      "from_date", fromDate != null ? fromDate.toString() : "all",
                      "to_date", toDate != null ? toDate.toString() : "all",
                      "entries_processed", processed,
                      "entries_updated", updated,
                      "entries_skipped", skipped))
              .build());
    }

    return new ReSnapshotResult(processed, updated, skipped);
  }

  public record CreateTimeEntryResult(TimeEntry entry, String rateWarning) {}

  public record ReSnapshotResult(int entriesProcessed, int entriesUpdated, int entriesSkipped) {}

  private static boolean bigDecimalEquals(BigDecimal a, BigDecimal b) {
    if (a == null && b == null) return true;
    if (a == null || b == null) return false;
    return a.compareTo(b) == 0;
  }

  private void publishTimeEntryChangedEvent(UUID timeEntryId, UUID projectId, String action) {
    var memberId = RequestScopes.MEMBER_ID.isBound() ? RequestScopes.MEMBER_ID.get() : null;
    var tenantId = RequestScopes.getTenantIdOrNull();
    var orgId = RequestScopes.getOrgIdOrNull();
    applicationEventPublisher.publishEvent(
        new TimeEntryChangedEvent(
            "time_entry.changed",
            "time_entry",
            timeEntryId,
            projectId,
            action,
            memberId,
            null,
            tenantId,
            orgId,
            Instant.now(),
            Map.of("project_id", projectId.toString())));
  }

  /**
   * Checks that the caller has permission to edit/delete the given time entry. The creator can
   * always modify their own entries. Otherwise, the caller must have canEdit() access on the
   * entry's project (project lead, org admin, or org owner).
   */
  private void requireEditPermission(TimeEntry entry, ActorContext actor) {
    if (entry.getMemberId().equals(actor.memberId())) {
      return; // creator can always modify own entries
    }

    var task =
        taskRepository
            .findById(entry.getTaskId())
            .orElseThrow(() -> new ResourceNotFoundException("Task", entry.getTaskId()));

    var access = projectAccessService.checkAccess(task.getProjectId(), actor);
    if (!access.canEdit()) {
      throw new ForbiddenException(
          "Cannot modify time entry",
          "Only the creator or a project lead/admin/owner can modify this time entry");
    }
  }

  // --- Name Resolution (moved from controller for BE-007) ---

  /** Batch-loads member names for all member IDs referenced by the given time entries. */
  public Map<UUID, String> resolveNames(java.util.List<TimeEntry> entries) {
    var ids =
        entries.stream()
            .map(TimeEntry::getMemberId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
    return memberNameResolver.resolveNames(ids);
  }

  /**
   * Batch-loads invoice numbers for all invoice IDs referenced by the given time entries. Returns a
   * map of invoice UUID to human-readable invoice number. Drafts without an assigned number are
   * represented as "Draft".
   */
  public Map<UUID, String> resolveInvoiceNumbers(java.util.List<TimeEntry> entries) {
    var invoiceIds =
        entries.stream()
            .map(TimeEntry::getInvoiceId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();

    if (invoiceIds.isEmpty()) {
      return Map.of();
    }

    return invoiceRepository.findAllById(invoiceIds).stream()
        .collect(
            java.util.stream.Collectors.toMap(
                io.b2mash.b2b.b2bstrawman.invoice.Invoice::getId,
                inv -> inv.getInvoiceNumber() != null ? inv.getInvoiceNumber() : "Draft",
                (a, b) -> a));
  }
}
