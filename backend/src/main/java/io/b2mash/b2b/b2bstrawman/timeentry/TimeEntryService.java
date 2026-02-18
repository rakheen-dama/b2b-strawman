package io.b2mash.b2b.b2bstrawman.timeentry;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.billingrate.BillingRateService;
import io.b2mash.b2b.b2bstrawman.budget.BudgetCheckService;
import io.b2mash.b2b.b2bstrawman.compliance.CustomerLifecycleGuard;
import io.b2mash.b2b.b2bstrawman.compliance.LifecycleAction;
import io.b2mash.b2b.b2bstrawman.costrate.CostRateService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
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
  private final BudgetCheckService budgetCheckService;
  private final MemberRepository memberRepository;
  private final CustomerLifecycleGuard customerLifecycleGuard;
  private final CustomerProjectRepository customerProjectRepository;
  private final CustomerRepository customerRepository;

  public TimeEntryService(
      TimeEntryRepository timeEntryRepository,
      TaskRepository taskRepository,
      ProjectAccessService projectAccessService,
      AuditService auditService,
      BillingRateService billingRateService,
      CostRateService costRateService,
      BudgetCheckService budgetCheckService,
      MemberRepository memberRepository,
      CustomerLifecycleGuard customerLifecycleGuard,
      CustomerProjectRepository customerProjectRepository,
      CustomerRepository customerRepository) {
    this.timeEntryRepository = timeEntryRepository;
    this.taskRepository = taskRepository;
    this.projectAccessService = projectAccessService;
    this.auditService = auditService;
    this.billingRateService = billingRateService;
    this.costRateService = costRateService;
    this.budgetCheckService = budgetCheckService;
    this.memberRepository = memberRepository;
    this.customerLifecycleGuard = customerLifecycleGuard;
    this.customerProjectRepository = customerProjectRepository;
    this.customerRepository = customerRepository;
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
            .findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

    projectAccessService.requireViewAccess(task.getProjectId(), memberId, orgRole);

    // Check lifecycle guard if project is linked to a customer
    customerProjectRepository
        .findFirstCustomerByProjectId(task.getProjectId())
        .ifPresent(
            custId ->
                customerRepository
                    .findById(custId)
                    .ifPresent(
                        customer ->
                            customerLifecycleGuard.requireActionPermitted(
                                customer, LifecycleAction.CREATE_TIME_ENTRY)));

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

    // Check budget thresholds after time entry creation
    var actorName = memberRepository.findById(memberId).map(m -> m.getName()).orElse("Unknown");
    var tenantId = RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : null;
    var orgId = RequestScopes.ORG_ID.isBound() ? RequestScopes.ORG_ID.get() : null;
    budgetCheckService.checkAndAlert(task.getProjectId(), memberId, actorName, tenantId, orgId);

    return saved;
  }

  @Transactional(readOnly = true)
  public List<TimeEntry> listTimeEntriesByTask(
      UUID taskId, UUID memberId, String orgRole, Boolean billable, BillingStatus billingStatus) {
    var task =
        taskRepository
            .findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

    projectAccessService.requireViewAccess(task.getProjectId(), memberId, orgRole);

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
      UUID projectId, UUID timeEntryId, boolean billable, UUID memberId, String orgRole) {
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

    requireEditPermission(entry, memberId, orgRole);

    boolean oldBillable = entry.isBillable();
    entry.setBillable(billable);
    entry.setUpdatedAt(Instant.now());

    var saved = timeEntryRepository.save(entry);
    log.info(
        "Toggled billable to {} on time entry {} by member {}", billable, timeEntryId, memberId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("time_entry.updated")
            .entityType("time_entry")
            .entityId(saved.getId())
            .details(Map.of("billable", Map.of("from", oldBillable, "to", billable)))
            .build());

    // Check budget thresholds after billable toggle (affects budget consumption)
    var actorName = memberRepository.findById(memberId).map(m -> m.getName()).orElse("Unknown");
    var tenantId = RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : null;
    var orgId = RequestScopes.ORG_ID.isBound() ? RequestScopes.ORG_ID.get() : null;
    budgetCheckService.checkAndAlert(task.getProjectId(), memberId, actorName, tenantId, orgId);

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
      UUID memberId,
      String orgRole) {
    var entry =
        timeEntryRepository
            .findById(timeEntryId)
            .orElseThrow(() -> new ResourceNotFoundException("TimeEntry", timeEntryId));

    // Block edit when time entry is part of an invoice (Epic 82B)
    if (entry.getInvoiceId() != null) {
      throw new ResourceConflictException(
          "Time entry is billed", "Time entry is part of an invoice. Void the invoice to unlock.");
    }

    requireEditPermission(entry, memberId, orgRole);

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

    // Check budget thresholds if duration, date, or billable changed (affects budget consumption)
    boolean durationChanged = durationMinutes != null && oldDurationMinutes != durationMinutes;
    boolean billableChanged = billable != null && !billable.equals(oldBillable);
    if (dateChanged || durationChanged || billableChanged) {
      var actorName = memberRepository.findById(memberId).map(m -> m.getName()).orElse("Unknown");
      var tenantId = RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : null;
      var orgId = RequestScopes.ORG_ID.isBound() ? RequestScopes.ORG_ID.get() : null;
      budgetCheckService.checkAndAlert(task.getProjectId(), memberId, actorName, tenantId, orgId);
    }

    return entry;
  }

  @Transactional
  public void deleteTimeEntry(UUID timeEntryId, UUID memberId, String orgRole) {
    var entry =
        timeEntryRepository
            .findById(timeEntryId)
            .orElseThrow(() -> new ResourceNotFoundException("TimeEntry", timeEntryId));

    // Block delete when time entry is part of an invoice (Epic 82B)
    if (entry.getInvoiceId() != null) {
      throw new ResourceConflictException(
          "Time entry is billed", "Time entry is part of an invoice. Void the invoice to unlock.");
    }

    requireEditPermission(entry, memberId, orgRole);

    var task =
        taskRepository
            .findById(entry.getTaskId())
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

      var billingRate =
          billingRateService.resolveRate(entry.getMemberId(), task.getProjectId(), entry.getDate());
      var costRate = costRateService.resolveCostRate(entry.getMemberId(), entry.getDate());

      BigDecimal newBillingRate = billingRate.map(r -> r.hourlyRate()).orElse(null);
      String newBillingCurrency = billingRate.map(r -> r.currency()).orElse(null);
      BigDecimal newCostRate = costRate.map(r -> r.hourlyCost()).orElse(null);
      String newCostCurrency = costRate.map(r -> r.currency()).orElse(null);

      boolean billingChanged =
          !bigDecimalEquals(entry.getBillingRateSnapshot(), newBillingRate)
              || !Objects.equals(entry.getBillingRateCurrency(), newBillingCurrency);
      boolean costChanged =
          !bigDecimalEquals(entry.getCostRateSnapshot(), newCostRate)
              || !Objects.equals(entry.getCostRateCurrency(), newCostCurrency);

      if (billingChanged || costChanged) {
        entry.snapshotBillingRate(newBillingRate, newBillingCurrency);
        entry.snapshotCostRate(newCostRate, newCostCurrency);
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

  public record ReSnapshotResult(int entriesProcessed, int entriesUpdated, int entriesSkipped) {}

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
            .findById(entry.getTaskId())
            .orElseThrow(() -> new ResourceNotFoundException("Task", entry.getTaskId()));

    var access = projectAccessService.checkAccess(task.getProjectId(), memberId, orgRole);
    if (!access.canEdit()) {
      throw new ForbiddenException(
          "Cannot modify time entry",
          "Only the creator or a project lead/admin/owner can modify this time entry");
    }
  }
}
