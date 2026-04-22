package io.b2mash.b2b.b2bstrawman.informationrequest;

import io.b2mash.b2b.b2bstrawman.event.InformationRequestCompletedEvent;
import io.b2mash.b2b.b2bstrawman.event.InformationRequestSentEvent;
import io.b2mash.b2b.b2bstrawman.event.RequestItemAcceptedEvent;
import io.b2mash.b2b.b2bstrawman.event.RequestItemRejectedEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.MagicLinkService;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class InformationRequestEmailEventListener {

  private static final Logger log =
      LoggerFactory.getLogger(InformationRequestEmailEventListener.class);

  private final InformationRequestEmailService emailService;
  private final InformationRequestRepository requestRepository;
  private final RequestItemRepository itemRepository;
  private final PortalContactRepository portalContactRepository;
  private final MagicLinkService magicLinkService;
  private final TransactionTemplate requiresNewTxTemplate;

  public InformationRequestEmailEventListener(
      InformationRequestEmailService emailService,
      InformationRequestRepository requestRepository,
      RequestItemRepository itemRepository,
      PortalContactRepository portalContactRepository,
      MagicLinkService magicLinkService,
      PlatformTransactionManager transactionManager) {
    this.emailService = emailService;
    this.requestRepository = requestRepository;
    this.itemRepository = itemRepository;
    this.portalContactRepository = portalContactRepository;
    this.magicLinkService = magicLinkService;
    // AFTER_COMMIT listeners fire while the prior transaction's synchronization is still
    // active. A nested TransactionTemplate with default REQUIRED propagation would attempt
    // to join that sync instead of committing independently, silently losing writes. Force
    // a fresh tx so magic-link persistence commits to the tenant schema.
    this.requiresNewTxTemplate = new TransactionTemplate(transactionManager);
    this.requiresNewTxTemplate.setPropagationBehavior(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRequestSent(InformationRequestSentEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            var request = requestRepository.findById(event.requestId()).orElse(null);
            if (request == null) {
              log.warn("Request not found for email delivery: {}", event.requestId());
              return;
            }
            var contact = portalContactRepository.findById(event.portalContactId()).orElse(null);
            if (contact == null || contact.getEmail() == null) {
              log.warn("Portal contact not found or no email for request: {}", event.requestId());
              return;
            }
            var items = itemRepository.findByRequestId(request.getId());

            // Mint a magic-link token so the email CTA deep-links into the portal
            // authenticated flow. generateToken also dispatches a redundant
            // portal-magic-link email (Option A in GAP-L-42 spec) — acceptable for now.
            String rawToken;
            try {
              rawToken =
                  requiresNewTxTemplate.execute(
                      status -> magicLinkService.generateToken(event.portalContactId(), null));
            } catch (Exception e) {
              log.warn(
                  "Failed to mint magic-link token for request {}; "
                      + "sending request-sent email without deep link",
                  event.requestId(),
                  e);
              rawToken = null;
            }

            emailService.sendRequestSentEmail(
                contact.getEmail(),
                contact.getDisplayName(),
                request.getRequestNumber(),
                items.size(),
                request.getId(),
                rawToken,
                contact.getOrgId());
          } catch (Exception e) {
            log.error("Failed to send request-sent email for request={}", event.requestId(), e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onItemAccepted(RequestItemAcceptedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            var request = requestRepository.findById(event.requestId()).orElse(null);
            if (request == null) {
              log.warn("Request not found for item-accepted email: {}", event.requestId());
              return;
            }
            var contact =
                portalContactRepository.findById(request.getPortalContactId()).orElse(null);
            if (contact == null || contact.getEmail() == null) {
              log.warn("Portal contact not found or no email for request: {}", event.requestId());
              return;
            }
            var item = itemRepository.findById(event.itemId()).orElse(null);
            if (item == null) {
              log.warn("Item not found for item-accepted email: {}", event.itemId());
              return;
            }
            emailService.sendItemAcceptedEmail(
                contact.getEmail(),
                contact.getDisplayName(),
                item.getName(),
                request.getRequestNumber(),
                request.getId());
          } catch (Exception e) {
            log.error("Failed to send item-accepted email for item={}", event.itemId(), e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onItemRejected(RequestItemRejectedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            var request = requestRepository.findById(event.requestId()).orElse(null);
            if (request == null) {
              log.warn("Request not found for item-rejected email: {}", event.requestId());
              return;
            }
            var contact =
                portalContactRepository.findById(request.getPortalContactId()).orElse(null);
            if (contact == null || contact.getEmail() == null) {
              log.warn("Portal contact not found or no email for request: {}", event.requestId());
              return;
            }
            var item = itemRepository.findById(event.itemId()).orElse(null);
            if (item == null) {
              log.warn("Item not found for item-rejected email: {}", event.itemId());
              return;
            }
            emailService.sendItemRejectedEmail(
                contact.getEmail(),
                contact.getDisplayName(),
                item.getName(),
                request.getRequestNumber(),
                event.rejectionReason(),
                request.getId());
          } catch (Exception e) {
            log.error("Failed to send item-rejected email for item={}", event.itemId(), e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRequestCompleted(InformationRequestCompletedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            var request = requestRepository.findById(event.requestId()).orElse(null);
            if (request == null) {
              log.warn("Request not found for request-completed email: {}", event.requestId());
              return;
            }
            var contact = portalContactRepository.findById(event.portalContactId()).orElse(null);
            if (contact == null || contact.getEmail() == null) {
              log.warn("Portal contact not found or no email for request: {}", event.requestId());
              return;
            }
            var items = itemRepository.findByRequestId(request.getId());
            emailService.sendRequestCompletedEmail(
                contact.getEmail(),
                contact.getDisplayName(),
                request.getRequestNumber(),
                items.size(),
                request.getId());
          } catch (Exception e) {
            log.error(
                "Failed to send request-completed email for request={}", event.requestId(), e);
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
      log.warn("Information-request event missing tenantId; running without tenant scope");
      action.run();
    }
  }
}
