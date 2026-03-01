package io.b2mash.b2b.b2bstrawman.proposal;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Handles post-commit side effects for proposal acceptance and orchestration failure events.
 * Notifications and portal sync run after the main transaction commits (or rolls back for
 * failures).
 */
@Component
public class ProposalAcceptedEventHandler {

  private static final Logger log = LoggerFactory.getLogger(ProposalAcceptedEventHandler.class);

  private final NotificationService notificationService;
  private final ProposalPortalSyncService proposalPortalSyncService;

  public ProposalAcceptedEventHandler(
      NotificationService notificationService,
      ProposalPortalSyncService proposalPortalSyncService) {
    this.notificationService = notificationService;
    this.proposalPortalSyncService = proposalPortalSyncService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onProposalAccepted(ProposalAcceptedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            // 1. Notify creator
            notificationService.createNotification(
                event.creatorMemberId(),
                "PROPOSAL_ACCEPTED",
                "Proposal %s accepted — project created".formatted(event.proposalNumber()),
                "Project \"%s\" has been created for customer %s"
                    .formatted(event.projectName(), event.customerName()),
                "PROPOSAL",
                event.proposalId(),
                event.createdProjectId());

            // 2. Notify team members
            for (var memberId : event.teamMemberIds()) {
              notificationService.createNotification(
                  memberId,
                  "PROJECT_ASSIGNED",
                  "You've been assigned to project %s".formatted(event.projectName()),
                  "You have been assigned to project \"%s\" for customer %s"
                      .formatted(event.projectName(), event.customerName()),
                  "PROJECT",
                  event.createdProjectId(),
                  event.createdProjectId());
            }

            // 3. Update portal status
            proposalPortalSyncService.updatePortalProposalStatus(event.proposalId(), "ACCEPTED");

            log.info(
                "Post-commit actions completed for accepted proposal {}", event.proposalNumber());
          } catch (Exception e) {
            log.error(
                "Failed to process post-commit actions for proposal {}", event.proposalId(), e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
  public void onProposalOrchestrationFailed(ProposalOrchestrationFailedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            notificationService.createNotification(
                event.creatorMemberId(),
                "PROPOSAL_ORCHESTRATION_FAILED",
                "Proposal %s acceptance failed".formatted(event.proposalNumber()),
                "An error occurred while processing proposal acceptance: %s"
                    .formatted(event.errorMessage()),
                "PROPOSAL",
                event.proposalId(),
                null);

            log.info("Failure notification sent for proposal {}", event.proposalNumber());
          } catch (Exception e) {
            log.error("Failed to send failure notification for proposal {}", event.proposalId(), e);
          }
        });
  }

  private void handleInTenantScope(String tenantId, String orgId, Runnable action) {
    if (tenantId != null) {
      var carrier = ScopedValue.where(RequestScopes.TENANT_ID, tenantId);
      if (orgId != null) {
        carrier = carrier.where(RequestScopes.ORG_ID, orgId);
      }
      carrier.run(action);
    } else {
      action.run();
    }
  }
}
