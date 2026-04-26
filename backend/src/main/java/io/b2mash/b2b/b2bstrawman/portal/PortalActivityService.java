package io.b2mash.b2b.b2bstrawman.portal;

import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Surfaces the portal activity timeline to authenticated portal contacts. Combines two streams:
 *
 * <ul>
 *   <li><b>MINE</b> -- events the contact authored (actor_type = PORTAL_CONTACT, actor_id = contact
 *       id).
 *   <li><b>FIRM</b> -- non-portal-contact events on projects linked to the contact's customer.
 *   <li><b>ALL</b> -- both streams unioned.
 * </ul>
 *
 * Tenant isolation is provided by Hibernate {@code search_path}; customer/contact scoping is
 * provided by the {@link RequestScopes} bound by {@code CustomerAuthFilter}.
 */
@Service
public class PortalActivityService {

  public enum Tab {
    MINE,
    FIRM,
    ALL
  }

  private final AuditEventRepository auditEventRepository;

  public PortalActivityService(AuditEventRepository auditEventRepository) {
    this.auditEventRepository = auditEventRepository;
  }

  public Page<PortalActivityEventResponse> listActivity(Tab tab, Pageable pageable) {
    UUID customerId = RequestScopes.requireCustomerId();
    // PORTAL_CONTACT_ID is bound by CustomerAuthFilter when contact resolution succeeds; the
    // filter falls through silently when no contact row exists for the authenticated customer
    // (CustomerAuthFilter.java:96-104). Degrade to firm-only rather than 500 in that case so
    // the page still renders for customers without a registered contact.
    UUID portalContactId =
        RequestScopes.PORTAL_CONTACT_ID.isBound() ? RequestScopes.PORTAL_CONTACT_ID.get() : null;
    // Native queries already ORDER BY occurred_at DESC. Strip any sort the caller passes so
    // Spring does not append a duplicate / property-name-based ORDER BY clause that would fail
    // against the snake_case column names.
    Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    Page<AuditEvent> page =
        switch (tab) {
          case MINE ->
              portalContactId == null
                  ? Page.empty(unsorted)
                  : auditEventRepository.findActivityMineForPortalContact(
                      portalContactId, unsorted);
          case FIRM -> auditEventRepository.findActivityFirmForCustomer(customerId, unsorted);
          case ALL ->
              portalContactId == null
                  ? auditEventRepository.findActivityFirmForCustomer(customerId, unsorted)
                  : auditEventRepository.findActivityForPortalContact(
                      portalContactId, customerId, unsorted);
        };
    return page.map(PortalActivityEventResponse::from);
  }
}
