package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Per-tenant portal task backfill (Phase 89 633A, §14.3.13, R7.2, G88-F19): projects every task of
 * every client-linked matter into {@code portal.portal_tasks} when it is not there yet. Tenant
 * reads run inside the tenant's scope; the portal writes run in one portal transaction afterwards
 * (members and tasks live in the tenant schema, which the portal transaction cannot reach — the
 * {@code PortalResyncService} shape).
 */
@Service
public class PortalTaskBackfillService {

  private final CustomerProjectRepository customerProjectRepository;
  private final TaskRepository taskRepository;
  private final MemberNameResolver memberNameResolver;
  private final PortalReadModelRepository readModelRepo;
  private final TransactionTemplate portalTx;

  public PortalTaskBackfillService(
      CustomerProjectRepository customerProjectRepository,
      TaskRepository taskRepository,
      MemberNameResolver memberNameResolver,
      PortalReadModelRepository readModelRepo,
      @Qualifier("portalTransactionManager") PlatformTransactionManager portalTxManager) {
    this.customerProjectRepository = customerProjectRepository;
    this.taskRepository = taskRepository;
    this.memberNameResolver = memberNameResolver;
    this.readModelRepo = readModelRepo;
    this.portalTx = new TransactionTemplate(portalTxManager);
  }

  /**
   * Inserts the portal rows missing for one tenant and returns how many were inserted (rows the
   * live listener already wrote are left untouched and not counted; rows removed by {@link
   * #reconcileDeleted} are not counted either).
   */
  public int backfillTenant(String orgId, String tenantSchema, @Nullable String shardId) {
    List<TaskRow> rows =
        RequestScopes.callForTenantOnShard(tenantSchema, orgId, shardId, this::collectLinkedTasks);
    List<UUID> insertedIds =
        portalTx.execute(
            status -> {
              List<UUID> ids = new ArrayList<>();
              for (var r : rows) {
                // Insert-if-absent: never overwrite a row the live listener wrote. Task has no
                // sort order, so backfilled rows take 0 (R7.2).
                int n =
                    readModelRepo.insertPortalTaskIfAbsent(
                        r.taskId(),
                        orgId,
                        r.matterId(),
                        r.title(),
                        r.status(),
                        r.assigneeName(),
                        0);
                if (n > 0) {
                  ids.add(r.taskId());
                }
              }
              return ids;
            });
    if (insertedIds == null || insertedIds.isEmpty()) {
      return 0;
    }
    return insertedIds.size() - reconcileDeleted(orgId, tenantSchema, shardId, insertedIds);
  }

  /**
   * Removes the portal rows this run inserted for tasks deleted since they were read, and returns
   * how many it removed. A task delete that commits before this re-read is caught here; one that
   * commits after it has its own {@code PortalTaskDeletedEvent} AFTER_COMMIT listener remove the
   * row (our insert is already committed and visible by then), so the read-then-write window cannot
   * leave a stale row behind. Only ids this run inserted are considered — pre-existing rows were
   * written by the live listener, not by us.
   */
  int reconcileDeleted(
      String orgId, String tenantSchema, @Nullable String shardId, List<UUID> insertedIds) {
    if (insertedIds.isEmpty()) {
      return 0;
    }
    Set<UUID> stillPresent =
        RequestScopes.callForTenantOnShard(
            tenantSchema,
            orgId,
            shardId,
            () ->
                taskRepository.findAllById(insertedIds).stream()
                    .map(Task::getId)
                    .collect(Collectors.toSet()));
    List<UUID> gone = insertedIds.stream().filter(id -> !stillPresent.contains(id)).toList();
    if (!gone.isEmpty()) {
      portalTx.executeWithoutResult(
          status -> gone.forEach(id -> readModelRepo.deletePortalTask(id, orgId)));
    }
    return gone.size();
  }

  private List<TaskRow> collectLinkedTasks() {
    // A matter linked to two clients is visited once.
    List<UUID> projectIds =
        customerProjectRepository.findAll().stream()
            .map(CustomerProject::getProjectId)
            .distinct()
            .toList();
    Map<UUID, String> assigneeNames = new HashMap<>();
    List<TaskRow> out = new ArrayList<>();
    for (UUID projectId : projectIds) {
      for (var task : taskRepository.findByProjectId(projectId)) {
        UUID assigneeId = task.getAssigneeId();
        // Never resolve a null assignee (findById(null) throws).
        String assigneeName =
            assigneeId != null
                ? assigneeNames.computeIfAbsent(assigneeId, memberNameResolver::resolveNameOrNull)
                : null;
        out.add(
            new TaskRow(
                task.getId(), projectId, task.getTitle(), task.getStatus().name(), assigneeName));
      }
    }
    return out;
  }

  private record TaskRow(
      UUID taskId, UUID matterId, String title, String status, String assigneeName) {}
}
