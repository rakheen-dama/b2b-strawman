package io.b2mash.b2b.b2bstrawman.proposal;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.event.ProposalSentEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.template.TiptapRenderer;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Syncs proposal data to the portal read-model after the send transaction commits. Using
 * AFTER_COMMIT ensures that the portal row is only written for committed proposals, preventing
 * orphan rows if the main transaction rolls back (the portal uses a separate datasource).
 */
@Component
public class ProposalPortalSyncEventHandler {

  private static final Logger log = LoggerFactory.getLogger(ProposalPortalSyncEventHandler.class);

  private final ProposalRepository proposalRepository;
  private final CustomerRepository customerRepository;
  private final PortalContactRepository portalContactRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final OrganizationRepository organizationRepository;
  private final ProposalVariableResolver proposalVariableResolver;
  private final TiptapRenderer tiptapRenderer;
  private final ProposalPortalSyncService proposalPortalSyncService;

  public ProposalPortalSyncEventHandler(
      ProposalRepository proposalRepository,
      CustomerRepository customerRepository,
      PortalContactRepository portalContactRepository,
      OrgSettingsRepository orgSettingsRepository,
      OrganizationRepository organizationRepository,
      ProposalVariableResolver proposalVariableResolver,
      TiptapRenderer tiptapRenderer,
      ProposalPortalSyncService proposalPortalSyncService) {
    this.proposalRepository = proposalRepository;
    this.customerRepository = customerRepository;
    this.portalContactRepository = portalContactRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.organizationRepository = organizationRepository;
    this.proposalVariableResolver = proposalVariableResolver;
    this.tiptapRenderer = tiptapRenderer;
    this.proposalPortalSyncService = proposalPortalSyncService;
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

            var customer =
                customerRepository
                    .findById(proposal.getCustomerId())
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "Customer not found: " + proposal.getCustomerId()));

            var contact =
                portalContactRepository
                    .findById(proposal.getPortalContactId())
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "PortalContact not found: " + proposal.getPortalContactId()));

            var orgSettings = orgSettingsRepository.findForCurrentTenant().orElse(null);

            String orgId = event.orgId();
            var org = organizationRepository.findByClerkOrgId(orgId).orElse(null);
            String orgName = org != null ? org.getName() : orgId;

            var variableContext =
                proposalVariableResolver.buildContext(
                    proposal, customer, contact, orgSettings, orgName);
            Map<String, Object> renderContext = new HashMap<>(variableContext);
            String contentHtml =
                tiptapRenderer.render(proposal.getContentJson(), renderContext, Map.of(), null);

            proposalPortalSyncService.syncProposalToPortal(
                proposal, contentHtml, orgId, orgName, orgSettings);

            log.info(
                "Portal sync completed for proposal {} after commit", proposal.getProposalNumber());
          } catch (Exception e) {
            log.error("Failed to sync proposal {} to portal after commit", event.entityId(), e);
          }
        });
  }

  /**
   * Binds tenant and org ScopedValues so that the correct dedicated schema is selected via {@code
   * search_path} in the handler's new transaction.
   */
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
