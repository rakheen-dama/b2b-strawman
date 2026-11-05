package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
   * live listener already wrote are left untouched and not counted).
   */
  public int backfillTenant(String orgId, String tenantSchema, @Nullable String shardId) {
    List<TaskRow> rows =
        RequestScopes.callForTenantOnShard(tenantSchema, orgId, shardId, this::collectLinkedTasks);
    Integer inserted =
        portalTx.execute(
            status -> {
              int n = 0;
              for (var r : rows) {
                // Insert-if-absent: never overwrite a row the live listener wrote. Task has no
                // sort order, so backfilled rows take 0 (R7.2).
                n +=
                    readModelRepo.insertPortalTaskIfAbsent(
                        r.taskId(),
                        orgId,
                        r.projectId(),
                        r.title(),
                        r.status(),
                        r.assigneeName(),
                        0);
              }
              return n;
            });
    return inserted != null ? inserted : 0;
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
      UUID taskId, UUID projectId, String title, String status, String assigneeName) {}
}
