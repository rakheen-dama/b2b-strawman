package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalCommentView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalProjectSummaryView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalProjectView;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for portal read-model queries. Provides project detail, comments, summary, and
 * contact profile lookups for authenticated portal customers.
 */
@Service
public class PortalReadModelService {

  private final PortalReadModelRepository readModelRepository;
  private final PortalContactRepository portalContactRepository;
  private final CustomerRepository customerRepository;

  public PortalReadModelService(
      PortalReadModelRepository readModelRepository,
      PortalContactRepository portalContactRepository,
      CustomerRepository customerRepository) {
    this.readModelRepository = readModelRepository;
    this.portalContactRepository = portalContactRepository;
    this.customerRepository = customerRepository;
  }

  /** Returns the portal project detail for the given project, customer, and org. */
  public PortalProjectView getProjectDetail(UUID projectId, UUID customerId, String orgId) {
    return readModelRepository
        .findProjectDetail(projectId, customerId, orgId)
        .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
  }

  /**
   * Lists comments for a portal project. Verifies the customer is linked to the project via the
   * portal read-model before returning comments.
   */
  public List<PortalCommentView> listProjectComments(
      UUID projectId, UUID customerId, String orgId) {
    // Verify customer is linked to the project
    readModelRepository
        .findProjectDetail(projectId, customerId, orgId)
        .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

    return readModelRepository.findCommentsByProject(projectId, orgId);
  }

  /** Returns the project summary (time/billing rollup) or empty if no summary exists. */
  public Optional<PortalProjectSummaryView> getProjectSummary(
      UUID projectId, UUID customerId, String orgId) {
    // Verify customer is linked to the project
    readModelRepository
        .findProjectDetail(projectId, customerId, orgId)
        .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

    return readModelRepository.findProjectSummary(projectId, customerId, orgId);
  }

  /** Returns the contact profile with customer name for the authenticated portal user. */
  @Transactional(readOnly = true)
  public ContactProfile getContactProfile(UUID portalContactId) {
    var contact =
        portalContactRepository
            .findById(portalContactId)
            .orElseThrow(() -> new ResourceNotFoundException("PortalContact", portalContactId));

    var customer =
        customerRepository
            .findById(contact.getCustomerId())
            .orElseThrow(() -> new ResourceNotFoundException("Customer", contact.getCustomerId()));

    return new ContactProfile(
        contact.getId(),
        customer.getId(),
        customer.getName(),
        contact.getEmail(),
        contact.getDisplayName(),
        contact.getRole().name());
  }

  public record ContactProfile(
      UUID contactId,
      UUID customerId,
      String customerName,
      String email,
      String displayName,
      String role) {}
}
