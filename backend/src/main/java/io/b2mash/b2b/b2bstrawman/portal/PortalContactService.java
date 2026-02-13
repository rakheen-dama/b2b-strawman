package io.b2mash.b2b.b2bstrawman.portal;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortalContactService {

  private static final Logger log = LoggerFactory.getLogger(PortalContactService.class);

  private final PortalContactRepository portalContactRepository;
  private final CustomerRepository customerRepository;

  public PortalContactService(
      PortalContactRepository portalContactRepository, CustomerRepository customerRepository) {
    this.portalContactRepository = portalContactRepository;
    this.customerRepository = customerRepository;
  }

  @Transactional
  public PortalContact createContact(
      String orgId,
      UUID customerId,
      String email,
      String displayName,
      PortalContact.ContactRole role) {
    // Validate customer exists (uses JPQL-based query that respects @Filter)
    customerRepository
        .findOneById(customerId)
        .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    // Check email uniqueness per customer
    if (portalContactRepository.existsByEmailAndCustomerId(email, customerId)) {
      throw new ResourceConflictException(
          "Portal contact email conflict",
          "A contact with this email already exists for this customer");
    }

    var contact = new PortalContact(orgId, customerId, email, displayName, role);
    var saved = portalContactRepository.save(contact);
    log.info(
        "Created portal contact {} for customer {} in org {}", saved.getId(), customerId, orgId);
    return saved;
  }

  @Transactional(readOnly = true)
  public List<PortalContact> listContactsForCustomer(UUID customerId) {
    return portalContactRepository.findByCustomerId(customerId);
  }

  @Transactional(readOnly = true)
  public Optional<PortalContact> findByEmailAndOrg(String email, String orgId) {
    return portalContactRepository.findByEmailAndOrgId(email, orgId);
  }

  @Transactional
  public PortalContact suspendContact(UUID contactId) {
    var contact =
        portalContactRepository
            .findOneById(contactId)
            .orElseThrow(() -> new ResourceNotFoundException("PortalContact", contactId));
    contact.suspend();
    var saved = portalContactRepository.save(contact);
    log.info("Suspended portal contact {}", contactId);
    return saved;
  }

  @Transactional
  public PortalContact archiveContact(UUID contactId) {
    var contact =
        portalContactRepository
            .findOneById(contactId)
            .orElseThrow(() -> new ResourceNotFoundException("PortalContact", contactId));
    contact.archive();
    var saved = portalContactRepository.save(contact);
    log.info("Archived portal contact {}", contactId);
    return saved;
  }
}
