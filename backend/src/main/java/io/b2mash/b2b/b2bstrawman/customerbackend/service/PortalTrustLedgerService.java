package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import io.b2mash.b2b.b2bstrawman.customerbackend.controller.PortalTrustController.PortalTrustMatterSummary;
import io.b2mash.b2b.b2bstrawman.customerbackend.controller.PortalTrustController.PortalTrustStatementDocumentResponse;
import io.b2mash.b2b.b2bstrawman.customerbackend.controller.PortalTrustController.PortalTrustSummaryResponse;
import io.b2mash.b2b.b2bstrawman.customerbackend.controller.PortalTrustController.PortalTrustTransactionResponse;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalTrustReadModelRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import java.time.Duration;
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
  private static final Duration DOWNLOAD_TTL = Duration.ofHours(1);

  private final PortalTrustReadModelRepository portalTrustRepo;
  private final VerticalModuleGuard moduleGuard;
  private final GeneratedDocumentRepository generatedDocumentRepository;
  private final StorageService storageService;

  public PortalTrustLedgerService(
      PortalTrustReadModelRepository portalTrustRepo,
      VerticalModuleGuard moduleGuard,
      GeneratedDocumentRepository generatedDocumentRepository,
      StorageService storageService) {
    this.portalTrustRepo = portalTrustRepo;
    this.moduleGuard = moduleGuard;
    this.generatedDocumentRepository = generatedDocumentRepository;
    this.storageService = storageService;
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

    int size = pageable.getPageSize();
    int offset = Math.toIntExact(pageable.getOffset());

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
    // TODO(phase 68): once a STATEMENT template category exists, filter by that category —
    // see template.TemplateCategory. For now any generated doc scoped to the matter (PROJECT) is
    // surfaced and the portal caller decides display.
    var generated =
        generatedDocumentRepository.findByPrimaryEntityTypeAndPrimaryEntityIdOrderByGeneratedAtDesc(
            TemplateEntityType.PROJECT, matterId);
    return generated.stream()
        .map(
            gd ->
                new PortalTrustStatementDocumentResponse(
                    gd.getId(),
                    gd.getFileName(),
                    gd.getGeneratedAt(),
                    storageService.generateDownloadUrl(gd.getS3Key(), DOWNLOAD_TTL).url()))
        .toList();
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
