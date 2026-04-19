package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.SetFieldGroupsRequest;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceValidationService.ValidationCheck;
import io.b2mash.b2b.b2bstrawman.invoice.dto.AddDisbursementLinesRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.AddLineItemRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.CreateInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.InvoiceResponse;
import io.b2mash.b2b.b2bstrawman.invoice.dto.PaymentEventResponse;
import io.b2mash.b2b.b2bstrawman.invoice.dto.RecordPaymentRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.SendInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UpdateCustomFieldsRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UpdateInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UpdateLineItemRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.ValidateGenerationRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

  private final InvoiceService invoiceService;
  private final InvoiceCreationService invoiceCreationService;

  public InvoiceController(
      InvoiceService invoiceService, InvoiceCreationService invoiceCreationService) {
    this.invoiceService = invoiceService;
    this.invoiceCreationService = invoiceCreationService;
  }

  @PostMapping
  @RequiresCapability("INVOICING")
  public ResponseEntity<InvoiceResponse> createDraft(
      @Valid @RequestBody CreateInvoiceRequest request) {
    UUID createdBy = RequestScopes.requireMemberId();
    var response = invoiceService.createDraft(request, createdBy);
    return ResponseEntity.created(URI.create("/api/invoices/" + response.id())).body(response);
  }

  @PutMapping("/{id}")
  @RequiresCapability("INVOICING")
  public ResponseEntity<InvoiceResponse> updateDraft(
      @PathVariable UUID id, @Valid @RequestBody UpdateInvoiceRequest request) {
    return ResponseEntity.ok(invoiceService.updateDraft(id, request));
  }

  @DeleteMapping("/{id}")
  @RequiresCapability("INVOICING")
  public ResponseEntity<Void> deleteDraft(@PathVariable UUID id) {
    invoiceService.deleteDraft(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}")
  @RequiresCapability("INVOICING")
  public ResponseEntity<InvoiceResponse> getInvoice(@PathVariable UUID id) {
    return ResponseEntity.ok(invoiceService.findById(id));
  }

  @GetMapping("/{id}/preview")
  @RequiresCapability("INVOICING")
  public ResponseEntity<String> preview(@PathVariable UUID id) {
    String html = invoiceService.renderPreview(id);
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
  }

  @GetMapping("/unbilled-summary")
  @RequiresCapability("INVOICING")
  public ResponseEntity<List<BillingRunDtos.CustomerUnbilledSummary>> getUnbilledSummary(
      @RequestParam LocalDate periodFrom,
      @RequestParam LocalDate periodTo,
      @RequestParam String currency) {
    return ResponseEntity.ok(invoiceService.getUnbilledSummary(periodFrom, periodTo, currency));
  }

  @GetMapping
  @RequiresCapability("INVOICING")
  public ResponseEntity<List<InvoiceResponse>> listInvoices(
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false) InvoiceStatus status,
      @RequestParam(required = false) UUID projectId) {
    return ResponseEntity.ok(invoiceService.findAll(customerId, status, projectId));
  }

  // --- Custom fields ---

  @PutMapping("/{id}/custom-fields")
  @RequiresCapability("INVOICING")
  public ResponseEntity<InvoiceResponse> updateCustomFields(
      @PathVariable UUID id, @Valid @RequestBody UpdateCustomFieldsRequest request) {
    return ResponseEntity.ok(invoiceService.updateCustomFields(id, request.customFields()));
  }

  @PutMapping("/{id}/field-groups")
  @RequiresCapability("INVOICING")
  public ResponseEntity<List<FieldDefinitionResponse>> setFieldGroups(
      @PathVariable UUID id, @Valid @RequestBody SetFieldGroupsRequest request) {
    return ResponseEntity.ok(invoiceService.setFieldGroups(id, request.appliedFieldGroups()));
  }

  // --- Line item CRUD ---

  @PostMapping("/{id}/lines")
  @RequiresCapability("INVOICING")
  public ResponseEntity<InvoiceResponse> addLineItem(
      @PathVariable UUID id, @Valid @RequestBody AddLineItemRequest request) {
    var response = invoiceService.addLineItem(id, request);
    return ResponseEntity.created(URI.create("/api/invoices/" + id)).body(response);
  }

  @PostMapping("/{id}/disbursement-lines")
  @RequiresCapability("INVOICING")
  public ResponseEntity<InvoiceResponse> addDisbursementLines(
      @PathVariable UUID id, @Valid @RequestBody AddDisbursementLinesRequest request) {
    return ResponseEntity.ok(
        invoiceCreationService.appendDisbursementLinesToInvoice(id, request.disbursementIds()));
  }

  @PutMapping("/{id}/lines/{lineId}")
  @RequiresCapability("INVOICING")
  public ResponseEntity<InvoiceResponse> updateLineItem(
      @PathVariable UUID id,
      @PathVariable UUID lineId,
      @Valid @RequestBody UpdateLineItemRequest request) {
    return ResponseEntity.ok(invoiceService.updateLineItem(id, lineId, request));
  }

  @DeleteMapping("/{id}/lines/{lineId}")
  @RequiresCapability("INVOICING")
  public ResponseEntity<Void> deleteLineItem(@PathVariable UUID id, @PathVariable UUID lineId) {
    invoiceService.deleteLineItem(id, lineId);
    return ResponseEntity.noContent().build();
  }

  // --- Validation ---

  @PostMapping("/validate-generation")
  @RequiresCapability("INVOICING")
  public ResponseEntity<List<ValidationCheck>> validateGeneration(
      @Valid @RequestBody ValidateGenerationRequest request) {
    return ResponseEntity.ok(
        invoiceService.validateInvoiceGeneration(
            request.customerId(), request.timeEntryIds(), request.templateId()));
  }

  // --- Lifecycle transitions ---

  @PostMapping("/{id}/approve")
  @RequiresCapability("INVOICING")
  public ResponseEntity<InvoiceResponse> approveInvoice(@PathVariable UUID id) {
    UUID approvedBy = RequestScopes.requireMemberId();
    return ResponseEntity.ok(invoiceService.approve(id, approvedBy));
  }

  @PostMapping("/{id}/send")
  @RequiresCapability("INVOICING")
  public ResponseEntity<InvoiceResponse> sendInvoice(
      @PathVariable UUID id, @RequestBody(required = false) SendInvoiceRequest request) {
    return ResponseEntity.ok(invoiceService.send(id, request));
  }

  @PostMapping("/{id}/payment")
  @RequiresCapability("INVOICING")
  public ResponseEntity<InvoiceResponse> recordPayment(
      @PathVariable UUID id, @RequestBody(required = false) RecordPaymentRequest request) {
    String paymentReference = request != null ? request.paymentReference() : null;
    return ResponseEntity.ok(invoiceService.recordPayment(id, paymentReference));
  }

  @GetMapping("/{id}/payment-events")
  @RequiresCapability("INVOICING")
  public ResponseEntity<List<PaymentEventResponse>> getPaymentEvents(@PathVariable UUID id) {
    return ResponseEntity.ok(invoiceService.getPaymentEvents(id));
  }

  @PostMapping("/{id}/refresh-payment-link")
  @RequiresCapability("INVOICING")
  public ResponseEntity<InvoiceResponse> refreshPaymentLink(@PathVariable UUID id) {
    return ResponseEntity.ok(invoiceService.refreshPaymentLink(id));
  }

  @PostMapping("/{id}/void")
  @RequiresCapability("INVOICING")
  public ResponseEntity<InvoiceResponse> voidInvoice(@PathVariable UUID id) {
    return ResponseEntity.ok(invoiceService.voidInvoice(id));
  }
}
