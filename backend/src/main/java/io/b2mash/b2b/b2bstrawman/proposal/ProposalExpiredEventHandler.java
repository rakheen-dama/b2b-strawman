package io.b2mash.b2b.b2bstrawman.proposal;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Handles post-commit side effects for proposal expiry events. Sends in-app notification to the
 * proposal creator, emails the portal contact, and syncs status to the portal read-model.
 */
@Component
public class ProposalExpiredEventHandler {

  private static final Logger log = LoggerFactory.getLogger(ProposalExpiredEventHandler.class);

  private final NotificationService notificationService;
  private final ProposalPortalSyncService proposalPortalSyncService;

  public ProposalExpiredEventHandler(
      NotificationService notificationService,
      ProposalPortalSyncService proposalPortalSyncService) {
    this.notificationService = notificationService;
    this.proposalPortalSyncService = proposalPortalSyncService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onProposalExpired(ProposalExpiredEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            // 1. In-app notification to creator
            notificationService.createNotification(
                event.createdByMemberId(),
                "PROPOSAL_EXPIRED",
                "Proposal %s has expired".formatted(event.proposalNumber()),
                "Your proposal %s for %s has expired"
                    .formatted(event.proposalNumber(), event.customerName()),
                "PROPOSAL",
                event.proposalId(),
                null);

            // 2. Portal sync: update status to EXPIRED
            proposalPortalSyncService.updatePortalProposalStatus(event.proposalId(), "EXPIRED");

            log.info(
                "Post-commit actions completed for expired proposal {}", event.proposalNumber());
          } catch (Exception e) {
            log.error(
                "Failed to process post-commit actions for expired proposal {}",
                event.proposalId(),
                e);
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
