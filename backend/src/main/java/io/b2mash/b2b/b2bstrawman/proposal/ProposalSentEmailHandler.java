package io.b2mash.b2b.b2bstrawman.proposal;

import io.b2mash.b2b.b2bstrawman.event.ProposalSentEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailContextBuilder;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.portal.PortalEmailService;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends a portal email to the recipient portal contact when a proposal is Sent. Wired post-Epic
 * 498B per OBS-703 — closes the gap where {@code ProposalSentEvent} had a portal-sync handler + a
 * firm-side notification handler but no portal-side email handler. Renders {@code
 * templates/email/portal-new-proposal.html}.
 *
 * <p>Fire-and-forget: exceptions are caught and logged; a failed email never rolls back the
 * originating proposal.send transaction (we run AFTER_COMMIT). Honours ADR-258 by living beside
 * {@link ProposalPortalSyncEventHandler} rather than extending {@code
 * PortalEmailNotificationChannel}, which intentionally excludes proposal events.
 */
@Component
public class ProposalSentEmailHandler {

  private static final Logger log = LoggerFactory.getLogger(ProposalSentEmailHandler.class);
  private static final DateTimeFormatter EXPIRES_AT_FMT =
      DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

  private final ProposalRepository proposalRepository;
  private final PortalContactRepository portalContactRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final OrganizationRepository organizationRepository;
  private final PortalEmailService portalEmailService;
  private final EmailContextBuilder emailContextBuilder;
  private final String portalBaseUrl;
  private final String productName;

  public ProposalSentEmailHandler(
      ProposalRepository proposalRepository,
      PortalContactRepository portalContactRepository,
      OrgSettingsRepository orgSettingsRepository,
      OrganizationRepository organizationRepository,
      PortalEmailService portalEmailService,
      EmailContextBuilder emailContextBuilder,
      @Value("${docteams.app.portal-base-url:http://localhost:3002}") String portalBaseUrl,
      @Value("${docteams.app.product-name:Kazi}") String productName) {
    this.proposalRepository = proposalRepository;
    this.portalContactRepository = portalContactRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.organizationRepository = organizationRepository;
    this.portalEmailService = portalEmailService;
    this.emailContextBuilder = emailContextBuilder;
    this.portalBaseUrl = portalBaseUrl;
    this.productName = productName;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onProposalSent(ProposalSentEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            var proposal =
                proposalRepository
                    .findById(event.entityId())
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "Proposal not found after commit: " + event.entityId()));
            if (proposal.getPortalContactId() == null) {
              log.warn(
                  "Skipping portal-new-proposal email — proposal {} has no portalContactId",
                  proposal.getId());
              return;
            }
            var contact =
                portalContactRepository
                    .findById(proposal.getPortalContactId())
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "PortalContact not found: " + proposal.getPortalContactId()));

            var orgSettings = orgSettingsRepository.findForCurrentTenant().orElse(null);
            String orgName =
                organizationRepository
                    .findByClerkOrgId(event.orgId())
                    .map(o -> o.getName())
                    .orElse(productName);

            Map<String, Object> context =
                new HashMap<>(emailContextBuilder.buildBaseContext(contact.getDisplayName(), null));
            context.put("contactName", contact.getDisplayName());
            context.put("orgName", orgName);
            context.put("proposalNumber", proposal.getProposalNumber());
            context.put("proposalTitle", proposal.getTitle());
            context.put("portalUrl", portalBaseUrl + "/proposals/" + proposal.getId());
            if (orgSettings != null && orgSettings.getBrandColor() != null) {
              context.put("brandColor", orgSettings.getBrandColor());
            }
            if (proposal.getExpiresAt() != null) {
              context.put(
                  "expiresAtFormatted",
                  proposal.getExpiresAt().atZone(ZoneOffset.UTC).format(EXPIRES_AT_FMT));
            }
            context.put(
                "subject",
                "%s: New proposal %s for your review"
                    .formatted(orgName, proposal.getProposalNumber()));

            portalEmailService.sendNewProposalEmail(contact, context);
          } catch (Exception e) {
            log.error(
                "Failed to send portal-new-proposal email for proposal {}", event.entityId(), e);
          }
        });
  }

  /** Mirrors the binding pattern used by {@link ProposalPortalSyncEventHandler}. */
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
