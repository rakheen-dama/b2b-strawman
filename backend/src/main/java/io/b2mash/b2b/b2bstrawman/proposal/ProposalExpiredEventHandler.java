package io.b2mash.b2b.b2bstrawman.proposal;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailContextBuilder;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.portal.PortalEmailService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Handles post-commit side effects for proposal expiry events. Three independent steps run
 * sequentially with isolated try/catch — a failure in one does not skip the others:
 *
 * <ol>
 *   <li>In-app notification to the firm-side creator.
 *   <li>Portal read-model sync: status → EXPIRED.
 *   <li>Portal email to the customer contact (OBS-AUDIT-N1, mirrors OBS-703 / OBS-2106).
 * </ol>
 *
 * <p>Honours ADR-258: portal email is sent here rather than via {@code
 * PortalEmailNotificationChannel}, which intentionally excludes proposal events.
 */
@Component
public class ProposalExpiredEventHandler {

  private static final Logger log = LoggerFactory.getLogger(ProposalExpiredEventHandler.class);

  private final NotificationService notificationService;
  private final ProposalPortalSyncService proposalPortalSyncService;
  private final PortalContactRepository portalContactRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final PortalEmailService portalEmailService;
  private final EmailContextBuilder emailContextBuilder;
  private final String productName;

  public ProposalExpiredEventHandler(
      NotificationService notificationService,
      ProposalPortalSyncService proposalPortalSyncService,
      PortalContactRepository portalContactRepository,
      OrgSettingsRepository orgSettingsRepository,
      PortalEmailService portalEmailService,
      EmailContextBuilder emailContextBuilder,
      @Value("${docteams.app.product-name:Kazi}") String productName) {
    this.notificationService = notificationService;
    this.proposalPortalSyncService = proposalPortalSyncService;
    this.portalContactRepository = portalContactRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.portalEmailService = portalEmailService;
    this.emailContextBuilder = emailContextBuilder;
    this.productName = productName;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onProposalExpired(ProposalExpiredEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          sendCreatorNotification(event);
          syncPortalStatus(event);
          sendPortalExpiredEmail(event);
        });
  }

  private void sendCreatorNotification(ProposalExpiredEvent event) {
    try {
      notificationService.createNotification(
          event.createdByMemberId(),
          "PROPOSAL_EXPIRED",
          "Proposal %s has expired".formatted(event.proposalNumber()),
          "Your proposal %s for %s has expired"
              .formatted(event.proposalNumber(), event.customerName()),
          "PROPOSAL",
          event.proposalId(),
          null);
    } catch (Exception e) {
      log.error(
          "Failed to create in-app notification for expired proposal {}", event.proposalId(), e);
    }
  }

  private void syncPortalStatus(ProposalExpiredEvent event) {
    try {
      proposalPortalSyncService.updatePortalProposalStatus(event.proposalId(), "EXPIRED");
    } catch (Exception e) {
      log.error("Failed to sync portal status for expired proposal {}", event.proposalId(), e);
    }
  }

  private void sendPortalExpiredEmail(ProposalExpiredEvent event) {
    try {
      if (event.portalContactEmail() == null) {
        log.debug(
            "Skipping portal-proposal-expired email — proposal {} has no portal contact",
            event.proposalId());
        return;
      }
      var contact =
          portalContactRepository
              .findByEmailAndOrgId(event.portalContactEmail(), event.orgId())
              .orElse(null);
      if (contact == null) {
        log.warn(
            "Skipping portal-proposal-expired email — no PortalContact for email={} org={}",
            event.portalContactEmail(),
            event.orgId());
        return;
      }

      String orgName = event.orgName() != null ? event.orgName() : productName;

      Map<String, Object> context =
          new HashMap<>(emailContextBuilder.buildBaseContext(contact.getDisplayName(), null));
      context.put("contactName", contact.getDisplayName());
      context.put("orgName", orgName);
      context.put("proposalNumber", event.proposalNumber());
      orgSettingsRepository
          .findForCurrentTenant()
          .map(s -> s.getBrandColor())
          .ifPresent(c -> context.put("brandColor", c));
      context.put(
          "subject", "%s: Proposal %s has expired".formatted(orgName, event.proposalNumber()));

      portalEmailService.sendProposalExpiredEmail(contact, context);
    } catch (Exception e) {
      log.error(
          "Failed to send portal-proposal-expired email for proposal {}", event.proposalId(), e);
    }
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
