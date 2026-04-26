package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.invoice.dto.AddLineItemRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.CreateInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.InvoiceResponse;
import io.b2mash.b2b.b2bstrawman.invoice.dto.PaymentEventResponse;
import io.b2mash.b2b.b2bstrawman.invoice.dto.SendInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UnbilledTimeResponse;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UpdateInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UpdateLineItemRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facade that preserves the original InvoiceService public API while delegating to focused
 * collaborator services: InvoiceCreationService, InvoiceTransitionService, InvoiceRenderingService,
 * UnbilledTimeService, and InvoiceTaxService.
 */
@Service
public class InvoiceService {

  private final InvoiceCreationService creationService;
  private final InvoiceTransitionService transitionService;
  private final InvoiceRenderingService renderingService;
  private final UnbilledTimeService unbilledTimeService;
  private final InvoiceValidationService invoiceValidationService;
  private final InvoiceRepository invoiceRepository;
  private final PaymentEventRepository paymentEventRepository;

  public InvoiceService(
      InvoiceCreationService creationService,
      InvoiceTransitionService transitionService,
      InvoiceRenderingService renderingService,
      UnbilledTimeService unbilledTimeService,
      InvoiceValidationService invoiceValidationService,
      InvoiceRepository invoiceRepository,
      PaymentEventRepository paymentEventRepository) {
    this.creationService = creationService;
    this.transitionService = transitionService;
    this.renderingService = renderingService;
    this.unbilledTimeService = unbilledTimeService;
    this.invoiceValidationService = invoiceValidationService;
    this.invoiceRepository = invoiceRepository;
    this.paymentEventRepository = paymentEventRepository;
  }

  // --- Validation ---

  @Transactional(readOnly = true)
  public List<InvoiceValidationService.ValidationCheck> validateInvoiceGeneration(
      UUID customerId, List<UUID> timeEntryIds, UUID templateId) {
    return invoiceValidationService.validateInvoiceGeneration(customerId, timeEntryIds, templateId);
  }

  // --- Creation & Draft Management ---

  @Transactional
  public InvoiceResponse createDraft(CreateInvoiceRequest request, UUID createdBy) {
    return creationService.createDraft(request, createdBy);
  }

  @Transactional
  public InvoiceResponse updateDraft(UUID invoiceId, UpdateInvoiceRequest request) {
    return creationService.updateDraft(invoiceId, request);
  }

  @Transactional
  public void deleteDraft(UUID invoiceId) {
    creationService.deleteDraft(invoiceId);
  }

  @Transactional
  public InvoiceResponse addLineItem(UUID invoiceId, AddLineItemRequest request) {
    return creationService.addLineItem(invoiceId, request);
  }

  @Transactional
  public InvoiceResponse updateLineItem(
      UUID invoiceId, UUID lineId, UpdateLineItemRequest request) {
    return creationService.updateLineItem(invoiceId, lineId, request);
  }

  @Transactional
  public void deleteLineItem(UUID invoiceId, UUID lineId) {
    creationService.deleteLineItem(invoiceId, lineId);
  }

  @Transactional
  public InvoiceResponse updateCustomFields(UUID invoiceId, Map<String, Object> customFields) {
    return creationService.updateCustomFields(invoiceId, customFields);
  }

  @Transactional
  public List<FieldDefinitionResponse> setFieldGroups(
      UUID invoiceId, List<UUID> appliedFieldGroups) {
    return creationService.setFieldGroups(invoiceId, appliedFieldGroups);
  }

  // --- Queries ---

  @Transactional(readOnly = true)
  public InvoiceResponse findById(UUID invoiceId) {
    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(
                () ->
                    new io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException(
                        "Invoice", invoiceId));
    return renderingService.buildResponse(invoice);
  }

  @Transactional(readOnly = true)
  public InvoiceResponse findByInvoiceNumber(String invoiceNumber) {
    var invoice =
        invoiceRepository
            .findByInvoiceNumber(invoiceNumber)
            .orElseThrow(
                () ->
                    io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException.withDetail(
                        "Invoice not found", "No invoice found with number " + invoiceNumber));
    return renderingService.buildResponse(invoice);
  }

  @Transactional(readOnly = true)
  public List<PaymentEventResponse> getPaymentEvents(UUID invoiceId) {
    invoiceRepository
        .findById(invoiceId)
        .orElseThrow(
            () ->
                new io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException(
                    "Invoice", invoiceId));

    return paymentEventRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId).stream()
        .map(PaymentEventResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<InvoiceResponse> findAll(UUID customerId, InvoiceStatus status, UUID projectId) {
    List<Invoice> invoices;

    if (customerId != null) {
      invoices = invoiceRepository.findByCustomerId(customerId);
    } else if (status != null) {
      invoices = invoiceRepository.findByStatus(status);
    } else if (projectId != null) {
      invoices = invoiceRepository.findByProjectId(projectId);
    } else {
      invoices = invoiceRepository.findAllOrdered();
    }

    return invoices.stream().map(renderingService::buildResponse).toList();
  }

  // --- Unbilled Time ---

  @Transactional(readOnly = true)
  public List<BillingRunDtos.CustomerUnbilledSummary> getUnbilledSummary(
      LocalDate periodFrom, LocalDate periodTo, String currency) {
    return unbilledTimeService.getUnbilledSummary(periodFrom, periodTo, currency);
  }

  public UnbilledTimeResponse getUnbilledTime(UUID customerId, LocalDate from, LocalDate to) {
    return unbilledTimeService.getUnbilledTime(customerId, from, to);
  }

  // --- Lifecycle Transitions ---

  @Transactional
  public InvoiceResponse approve(UUID invoiceId, UUID approvedBy) {
    return transitionService.approve(invoiceId, approvedBy);
  }

  @Transactional
  public InvoiceResponse send(UUID invoiceId, SendInvoiceRequest request) {
    return transitionService.send(invoiceId, request);
  }

  @Transactional
  public InvoiceResponse recordPayment(UUID invoiceId, String paymentReference) {
    return transitionService.recordPayment(invoiceId, paymentReference);
  }

  @Transactional
  public InvoiceResponse recordPayment(
      UUID invoiceId, String paymentReference, boolean fromWebhook) {
    return transitionService.recordPayment(invoiceId, paymentReference, fromWebhook);
  }

  @Transactional
  public InvoiceResponse refreshPaymentLink(UUID invoiceId) {
    return transitionService.refreshPaymentLink(invoiceId);
  }

  /**
   * Reverses a previously-recorded payment on an invoice. See {@link
   * InvoiceTransitionService#reversePayment(UUID, UUID)} for full semantics. Joins the caller's
   * transaction so it commits atomically with the trust-side reversal.
   */
  @Transactional
  public void reversePayment(UUID invoiceId, UUID paymentEventId) {
    transitionService.reversePayment(invoiceId, paymentEventId);
  }

  @Transactional
  public InvoiceResponse voidInvoice(UUID invoiceId) {
    return transitionService.voidInvoice(invoiceId);
  }

  // --- Preview ---

  @Transactional(readOnly = true)
  public String renderPreview(UUID invoiceId) {
    return renderingService.renderPreview(invoiceId);
  }
}
