package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.invoice.dto.AddLineItemRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.CreateInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.InvoiceResponse;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UpdateInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UpdateLineItemRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
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

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<InvoiceResponse>> listInvoices(
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false) InvoiceStatus status,
      @RequestParam(required = false) UUID projectId) {
    return ResponseEntity.ok(invoiceService.findAll(customerId, status, projectId));
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
}
