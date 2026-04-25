package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.event;

import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Fans out in-app notifications to org owners and admins when a matter is closed or reopened (Phase
 * 67, Epic 489B, task 489.14) AND keeps the portal {@code portal_projects.status} projection in
 * sync with the matter's lifecycle (GAP-L-73).
 *
 * <p>Uses {@link TransactionalEventListener} with {@link TransactionPhase#AFTER_COMMIT} — the event
 * is published inside {@code MatterClosureService}'s transaction, but the listener must only fire
 * after that transaction successfully commits so we never deliver "Matter closed" to recipients
 * when the close itself rolled back. {@code NotificationService.notifyAdminsAndOwners} runs in
 * {@code REQUIRES_NEW}, so notification failures cannot affect the (already committed) close. The
 * listener executes on the publisher thread, which retains the {@code TENANT_ID} {@code
 * ScopedValue} binding required by {@code NotificationService} for schema routing.
 *
 * <p>The portal-projection sync (GAP-L-73) mirrors the row-by-row pattern in {@code
 * PortalEventHandler.onProjectUpdated}: every {@code portal_projects} row that mirrors this project
 * (one per linked customer) gets its {@code status} column flipped. The sync is best-effort —
 * failures only affect the projection, not the close itself.
 */
@Component
public class MatterClosureNotificationHandler {

  private static final Logger log = LoggerFactory.getLogger(MatterClosureNotificationHandler.class);

  private final NotificationService notificationService;
  private final ProjectRepository projectRepository;
  private final PortalReadModelRepository portalReadModelRepository;

  public MatterClosureNotificationHandler(
      NotificationService notificationService,
      ProjectRepository projectRepository,
      PortalReadModelRepository portalReadModelRepository) {
    this.notificationService = notificationService;
    this.projectRepository = projectRepository;
    this.portalReadModelRepository = portalReadModelRepository;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onMatterClosed(MatterClosedEvent event) {
    try {
      String title = "Matter closed" + (event.override() ? " (override used)" : "");
      String body =
          "Matter has been closed (reason: " + event.reason() + "). Retention clock started.";
      notificationService.notifyAdminsAndOwners(
          "MATTER_CLOSED", title, body, "project", event.projectId());
    } catch (Exception e) {
      log.warn("Failed to send MATTER_CLOSED notifications for project={}", event.projectId(), e);
    }

    // GAP-L-73: keep portal projection in sync. The portal /projects/{id} matter detail badge
    // reads from portal_projects.status — that column is otherwise only updated on
    // ProjectUpdatedEvent (firm-side ProjectService.update), which closure does not emit.
    syncPortalProjectionStatus(event.projectId(), "CLOSED");
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onMatterReopened(MatterReopenedEvent event) {
    try {
      // NOTE: retention soft-cancel is not yet persisted (TODO 489C). The reopen clears the
      // project's closed_at, but the canonical retention anchor (retentionClockStartedAt) and
      // the derived retention window are preserved — see MatterClosureService.reopen and
      // RetentionElapsedException. Messaging intentionally omits any "soft-cancel" claim.
      notificationService.notifyAdminsAndOwners(
          "MATTER_REOPENED",
          "Matter reopened",
          "Matter reopened. Retention window unchanged.",
          "project",
          event.projectId());
    } catch (Exception e) {
      log.warn("Failed to send MATTER_REOPENED notifications for project={}", event.projectId(), e);
    }

    // GAP-L-73: mirror the close path so a closed→reopened matter goes CLOSED → ACTIVE in the
    // portal projection too.
    syncPortalProjectionStatus(event.projectId(), "ACTIVE");
  }

  /**
   * Updates {@code portal.portal_projects.status} for every customer-linked row mirroring this
   * project. Mirrors {@link
   * io.b2mash.b2b.b2bstrawman.customerbackend.handler.PortalEventHandler#onProjectUpdated}'s
   * row-by-row pattern. Best-effort — projection failures must not surface to the caller (the
   * domain transaction has already committed by the time AFTER_COMMIT fires).
   *
   * <p>Resolves the project name + description by re-reading {@link ProjectRepository#findById}
   * because {@link MatterClosedEvent} / {@link MatterReopenedEvent} do not carry them. The {@code
   * TENANT_ID} ScopedValue is bound on the publisher thread (see class Javadoc) so the lookup hits
   * the correct dedicated schema. {@code RequestScopes.requireOrgId()} is the canonical org-id
   * accessor for AFTER_COMMIT side effects on non-{@code PortalDomainEvent} shapes.
   */
  private void syncPortalProjectionStatus(UUID projectId, String status) {
    try {
      var projectOpt = projectRepository.findById(projectId);
      if (projectOpt.isEmpty()) {
        log.warn(
            "Project {} not found when syncing portal projection status={}", projectId, status);
        return;
      }
      var project = projectOpt.get();
      String orgId = RequestScopes.getOrgIdOrNull();
      if (orgId == null) {
        log.warn(
            "ORG_ID ScopedValue not bound when syncing portal projection status for project={}; skipping",
            projectId);
        return;
      }
      var customerIds = portalReadModelRepository.findCustomerIdsByProjectId(projectId, orgId);
      for (var customerId : customerIds) {
        portalReadModelRepository.updatePortalProjectDetails(
            projectId, customerId, project.getName(), status, project.getDescription());
      }
    } catch (Exception e) {
      log.warn(
          "Failed to sync portal projection status for project={}, status={}",
          projectId,
          status,
          e);
    }
  }
}
