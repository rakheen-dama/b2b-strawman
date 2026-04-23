package io.b2mash.b2b.b2bstrawman.portal;

import io.b2mash.b2b.b2bstrawman.customerbackend.event.CustomerCreatedEvent;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auto-creates a default {@link PortalContact} whenever a {@link CustomerCreatedEvent} carries an
 * email address. This unblocks the firm-side "send information request / proposal / fee note" flows
 * for the typical one-contact-per-customer case, with no additional UI required.
 *
 * <p>GAP-L-34 — Scenario 3.9 literally reads "portal contact auto-populated from client record" —
 * auto-create is the scenario-specified behaviour. For customers needing multiple named contacts
 * (billing vs. primary), a future firm-side "Add Portal Contact" dialog (GAP-L-40) will layer on
 * top.
 *
 * <p>The listener runs in the originating customer-create transaction so {@code search_path} is
 * already bound via {@link RequestScopes#TENANT_ID}. Idempotency on replay (e.g. background retries
 * publishing the same event) is handled by a {@code existsByEmailAndCustomerId} pre-check so that a
 * duplicate never bubbles up as an exception that would mark the outer transaction rollback-only.
 */
@Component
public class PortalContactAutoProvisioner {

  private static final Logger log = LoggerFactory.getLogger(PortalContactAutoProvisioner.class);

  private final PortalContactService portalContactService;
  private final PortalContactRepository portalContactRepository;

  public PortalContactAutoProvisioner(
      PortalContactService portalContactService, PortalContactRepository portalContactRepository) {
    this.portalContactService = portalContactService;
    this.portalContactRepository = portalContactRepository;
  }

  @EventListener
  @Transactional
  public void onCustomerCreated(CustomerCreatedEvent event) {
    String email = event.getEmail();
    if (email == null || email.isBlank()) {
      log.debug(
          "Customer {} created without email — skipping portal-contact auto-provision",
          event.getCustomerId());
      return;
    }
    String orgId = event.getOrgId();
    if (orgId == null) {
      orgId = RequestScopes.getOrgIdOrNull();
    }
    if (orgId == null) {
      log.warn(
          "Customer {} created but orgId unavailable — cannot auto-provision portal contact",
          event.getCustomerId());
      return;
    }

    // Idempotent pre-check: if a contact already exists for this customer+email, skip silently.
    // The try/catch below is the belt — this pre-check is the suspender for the common replay path.
    if (portalContactRepository.existsByEmailAndCustomerId(email, event.getCustomerId())) {
      log.debug(
          "Portal contact for customer {} already exists with email {} — skipping auto-provision",
          event.getCustomerId(),
          maskEmail(email));
      return;
    }

    // Belt against a race: two concurrent events passing the pre-check would both call
    // createContact
    // and one would hit the uq_portal_contacts_email_customer unique constraint. Swallowing the
    // resulting exception here prevents the outer customer-create transaction from being marked
    // rollback-only.
    try {
      var contact =
          portalContactService.createContact(
              orgId,
              event.getCustomerId(),
              email,
              event.getName(),
              PortalContact.ContactRole.GENERAL);
      log.info(
          "Auto-provisioned portal contact {} for customer {} (email={})",
          contact.getId(),
          event.getCustomerId(),
          maskEmail(email));
    } catch (ResourceConflictException | DataIntegrityViolationException e) {
      log.debug(
          "Portal contact for customer {} already exists with email {} (race) — skipping",
          event.getCustomerId(),
          maskEmail(email));
    }
  }

  /**
   * Masks the local-part of an email for safe logging. Keeps the first character and the domain
   * visible for operational debugging, hides the rest. Example: {@code alice@example.com} → {@code
   * a****@example.com}. Returns the input unchanged if null/blank or missing an {@code @}.
   */
  private static String maskEmail(String email) {
    if (email == null || email.isBlank()) {
      return email;
    }
    int at = email.indexOf('@');
    if (at <= 0) {
      return email;
    }
    return email.charAt(0) + "****" + email.substring(at);
  }
}
