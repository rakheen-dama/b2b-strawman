package io.b2mash.b2b.b2bstrawman.portal;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.EntityNoun;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.portal.directory.PortalContactDirectoryService;
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
  private final PortalContactDirectoryService directoryService;

  public PortalContactService(
      PortalContactRepository portalContactRepository,
      CustomerRepository customerRepository,
      PortalContactDirectoryService directoryService) {
    this.portalContactRepository = portalContactRepository;
    this.customerRepository = customerRepository;
    this.directoryService = directoryService;
  }

  @Transactional
  public PortalContact createContact(
      String orgId,
      UUID customerId,
      String email,
      String displayName,
      PortalContact.ContactRole role) {
    // Validate customer exists (JPQL query scoped to current tenant schema via search_path)
    customerRepository
        .findById(customerId)
        .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.CLIENT, customerId));

    // Check email uniqueness per customer
    if (portalContactRepository.existsByEmailAndCustomerId(email, customerId)) {
      throw new ResourceConflictException(
          "Portal contact email conflict",
          "A contact with this email already exists for this client");
    }

    var contact = new PortalContact(orgId, customerId, email, displayName, role);
    var saved = portalContactRepository.save(contact);
    // ADR-351: keep the global email → firm directory in step once this write commits.
    directoryService.reconcileAfterCommit(saved.getEmail(), saved.getOrgId());
    log.info(
        "Created portal contact {} for customer {} in org {}", saved.getId(), customerId, orgId);
    return saved;
  }

  @Transactional(readOnly = true)
  public List<PortalContact> listContactsForCustomer(UUID customerId) {
    return portalContactRepository.findByCustomerId(customerId);
  }

  @Transactional(readOnly = true)
  public List<PortalContactSummary> listPortalContactSummaries(UUID customerId) {
    customerRepository
        .findById(customerId)
        .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.CLIENT, customerId));
    return portalContactRepository.findByCustomerId(customerId).stream()
        .map(PortalContactSummary::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public Optional<PortalContact> findByEmailAndOrg(String email, String orgId) {
    return portalContactRepository.findByEmailAndOrgId(email, orgId);
  }

  @Transactional
  public PortalContact suspendContact(UUID contactId) {
    var contact =
        portalContactRepository
            .findById(contactId)
            .orElseThrow(() -> new ResourceNotFoundException("PortalContact", contactId));
    contact.suspend();
    var saved = portalContactRepository.save(contact);
    // A suspended contact cannot sign in, so it must not be routable (ADR-351).
    directoryService.reconcileAfterCommit(saved.getEmail(), saved.getOrgId());
    log.info("Suspended portal contact {}", contactId);
    return saved;
  }

  @Transactional
  public PortalContact archiveContact(UUID contactId) {
    var contact =
        portalContactRepository
            .findById(contactId)
            .orElseThrow(() -> new ResourceNotFoundException("PortalContact", contactId));
    contact.archive();
    var saved = portalContactRepository.save(contact);
    directoryService.reconcileAfterCommit(saved.getEmail(), saved.getOrgId());
    log.info("Archived portal contact {}", contactId);
    return saved;
  }
}
