package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import io.b2mash.b2b.b2bstrawman.customerbackend.dto.PortalTrustMatterSummary;
import io.b2mash.b2b.b2bstrawman.customerbackend.dto.PortalTrustStatementDocumentResponse;
import io.b2mash.b2b.b2bstrawman.customerbackend.dto.PortalTrustSummaryResponse;
import io.b2mash.b2b.b2bstrawman.customerbackend.dto.PortalTrustTransactionResponse;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalTrustReadModelRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Service backing the portal trust ledger endpoints. Encapsulates the module gate, the read-model
 * queries, and the response-record mapping — controllers stay thin per CLAUDE.md.
 */
@Service
public class PortalTrustLedgerService {

  private static final String MODULE_ID = "trust_accounting";

  /**
   * Upper bound on requested page size. Prevents a client sending {@code size=Integer.MAX_VALUE}
   * from forcing a full table scan.
   */
  private static final int MAX_PAGE_SIZE = 200;

  private final PortalTrustReadModelRepository portalTrustRepo;
  private final VerticalModuleGuard moduleGuard;

  public PortalTrustLedgerService(
      PortalTrustReadModelRepository portalTrustRepo, VerticalModuleGuard moduleGuard) {
    this.portalTrustRepo = portalTrustRepo;
    this.moduleGuard = moduleGuard;
  }

  public PortalTrustSummaryResponse getSummary(UUID customerId) {
    requireTrustAccountingEnabled();
    var balances = portalTrustRepo.findBalancesByCustomer(customerId);
    List<PortalTrustMatterSummary> matters =
        balances.stream()
            .map(
                b ->
                    new PortalTrustMatterSummary(
                        b.matterId(), b.currentBalance(), b.lastTransactionAt(), b.lastSyncedAt()))
            .toList();
    return new PortalTrustSummaryResponse(matters);
  }

  public Page<PortalTrustTransactionResponse> listMatterTransactions(
      UUID customerId, UUID matterId, Pageable pageable, Instant from, Instant to) {
    requireTrustAccountingEnabled();
    requireMatterVisibleToCustomer(customerId, matterId);

    int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
    long offsetLong = pageable.getOffset();
    if (offsetLong > Integer.MAX_VALUE) {
      // Unbounded page numbers can produce offsets beyond the JDBC int limit; surface a 400
      // instead of letting Math.toIntExact throw and turn into a 500.
      throw new IllegalArgumentException("Page offset exceeds supported range");
    }
    int offset = (int) offsetLong;

    long total = portalTrustRepo.countTransactions(customerId, matterId, from, to);
    List<PortalTrustTransactionResponse> content =
        portalTrustRepo.findTransactions(customerId, matterId, from, to, size, offset).stream()
            .map(
                v ->
                    new PortalTrustTransactionResponse(
                        v.id(),
                        v.transactionType(),
                        v.amount(),
                        v.runningBalance(),
                        v.occurredAt(),
                        v.description(),
                        v.reference()))
            .toList();
    return new PageImpl<>(content, pageable, total);
  }

  public List<PortalTrustStatementDocumentResponse> listStatementDocuments(
      UUID customerId, UUID matterId) {
    requireTrustAccountingEnabled();
    requireMatterVisibleToCustomer(customerId, matterId);
    // TODO(phase 67 — Statement-of-Account): once a STATEMENT template category (or an
    // equivalent publish flag on GeneratedDocument) exists, filter by it and surface the
    // signed download URLs here. Returning every generated document scoped to the matter
    // would leak firm-internal templates (engagement letters, internal memos) through the
    // portal — see code review on PR #1084. The endpoint remains so the portal contract
    // (200 + JSON array) is stable; it just returns an empty list until the category lands.
    return List.of();
  }

  // ── Gates ─────────────────────────────────────────────────────────────

  private void requireTrustAccountingEnabled() {
    if (!moduleGuard.isModuleEnabled(MODULE_ID)) {
      // 404 (not 403) per the portal's "module disabled looks like no resource" contract.
      throw ResourceNotFoundException.withDetail(
          "Trust ledger not available", "The trust ledger is not available for this organization");
    }
  }

  private void requireMatterVisibleToCustomer(UUID customerId, UUID matterId) {
    portalTrustRepo
        .findBalance(customerId, matterId)
        .orElseThrow(() -> new ResourceNotFoundException("Matter", matterId));
  }
}
