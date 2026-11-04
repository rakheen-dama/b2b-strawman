package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import io.b2mash.b2b.b2bstrawman.config.KaziTime;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.controller.PaymentStatusResponse;
import io.b2mash.b2b.b2bstrawman.customerbackend.dto.PortalInvoicePayment;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalCommentView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalInvoiceLineView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalInvoiceView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalProjectSummaryView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalProjectView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalRequestItemView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalRequestView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalTaskView;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.exception.EntityNoun;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEvent;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEventRepository;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEventStatus;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentMethod;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentReceiptService;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.tax.dto.TaxBreakdownEntry;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentService;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Service layer for portal read-model queries. Provides project detail, comments, summary, and
 * contact profile lookups for authenticated portal customers.
 */
@Service
public class PortalReadModelService {

  private static final Logger log = LoggerFactory.getLogger(PortalReadModelService.class);

  private final PortalReadModelRepository readModelRepository;
  private final PortalContactRepository portalContactRepository;
  private final CustomerRepository customerRepository;
  private final GeneratedDocumentRepository generatedDocumentRepository;
  private final StorageService storageService;
  private final ObjectMapper objectMapper;
  private final GeneratedDocumentService generatedDocumentService;
  private final PaymentEventRepository paymentEventRepository;
  private final PaymentReceiptService paymentReceiptService;

  public PortalReadModelService(
      PortalReadModelRepository readModelRepository,
      PortalContactRepository portalContactRepository,
      CustomerRepository customerRepository,
      GeneratedDocumentRepository generatedDocumentRepository,
      StorageService storageService,
      ObjectMapper objectMapper,
      GeneratedDocumentService generatedDocumentService,
      PaymentEventRepository paymentEventRepository,
      PaymentReceiptService paymentReceiptService) {
    this.readModelRepository = readModelRepository;
    this.portalContactRepository = portalContactRepository;
    this.customerRepository = customerRepository;
    this.generatedDocumentRepository = generatedDocumentRepository;
    this.storageService = storageService;
    this.objectMapper = objectMapper;
    this.generatedDocumentService = generatedDocumentService;
    this.paymentEventRepository = paymentEventRepository;
    this.paymentReceiptService = paymentReceiptService;
  }

  /** Lists all portal projects for the given customer and org via the read model. */
  public List<PortalProjectView> listProjects(String orgId, UUID customerId) {
    return readModelRepository.findProjectsByCustomer(orgId, customerId);
  }

  /** Returns the portal project detail for the given project, customer, and org. */
  public PortalProjectView getProjectDetail(UUID projectId, UUID customerId, String orgId) {
    return readModelRepository
        .findProjectDetail(projectId, customerId, orgId)
        .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.MATTER, projectId));
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
        .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.MATTER, projectId));

    return readModelRepository.findCommentsByProject(projectId, orgId);
  }

  /** Returns the project summary (time/billing rollup) or empty if no summary exists. */
  public Optional<PortalProjectSummaryView> getProjectSummary(
      UUID projectId, UUID customerId, String orgId) {
    // Verify customer is linked to the project
    readModelRepository
        .findProjectDetail(projectId, customerId, orgId)
        .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.MATTER, projectId));

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
            .orElseThrow(
                () -> new ResourceNotFoundException(EntityNoun.CLIENT, contact.getCustomerId()));

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
        .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.MATTER, projectId));

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
            .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.FEE_NOTE, invoiceId));

    // Verify the invoice belongs to this customer
    if (!invoice.customerId().equals(customerId)) {
      throw new ResourceNotFoundException(EntityNoun.FEE_NOTE, invoiceId);
    }

    var lines = readModelRepository.findInvoiceLinesByInvoice(invoiceId);
    var taxBreakdown = parseTaxBreakdownJson(invoice.taxBreakdownJson());
    return new InvoiceDetail(invoice, lines, taxBreakdown);
  }

  /**
   * Returns a presigned download URL for the fee note: the newest generated document of the
   * resolved fee-note template ({@code resolveDefaultInvoiceTemplate}) — never "the newest INVOICE
   * document", which a payment receipt (or any later INVOICE-typed document) would hijack (Phase 89
   * 632B, ADR-364, G88-F32).
   */
  @Transactional(readOnly = true)
  public String getInvoiceDownloadUrl(UUID invoiceId, UUID customerId, String orgId) {
    // Verify ownership
    getInvoiceDetail(invoiceId, customerId, orgId);

    UUID feeNoteTemplateId =
        generatedDocumentService
            .resolveDefaultInvoiceTemplate()
            .map(DocumentTemplate::getId)
            .orElseThrow(() -> new ResourceNotFoundException("GeneratedDocument", invoiceId));
    var feeNote =
        generatedDocumentRepository
            .findByPrimaryEntityTypeAndPrimaryEntityIdOrderByGeneratedAtDesc(
                TemplateEntityType.INVOICE, invoiceId)
            .stream()
            .filter(doc -> feeNoteTemplateId.equals(doc.getTemplateId()))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("GeneratedDocument", invoiceId));

    var presigned = storageService.generateDownloadUrl(feeNote.getS3Key(), Duration.ofHours(1));
    return presigned.url();
  }

  /**
   * The fee note's COMPLETED payments, oldest first (Phase 89 632B, §14.4.5). 404 unless the fee
   * note belongs to the contact's client.
   */
  @Transactional(readOnly = true)
  public List<PortalInvoicePayment> listInvoicePayments(
      UUID invoiceId, UUID customerId, String orgId) {
    requireOwnedInvoice(invoiceId, customerId, orgId);
    return paymentEventRepository
        .findByInvoiceIdAndStatus(invoiceId, PaymentEventStatus.COMPLETED)
        .stream()
        .sorted(Comparator.comparing(PaymentEvent::getCreatedAt).thenComparing(PaymentEvent::getId))
        .map(
            event ->
                new PortalInvoicePayment(
                    event.getId(),
                    LocalDate.ofInstant(event.getCreatedAt(), KaziTime.ZONE),
                    PaymentMethod.labelOf(event.getPaymentMethod()),
                    event.getAmount(),
                    event.getPaymentReference(),
                    event.getReceiptGeneratedDocumentId() != null))
        .toList();
  }

  /**
   * Presigned (1 h) download URL for one payment's receipt (Phase 89 632B, §14.6.2): the fee note
   * must belong to the contact's client and the payment event to the fee note (404 otherwise); a
   * missing receipt is generated now, in this transaction, by the idempotent lazy path — the actor
   * is the fee note's creator (the portal has no member).
   */
  @Transactional
  public String getReceiptDownloadUrl(
      UUID invoiceId, UUID paymentEventId, UUID customerId, String orgId) {
    requireOwnedInvoice(invoiceId, customerId, orgId);
    UUID documentId = paymentReceiptService.ensureReceipt(invoiceId, paymentEventId, null);
    var receipt =
        generatedDocumentRepository
            .findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("GeneratedDocument", documentId));
    return storageService.generateDownloadUrl(receipt.getS3Key(), Duration.ofHours(1)).url();
  }

  /** The portal fee note of this client, or 404 (same rule as the fee-note download). */
  private void requireOwnedInvoice(UUID invoiceId, UUID customerId, String orgId) {
    var invoice =
        readModelRepository
            .findInvoiceById(invoiceId, orgId)
            .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.FEE_NOTE, invoiceId));
    if (!invoice.customerId().equals(customerId)) {
      throw new ResourceNotFoundException(EntityNoun.FEE_NOTE, invoiceId);
    }
  }

  /** Returns the payment status for a portal invoice. Verifies customer ownership. */
  public PaymentStatusResponse getPaymentStatus(UUID invoiceId, UUID customerId, String orgId) {
    var invoice =
        readModelRepository
            .findInvoiceById(invoiceId, orgId)
            .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.FEE_NOTE, invoiceId));

    if (!invoice.customerId().equals(customerId)) {
      throw new ResourceNotFoundException(EntityNoun.FEE_NOTE, invoiceId);
    }

    return new PaymentStatusResponse(
        invoice.status(), invoice.paidAt() != null ? invoice.paidAt().toString() : null);
  }

  public record InvoiceDetail(
      PortalInvoiceView invoice,
      List<PortalInvoiceLineView> lines,
      List<TaxBreakdownEntry> taxBreakdown) {}

  /** Returns pending acceptance requests for the given portal contact. */
  @Transactional(readOnly = true)
  public List<io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalAcceptanceView>
      findPendingAcceptances(UUID portalContactId) {
    return readModelRepository.findPendingAcceptancesByContactId(portalContactId);
  }

  // ── Information request query methods ──────────────────────────────

  /** Lists all portal information requests for a given portal contact. */
  public List<PortalRequestView> findRequestsByPortalContactId(UUID portalContactId) {
    return readModelRepository.findRequestsByPortalContactId(portalContactId);
  }

  /** Returns a single request by ID, verifying the portal contact owns it. */
  public PortalRequestView findRequestById(UUID requestId, UUID portalContactId) {
    var request =
        readModelRepository
            .findRequestById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("InformationRequest", requestId));

    if (!request.portalContactId().equals(portalContactId)) {
      throw new ResourceNotFoundException("InformationRequest", requestId);
    }
    return request;
  }

  /** Lists items for a request, verifying the portal contact owns the request. */
  public List<PortalRequestItemView> findRequestItemsByRequestId(
      UUID requestId, UUID portalContactId) {
    // Verify ownership
    findRequestById(requestId, portalContactId);
    return readModelRepository.findRequestItemsByRequestId(requestId);
  }

  /** Deserializes the tax breakdown JSON string from the read-model into a typed list. */
  List<TaxBreakdownEntry> parseTaxBreakdownJson(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(
          json,
          objectMapper
              .getTypeFactory()
              .constructCollectionType(List.class, TaxBreakdownEntry.class));
    } catch (JacksonException e) {
      log.warn("Failed to parse tax breakdown JSON", e);
      return null;
    }
  }
}
