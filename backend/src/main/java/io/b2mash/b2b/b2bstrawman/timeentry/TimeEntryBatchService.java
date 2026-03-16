package io.b2mash.b2b.b2bstrawman.timeentry;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.budget.BudgetCheckService;
import io.b2mash.b2b.b2bstrawman.event.TimeEntryChangedEvent;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TimeEntryBatchService {

  private static final Logger log = LoggerFactory.getLogger(TimeEntryBatchService.class);

  /** Maximum batch size — matches @Size(max=50) on BatchTimeEntryRequest.entries. */
  static final int MAX_BATCH_SIZE = 50;

  private final TaskRepository taskRepository;
  private final ProjectAccessService projectAccessService;
  private final TimeEntryValidationService timeEntryValidationService;
  private final RateSnapshotService rateSnapshotService;
  private final TimeEntryRepository timeEntryRepository;
  private final BudgetCheckService budgetCheckService;
  private final MemberNameResolver memberNameResolver;
  private final AuditService auditService;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final TransactionTemplate transactionTemplate;

  public TimeEntryBatchService(
      TaskRepository taskRepository,
      ProjectAccessService projectAccessService,
      TimeEntryValidationService timeEntryValidationService,
      RateSnapshotService rateSnapshotService,
      TimeEntryRepository timeEntryRepository,
      BudgetCheckService budgetCheckService,
      MemberNameResolver memberNameResolver,
      AuditService auditService,
      ApplicationEventPublisher applicationEventPublisher,
      TransactionTemplate transactionTemplate) {
    this.taskRepository = taskRepository;
    this.projectAccessService = projectAccessService;
    this.timeEntryValidationService = timeEntryValidationService;
    this.rateSnapshotService = rateSnapshotService;
    this.timeEntryRepository = timeEntryRepository;
    this.budgetCheckService = budgetCheckService;
    this.memberNameResolver = memberNameResolver;
    this.auditService = auditService;
    this.applicationEventPublisher = applicationEventPublisher;
    this.transactionTemplate = transactionTemplate;
  }

  public TimeEntryController.BatchTimeEntryResult createBatch(
      TimeEntryController.BatchTimeEntryRequest request, ActorContext actor) {

    var created = new ArrayList<TimeEntryController.CreatedEntry>();
    var errors = new ArrayList<TimeEntryController.EntryError>();

    var actorName = memberNameResolver.resolveName(actor.memberId());
    var tenantId = RequestScopes.getTenantIdOrNull();
    var orgId = RequestScopes.getOrgIdOrNull();

    var entries = request.entries();
    for (int i = 0; i < entries.size(); i++) {
      var item = entries.get(i);
      final int index = i;
      try {
        var result =
            transactionTemplate.execute(
                status -> {
                  var task =
                      taskRepository
                          .findById(item.taskId())
                          .orElseThrow(() -> new ResourceNotFoundException("Task", item.taskId()));

                  projectAccessService.requireViewAccess(task.getProjectId(), actor);
                  timeEntryValidationService.validateProjectAndCustomer(task.getProjectId());

                  if (item.durationMinutes() <= 0) {
                    throw new InvalidStateException(
                        "Invalid duration", "Duration must be greater than 0 minutes");
                  }

                  var entry =
                      new TimeEntry(
                          item.taskId(),
                          actor.memberId(),
                          item.date(),
                          item.durationMinutes(),
                          item.billable(),
                          null,
                          item.description());

                  rateSnapshotService.snapshotRates(
                      entry, task.getProjectId(), actor.memberId(), item.date(), item.billable());

                  var saved = timeEntryRepository.save(entry);
                  log.info(
                      "Batch created time entry {} for task {} by member {}",
                      saved.getId(),
                      item.taskId(),
                      actor.memberId());

                  // Audit logging (matches TimeEntryService.createTimeEntry pattern)
                  var auditDetails = new LinkedHashMap<String, Object>();
                  auditDetails.put("task_id", item.taskId().toString());
                  auditDetails.put("duration_minutes", item.durationMinutes());
                  auditDetails.put("billable", item.billable());
                  auditDetails.put("project_id", task.getProjectId().toString());
                  auditDetails.put("batch", true);
                  if (saved.getBillingRateSnapshot() != null) {
                    auditDetails.put(
                        "billing_rate_snapshot", saved.getBillingRateSnapshot().toString());
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

                  // Budget check within the same transaction
                  budgetCheckService.checkAndAlert(
                      task.getProjectId(), actor.memberId(), actorName, tenantId, orgId);

                  // Publish domain event (matches TimeEntryService.createTimeEntry pattern)
                  publishTimeEntryChangedEvent(
                      saved.getId(), task.getProjectId(), "CREATED", tenantId, orgId);

                  return new TimeEntryController.CreatedEntry(
                      saved.getId(), saved.getTaskId(), saved.getDate());
                });

        created.add(result);

      } catch (ResourceNotFoundException e) {
        log.debug("Batch entry {} failed: {}", index, e.getBody().getDetail());
        errors.add(
            new TimeEntryController.EntryError(index, item.taskId(), e.getBody().getDetail()));
      } catch (InvalidStateException e) {
        log.debug("Batch entry {} failed: {}", index, e.getBody().getDetail());
        errors.add(
            new TimeEntryController.EntryError(index, item.taskId(), e.getBody().getDetail()));
      } catch (ForbiddenException e) {
        log.debug("Batch entry {} failed: {}", index, e.getBody().getDetail());
        errors.add(
            new TimeEntryController.EntryError(index, item.taskId(), e.getBody().getDetail()));
      } catch (IllegalArgumentException e) {
        log.debug("Batch entry {} failed: {}", index, e.getMessage());
        errors.add(new TimeEntryController.EntryError(index, item.taskId(), e.getMessage()));
      } catch (Exception e) {
        log.warn("Batch entry {} failed unexpectedly: {}", index, e.getMessage(), e);
        errors.add(
            new TimeEntryController.EntryError(
                index, item.taskId(), "An unexpected error occurred"));
      }
    }

    return new TimeEntryController.BatchTimeEntryResult(
        List.copyOf(created), List.copyOf(errors), created.size(), errors.size());
  }

  private void publishTimeEntryChangedEvent(
      UUID timeEntryId, UUID projectId, String action, String tenantId, String orgId) {
    var memberId = RequestScopes.MEMBER_ID.isBound() ? RequestScopes.MEMBER_ID.get() : null;
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
}
