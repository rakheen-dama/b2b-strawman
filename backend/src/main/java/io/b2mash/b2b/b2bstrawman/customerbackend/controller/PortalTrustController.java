package io.b2mash.b2b.b2bstrawman.customerbackend.controller;

import io.b2mash.b2b.b2bstrawman.customerbackend.dto.PortalTrustStatementDocumentResponse;
import io.b2mash.b2b.b2bstrawman.customerbackend.dto.PortalTrustSummaryResponse;
import io.b2mash.b2b.b2bstrawman.customerbackend.dto.PortalTrustTransactionResponse;
import io.b2mash.b2b.b2bstrawman.customerbackend.service.PortalTrustLedgerService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portal trust ledger endpoints. All routes require a valid portal JWT (enforced by {@code
 * CustomerAuthFilter} on {@code /portal/*}) and are module-gated inside the service — if the
 * authenticated customer's tenant does not have {@code trust_accounting} enabled, every endpoint
 * returns 404 (ADR-254: portal surfaces hide disabled modules).
 */
@RestController
@RequestMapping("/portal/trust")
public class PortalTrustController {

  private final PortalTrustLedgerService portalTrustLedgerService;

  public PortalTrustController(PortalTrustLedgerService portalTrustLedgerService) {
    this.portalTrustLedgerService = portalTrustLedgerService;
  }

  /** Returns a list of per-matter balance snapshots for the authenticated customer. */
  @GetMapping("/summary")
  public ResponseEntity<PortalTrustSummaryResponse> getSummary() {
    UUID customerId = RequestScopes.requireCustomerId();
    return ResponseEntity.ok(portalTrustLedgerService.getSummary(customerId));
  }

  /** Returns the paginated trust transaction history for a single matter. */
  @GetMapping("/matters/{matterId}/transactions")
  public ResponseEntity<Page<PortalTrustTransactionResponse>> listMatterTransactions(
      @PathVariable UUID matterId,
      Pageable pageable,
      @RequestParam(value = "from", required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(value = "to", required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to) {
    UUID customerId = RequestScopes.requireCustomerId();
    return ResponseEntity.ok(
        portalTrustLedgerService.listMatterTransactions(customerId, matterId, pageable, from, to));
  }

  /** Returns statement documents attached to a matter. */
  @GetMapping("/matters/{matterId}/statement-documents")
  public ResponseEntity<List<PortalTrustStatementDocumentResponse>> listMatterStatementDocuments(
      @PathVariable UUID matterId) {
    UUID customerId = RequestScopes.requireCustomerId();
    return ResponseEntity.ok(portalTrustLedgerService.listStatementDocuments(customerId, matterId));
  }
}
