package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import io.b2mash.b2b.b2bstrawman.customerbackend.dto.PortalTrustMatterSummary;
import io.b2mash.b2b.b2bstrawman.customerbackend.dto.PortalTrustMovementResponse;
import io.b2mash.b2b.b2bstrawman.customerbackend.dto.PortalTrustStatementDocumentResponse;
import io.b2mash.b2b.b2bstrawman.customerbackend.dto.PortalTrustSummaryResponse;
import io.b2mash.b2b.b2bstrawman.customerbackend.dto.PortalTrustTransactionResponse;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalTrustReadModelRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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

  /**
   * Defensive bounds for {@code GET /portal/trust/movements?limit=N}. The home tile asks for 1; an
   * activity feed could ask for 50. Anything beyond {@value #MAX_MOVEMENTS_LIMIT} is almost
   * certainly a misuse.
   */
  private static final int MIN_MOVEMENTS_LIMIT = 1;

  private static final int MAX_MOVEMENTS_LIMIT = 100;

  /**
   * Default display currency for trust-ledger movements. The portal read-model does not store
   * currency per row (ADR-253) — trust accounting is currently only enabled on the legal-za
   * vertical, which seeds {@code ZAR} as the firm currency. When the module is opened to other
   * verticals, this should be sourced from {@code OrgSettings} via {@code VerticalModuleGuard} or a
   * per-row column on {@code portal_trust_transaction}.
   */
  private static final String DEFAULT_TRUST_CURRENCY = "ZAR";

  private final PortalTrustReadModelRepository portalTrustRepo;
  private final ProjectRepository projectRepository;
  private final VerticalModuleGuard moduleGuard;

  public PortalTrustLedgerService(
      PortalTrustReadModelRepository portalTrustRepo,
      ProjectRepository projectRepository,
      VerticalModuleGuard moduleGuard) {
    this.portalTrustRepo = portalTrustRepo;
    this.projectRepository = projectRepository;
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
    Pageable effectivePageable = PageRequest.of(pageable.getPageNumber(), size, pageable.getSort());
    long offsetLong = effectivePageable.getOffset();
    if (offsetLong > Integer.MAX_VALUE) {
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
    return new PageImpl<>(content, effectivePageable, total);
  }

  /**
   * Returns the most recent trust movements across every matter belonging to {@code customerId},
   * newest-first, capped at {@code limit} rows (clamped to {@link #MIN_MOVEMENTS_LIMIT}/{@link
   * #MAX_MOVEMENTS_LIMIT}). Powers {@code GET /portal/trust/movements} — the portal home tile.
   *
   * <p>Tenant scoping: the firm-side {@link ProjectRepository} runs under the contact's tenant
   * schema (bound by {@code CustomerAuthFilter}), and the portal repo filter on {@code customer_id}
   * enforces customer-level isolation. Together they guarantee a contact only sees their own
   * customer's transactions, and only with matter names from their tenant.
   */
  public List<PortalTrustMovementResponse> listRecentMovements(UUID customerId, int limit) {
    requireTrustAccountingEnabled();
    int safeLimit = Math.max(MIN_MOVEMENTS_LIMIT, Math.min(limit, MAX_MOVEMENTS_LIMIT));
    var transactions = portalTrustRepo.findRecentTransactionsByCustomer(customerId, safeLimit);
    if (transactions.isEmpty()) {
      return List.of();
    }
    var matterIds = transactions.stream().map(t -> t.matterId()).distinct().toList();
    Map<UUID, String> matterNames =
        projectRepository.findByIdIn(matterIds).stream()
            .collect(Collectors.toMap(Project::getId, Project::getName));
    return transactions.stream()
        .map(
            t ->
                new PortalTrustMovementResponse(
                    t.id(),
                    t.transactionType(),
                    t.amount(),
                    DEFAULT_TRUST_CURRENCY,
                    t.occurredAt(),
                    matterNames.get(t.matterId()),
                    t.description()))
        .toList();
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
        // GAP-L-55: match the sibling portal endpoints' "Project not found" wording. The
        // tenant-side entity is a matter in legal-za terminology, but the portal API uses the
        // canonical "Project" resource name to avoid terminology drift across the portal contract.
        .orElseThrow(() -> new ResourceNotFoundException("Project", matterId));
  }
}
