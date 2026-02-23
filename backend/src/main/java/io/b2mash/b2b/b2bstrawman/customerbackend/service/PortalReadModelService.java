package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalCommentView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalInvoiceLineView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalInvoiceView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalProjectSummaryView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalProjectView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalTaskView;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import java.time.Duration;
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
  private final GeneratedDocumentRepository generatedDocumentRepository;
  private final StorageService storageService;

  public PortalReadModelService(
      PortalReadModelRepository readModelRepository,
      PortalContactRepository portalContactRepository,
      CustomerRepository customerRepository,
      GeneratedDocumentRepository generatedDocumentRepository,
      StorageService storageService) {
    this.readModelRepository = readModelRepository;
    this.portalContactRepository = portalContactRepository;
    this.customerRepository = customerRepository;
    this.generatedDocumentRepository = generatedDocumentRepository;
    this.storageService = storageService;
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

  /** Lists tasks for a portal project. Verifies the customer is linked to the project. */
  public List<PortalTaskView> listTasks(String orgId, UUID customerId, UUID projectId) {
    // Verify customer is linked to the project
    readModelRepository
        .findProjectDetail(projectId, customerId, orgId)
        .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

    return readModelRepository.findTasksByProject(projectId, orgId);
  }

  // ── Invoice methods ──────────────────────────────────────────────────

  /** Lists all portal invoices for the authenticated customer. */
  public List<PortalInvoiceView> listInvoices(String orgId, UUID customerId) {
    return readModelRepository.findInvoicesByCustomer(orgId, customerId);
  }

  /** Returns invoice detail with lines. Throws 404 if not found or wrong customer. */
  public InvoiceDetail getInvoiceDetail(UUID invoiceId, UUID customerId, String orgId) {
    var invoice =
        readModelRepository
            .findInvoiceById(invoiceId, orgId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    // Verify the invoice belongs to this customer
    if (!invoice.customerId().equals(customerId)) {
      throw new ResourceNotFoundException("Invoice", invoiceId);
    }

    var lines = readModelRepository.findInvoiceLinesByInvoice(invoiceId);
    return new InvoiceDetail(invoice, lines);
  }

  /** Returns a presigned download URL for the most recent PDF generated for this invoice. */
  @Transactional(readOnly = true)
  public String getInvoiceDownloadUrl(UUID invoiceId, UUID customerId, String orgId) {
    // Verify ownership
    getInvoiceDetail(invoiceId, customerId, orgId);

    var docs =
        generatedDocumentRepository.findByPrimaryEntityTypeAndPrimaryEntityIdOrderByGeneratedAtDesc(
            TemplateEntityType.INVOICE, invoiceId);
    if (docs.isEmpty()) {
      throw new ResourceNotFoundException("GeneratedDocument", invoiceId);
    }

    var presigned =
        storageService.generateDownloadUrl(docs.getFirst().getS3Key(), Duration.ofHours(1));
    return presigned.url();
  }

  public record InvoiceDetail(PortalInvoiceView invoice, List<PortalInvoiceLineView> lines) {}
}
