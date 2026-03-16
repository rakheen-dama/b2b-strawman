package io.b2mash.b2b.b2bstrawman.timeentry;

import io.b2mash.b2b.b2bstrawman.budget.BudgetCheckService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TimeEntryBatchService {

  private static final Logger log = LoggerFactory.getLogger(TimeEntryBatchService.class);
  private static final int MAX_BATCH_SIZE = 50;

  private final TaskRepository taskRepository;
  private final ProjectAccessService projectAccessService;
  private final TimeEntryValidationService timeEntryValidationService;
  private final RateSnapshotService rateSnapshotService;
  private final TimeEntryRepository timeEntryRepository;
  private final BudgetCheckService budgetCheckService;
  private final MemberNameResolver memberNameResolver;

  public TimeEntryBatchService(
      TaskRepository taskRepository,
      ProjectAccessService projectAccessService,
      TimeEntryValidationService timeEntryValidationService,
      RateSnapshotService rateSnapshotService,
      TimeEntryRepository timeEntryRepository,
      BudgetCheckService budgetCheckService,
      MemberNameResolver memberNameResolver) {
    this.taskRepository = taskRepository;
    this.projectAccessService = projectAccessService;
    this.timeEntryValidationService = timeEntryValidationService;
    this.rateSnapshotService = rateSnapshotService;
    this.timeEntryRepository = timeEntryRepository;
    this.budgetCheckService = budgetCheckService;
    this.memberNameResolver = memberNameResolver;
  }

  public TimeEntryController.BatchTimeEntryResult createBatch(
      TimeEntryController.BatchTimeEntryRequest request, ActorContext actor) {

    if (request.entries() == null || request.entries().size() > MAX_BATCH_SIZE) {
      throw new InvalidStateException(
          "Batch size exceeded",
          "Batch requests must contain between 1 and " + MAX_BATCH_SIZE + " entries");
    }

    var created = new ArrayList<TimeEntryController.CreatedEntry>();
    var errors = new ArrayList<TimeEntryController.EntryError>();

    var actorName = memberNameResolver.resolveName(actor.memberId());
    var tenantId = RequestScopes.getTenantIdOrNull();
    var orgId = RequestScopes.getOrgIdOrNull();

    var entries = request.entries();
    for (int i = 0; i < entries.size(); i++) {
      var item = entries.get(i);
      try {
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

        budgetCheckService.checkAndAlert(
            task.getProjectId(), actor.memberId(), actorName, tenantId, orgId);

        created.add(
            new TimeEntryController.CreatedEntry(
                saved.getId(), saved.getTaskId(), saved.getDate()));

      } catch (Exception e) {
        log.debug("Batch entry {} failed: {}", i, e.getMessage());
        errors.add(new TimeEntryController.EntryError(i, item.taskId(), e.getMessage()));
      }
    }

    return new TimeEntryController.BatchTimeEntryResult(
        List.copyOf(created), List.copyOf(errors), created.size(), errors.size());
  }
}
