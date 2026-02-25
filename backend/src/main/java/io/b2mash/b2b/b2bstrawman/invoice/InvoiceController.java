package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.SetFieldGroupsRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.AddLineItemRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.CreateInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.InvoiceResponse;
import io.b2mash.b2b.b2bstrawman.invoice.dto.RecordPaymentRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UpdateCustomFieldsRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UpdateInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UpdateLineItemRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

  public InvoiceController(InvoiceService invoiceService) {
    this.invoiceService = invoiceService;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<InvoiceResponse> createDraft(
      @Valid @RequestBody CreateInvoiceRequest request) {
    UUID createdBy = RequestScopes.requireMemberId();
    var response = invoiceService.createDraft(request, createdBy);
    return ResponseEntity.created(URI.create("/api/invoices/" + response.id())).body(response);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<InvoiceResponse> updateDraft(
      @PathVariable UUID id, @Valid @RequestBody UpdateInvoiceRequest request) {
    return ResponseEntity.ok(invoiceService.updateDraft(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> deleteDraft(@PathVariable UUID id) {
    invoiceService.deleteDraft(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<InvoiceResponse> getInvoice(@PathVariable UUID id) {
    return ResponseEntity.ok(invoiceService.findById(id));
  }

  @GetMapping("/{id}/preview")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<String> preview(@PathVariable UUID id) {
    String html = invoiceService.renderPreview(id);
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<InvoiceResponse>> listInvoices(
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false) InvoiceStatus status,
      @RequestParam(required = false) UUID projectId) {
    return ResponseEntity.ok(invoiceService.findAll(customerId, status, projectId));
  }

  // --- Custom fields ---

  @PutMapping("/{id}/custom-fields")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<InvoiceResponse> updateCustomFields(
      @PathVariable UUID id, @Valid @RequestBody UpdateCustomFieldsRequest request) {
    return ResponseEntity.ok(invoiceService.updateCustomFields(id, request.customFields()));
  }

  @PutMapping("/{id}/field-groups")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<FieldDefinitionResponse>> setFieldGroups(
      @PathVariable UUID id, @Valid @RequestBody SetFieldGroupsRequest request) {
    return ResponseEntity.ok(invoiceService.setFieldGroups(id, request.appliedFieldGroups()));
  }

  // --- Line item CRUD ---

  @PostMapping("/{id}/lines")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<InvoiceResponse> addLineItem(
      @PathVariable UUID id, @Valid @RequestBody AddLineItemRequest request) {
    var response = invoiceService.addLineItem(id, request);
    return ResponseEntity.created(URI.create("/api/invoices/" + id)).body(response);
  }

  @PutMapping("/{id}/lines/{lineId}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<InvoiceResponse> updateLineItem(
      @PathVariable UUID id,
      @PathVariable UUID lineId,
      @Valid @RequestBody UpdateLineItemRequest request) {
    return ResponseEntity.ok(invoiceService.updateLineItem(id, lineId, request));
  }

  @DeleteMapping("/{id}/lines/{lineId}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> deleteLineItem(@PathVariable UUID id, @PathVariable UUID lineId) {
    invoiceService.deleteLineItem(id, lineId);
    return ResponseEntity.noContent().build();
  }

  // --- Lifecycle transitions ---

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<InvoiceResponse> approveInvoice(@PathVariable UUID id) {
    UUID approvedBy = RequestScopes.requireMemberId();
    return ResponseEntity.ok(invoiceService.approve(id, approvedBy));
  }

  @PostMapping("/{id}/send")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<InvoiceResponse> sendInvoice(@PathVariable UUID id) {
    return ResponseEntity.ok(invoiceService.send(id));
  }

  @PostMapping("/{id}/payment")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<InvoiceResponse> recordPayment(
      @PathVariable UUID id, @RequestBody(required = false) RecordPaymentRequest request) {
    String paymentReference = request != null ? request.paymentReference() : null;
    return ResponseEntity.ok(invoiceService.recordPayment(id, paymentReference));
  }

  @PostMapping("/{id}/void")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<InvoiceResponse> voidInvoice(@PathVariable UUID id) {
    return ResponseEntity.ok(invoiceService.voidInvoice(id));
  }
}
