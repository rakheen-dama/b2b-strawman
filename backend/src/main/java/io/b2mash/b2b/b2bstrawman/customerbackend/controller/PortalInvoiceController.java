package io.b2mash.b2b.b2bstrawman.customerbackend.controller;

import io.b2mash.b2b.b2bstrawman.customerbackend.service.PortalReadModelService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portal invoice endpoints. Provides read-only access to invoices for the authenticated customer.
 * All endpoints require a valid portal JWT (enforced by CustomerAuthFilter).
 */
@RestController
@RequestMapping("/portal/invoices")
public class PortalInvoiceController {

  private final PortalReadModelService portalReadModelService;

  public PortalInvoiceController(PortalReadModelService portalReadModelService) {
    this.portalReadModelService = portalReadModelService;
  }

  /** Lists all invoices for the authenticated customer. */
  @GetMapping
  public ResponseEntity<List<PortalInvoiceResponse>> listInvoices() {
    UUID customerId = RequestScopes.requireCustomerId();
    String orgId = RequestScopes.requireOrgId();
    var invoices = portalReadModelService.listInvoices(orgId, customerId);

    var response =
        invoices.stream()
            .map(
                i ->
                    new PortalInvoiceResponse(
                        i.id(),
                        i.invoiceNumber(),
                        i.status(),
                        i.issueDate(),
                        i.dueDate(),
                        i.total(),
                        i.currency()))
            .toList();

    return ResponseEntity.ok(response);
  }

  /** Returns invoice detail including line items. */
  @GetMapping("/{id}")
  public ResponseEntity<PortalInvoiceDetailResponse> getInvoiceDetail(@PathVariable UUID id) {
    UUID customerId = RequestScopes.requireCustomerId();
    String orgId = RequestScopes.requireOrgId();
    var detail = portalReadModelService.getInvoiceDetail(id, customerId, orgId);

    var lines =
        detail.lines().stream()
            .map(
                l ->
                    new PortalInvoiceLineResponse(
                        l.id(),
                        l.description(),
                        l.quantity(),
                        l.unitPrice(),
                        l.amount(),
                        l.sortOrder()))
            .toList();

    var invoice = detail.invoice();
    return ResponseEntity.ok(
        new PortalInvoiceDetailResponse(
            invoice.id(),
            invoice.invoiceNumber(),
            invoice.status(),
            invoice.issueDate(),
            invoice.dueDate(),
            invoice.subtotal(),
            invoice.taxAmount(),
            invoice.total(),
            invoice.currency(),
            invoice.notes(),
            lines));
  }

  /** Returns the current payment status for an invoice (used by payment success page to poll). */
  @GetMapping("/{id}/payment-status")
  public ResponseEntity<PaymentStatusResponse> getPaymentStatus(@PathVariable UUID id) {
    UUID customerId = RequestScopes.requireCustomerId();
    String orgId = RequestScopes.requireOrgId();
    var status = portalReadModelService.getPaymentStatus(id, customerId, orgId);
    return ResponseEntity.ok(status);
  }

  /** Returns a presigned download URL for the most recent PDF generated for this invoice. */
  @GetMapping("/{id}/download")
  public ResponseEntity<PortalDownloadResponse> downloadInvoice(@PathVariable UUID id) {
    UUID customerId = RequestScopes.requireCustomerId();
    String orgId = RequestScopes.requireOrgId();
    String downloadUrl = portalReadModelService.getInvoiceDownloadUrl(id, customerId, orgId);
    return ResponseEntity.ok(new PortalDownloadResponse(downloadUrl));
  }

  // --- DTOs ---

  public record PortalInvoiceResponse(
      UUID id,
      String invoiceNumber,
      String status,
      LocalDate issueDate,
      LocalDate dueDate,
      BigDecimal total,
      String currency) {}

  public record PortalInvoiceDetailResponse(
      UUID id,
      String invoiceNumber,
      String status,
      LocalDate issueDate,
      LocalDate dueDate,
      BigDecimal subtotal,
      BigDecimal taxAmount,
      BigDecimal total,
      String currency,
      String notes,
      List<PortalInvoiceLineResponse> lines) {}

  public record PortalInvoiceLineResponse(
      UUID id,
      String description,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal amount,
      int sortOrder) {}

  public record PortalDownloadResponse(String downloadUrl) {}
}
