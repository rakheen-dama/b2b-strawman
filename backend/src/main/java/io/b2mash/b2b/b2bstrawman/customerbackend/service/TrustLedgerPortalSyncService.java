package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalTrustReadModelRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustDomainEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustTransactionApprovalEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Syncs firm-side trust ledger activity into the portal read-model (portal.portal_trust_balance,
 * portal.portal_trust_transaction). Listens to three firm-side events in {@link
 * TransactionPhase#AFTER_COMMIT} so only committed firm-side changes ever reach portal contacts:
 *
 * <ul>
 *   <li>{@link TrustTransactionApprovalEvent} with eventType "trust_transaction.approved" — upserts
 *       one transaction + recomputes the matter balance.
 *   <li>{@link TrustDomainEvent.InterestPosted} — fans out across every matter in the posting
 *       account (all trust transactions posted in the run share a trust account).
 *   <li>{@link TrustDomainEvent.ReconciliationCompleted} — same fan-out strategy; the
 *       reconciliation may have adjusted many matters' running balances.
 * </ul>
 *
 * <p>Raw firm-side descriptions pass through {@link PortalTrustDescriptionSanitiser} before
 * persistence — the portal schema only ever stores sanitised text (ADR-254).
 *
 * <p>{@link #backfillForTenant(String)} performs a full rebuild of the portal trust read-model for
 * a specific tenant. It follows the {@code PortalResyncService} pattern — resolves orgId → schema
 * via {@link OrgSchemaMappingRepository}, reads tenant data inside a {@link ScopedValue} binding,
 * and writes portal rows inside a portal-side transaction.
 */
@Service
public class TrustLedgerPortalSyncService {

  private static final Logger log = LoggerFactory.getLogger(TrustLedgerPortalSyncService.class);

  /** Backfill cap per matter — keeps the portal list view bounded for large histories. */
  private static final int BACKFILL_LIMIT_PER_MATTER = 50;

  private final PortalTrustReadModelRepository portalTrustRepo;
  private final TrustTransactionRepository trustTransactionRepository;
  private final PortalTrustDescriptionSanitiser sanitiser;
  private final OrgSchemaMappingRepository orgSchemaMappingRepository;
  private final TransactionTemplate portalTxTemplate;

  public TrustLedgerPortalSyncService(
      PortalTrustReadModelRepository portalTrustRepo,
      TrustTransactionRepository trustTransactionRepository,
      PortalTrustDescriptionSanitiser sanitiser,
      OrgSchemaMappingRepository orgSchemaMappingRepository,
      @Qualifier("portalTransactionManager") PlatformTransactionManager portalTxManager) {
    this.portalTrustRepo = portalTrustRepo;
    this.trustTransactionRepository = trustTransactionRepository;
    this.sanitiser = sanitiser;
    this.orgSchemaMappingRepository = orgSchemaMappingRepository;
    this.portalTxTemplate = new TransactionTemplate(portalTxManager);
  }

  // ── Event listeners ────────────────────────────────────────────────────

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTrustTransactionApproval(TrustTransactionApprovalEvent event) {
    if (!"trust_transaction.approved".equals(event.eventType())) {
      return;
    }
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            syncSingleTransaction(event.transactionId());
          } catch (Exception e) {
            log.warn(
                "Failed to sync approved trust transaction to portal txn={}",
                event.transactionId(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onInterestPosted(TrustDomainEvent.InterestPosted event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            syncForTrustAccount(event.trustAccountId());
          } catch (Exception e) {
            log.warn(
                "Failed to sync interest run to portal interestRun={} account={}",
                event.interestRunId(),
                event.trustAccountId(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onReconciliationCompleted(TrustDomainEvent.ReconciliationCompleted event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            syncForTrustAccount(event.trustAccountId());
          } catch (Exception e) {
            log.warn(
                "Failed to sync reconciliation to portal reconciliation={} account={}",
                event.reconciliationId(),
                event.trustAccountId(),
                e);
          }
        });
  }

  // ── Sync helpers (must run inside a bound tenant scope) ─────────────────

  private void syncSingleTransaction(UUID transactionId) {
    var txnOpt = trustTransactionRepository.findById(transactionId);
    if (txnOpt.isEmpty()) {
      log.warn("Approved trust transaction not found during portal sync — txn={}", transactionId);
      return;
    }
    var txn = txnOpt.get();
    if (!hasPortalScope(txn)) {
      return;
    }
    upsertTransactionAndBalance(txn);
  }

  /**
   * Recomputes the portal read-model for every {@code (customerId, projectId)} pair that has at
   * least one non-reversal trust transaction in the given account. Used by the interest-posted and
   * reconciliation-completed fan-out handlers.
   */
  private void syncForTrustAccount(UUID trustAccountId) {
    var statuses = List.of("RECORDED", "APPROVED");
    Set<UUID> seenMatters = new HashSet<>();
    for (var status : statuses) {
      for (var txn :
          trustTransactionRepository.findByStatusAndTrustAccountId(status, trustAccountId)) {
        if (!hasPortalScope(txn)) {
          continue;
        }
        if (!seenMatters.add(txn.getProjectId())) {
          continue; // Only recompute each matter once per fan-out.
        }
        recomputeMatter(txn.getCustomerId(), txn.getProjectId(), trustAccountId);
      }
    }
  }

  private void upsertTransactionAndBalance(TrustTransaction txn) {
    BigDecimal balance = trustTransactionRepository.calculateBalanceByProjectId(txn.getProjectId());
    Instant occurredAt = deriveOccurredAt(txn);
    portalTrustRepo.upsertTransaction(
        txn.getId(),
        txn.getCustomerId(),
        txn.getProjectId(),
        txn.getTransactionType(),
        txn.getAmount(),
        balance,
        occurredAt,
        sanitiser.sanitise(txn.getDescription(), txn.getTransactionType(), txn.getReference()),
        txn.getReference());
    portalTrustRepo.upsertBalance(txn.getCustomerId(), txn.getProjectId(), balance, occurredAt);
  }

  /**
   * Replays every non-reversal {@code (RECORDED|APPROVED)} trust transaction on {@code (customerId,
   * matterId)} from the account and upserts the matching portal rows. Used by the fan-out event
   * handlers and by the backfill.
   */
  private void recomputeMatter(UUID customerId, UUID matterId, UUID trustAccountId) {
    var transactions =
        trustTransactionRepository.findByCustomerIdAndTrustAccountIdOrderByTransactionDateDesc(
            customerId, trustAccountId);
    Instant latest = null;
    for (var txn : transactions) {
      if (!matterId.equals(txn.getProjectId())) {
        continue;
      }
      if (!isPortalEligible(txn)) {
        continue;
      }
      Instant occurredAt = deriveOccurredAt(txn);
      if (latest == null || occurredAt.isAfter(latest)) {
        latest = occurredAt;
      }
      portalTrustRepo.upsertTransaction(
          txn.getId(),
          customerId,
          matterId,
          txn.getTransactionType(),
          txn.getAmount(),
          trustTransactionRepository.calculateBalanceByProjectId(matterId),
          occurredAt,
          sanitiser.sanitise(txn.getDescription(), txn.getTransactionType(), txn.getReference()),
          txn.getReference());
    }
    BigDecimal balance = trustTransactionRepository.calculateBalanceByProjectId(matterId);
    portalTrustRepo.upsertBalance(customerId, matterId, balance, latest);
  }

  // ── Backfill ───────────────────────────────────────────────────────────

  /**
   * Rebuilds the portal trust read-model for a tenant. Intended to be called on module activation
   * (via {@code POST /internal/portal-resync/*}) or when repairing drift. Wipes and rewrites the
   * portal rows for every customer that has ledger activity.
   */
  public BackfillResult backfillForTenant(String orgId) {
    var mapping =
        orgSchemaMappingRepository
            .findByClerkOrgId(orgId)
            .orElseThrow(
                () ->
                    ResourceNotFoundException.withDetail(
                        "Organization not found", "No organization found with orgId " + orgId));

    String schema = mapping.getSchemaName();
    log.info("Starting portal trust backfill for org={} schema={}", orgId, schema);

    Map<UUID, MatterRollup> rollups = new LinkedHashMap<>();

    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .run(
            () -> {
              // Build a complete per-matter view from every non-reversal trust txn in the tenant.
              // We fetch by status because there is no single "findAllPortalEligible" method.
              for (var status : List.of("RECORDED", "APPROVED")) {
                for (var txn : trustTransactionRepository.findAll()) {
                  if (!status.equals(txn.getStatus())) {
                    continue;
                  }
                  if (!hasPortalScope(txn) || !isPortalEligible(txn)) {
                    continue;
                  }
                  UUID matterId = txn.getProjectId();
                  rollups
                      .computeIfAbsent(matterId, id -> new MatterRollup(id, txn.getCustomerId()))
                      .add(txn);
                }
              }

              // Recompute balances inside tenant scope (calculateBalanceByProjectId needs it).
              for (var rollup : rollups.values()) {
                rollup.balance =
                    trustTransactionRepository.calculateBalanceByProjectId(rollup.matterId);
              }
            });

    // Write portal rows inside a portal-side transaction.
    Integer txnCount =
        portalTxTemplate.execute(
            status -> {
              int written = 0;
              for (var rollup : rollups.values()) {
                // Truncate each matter's history to the latest N and write in chronological order
                // so the client-side list reflects the full retained window.
                List<TrustTransaction> tailHistory = rollup.tailHistory(BACKFILL_LIMIT_PER_MATTER);
                Instant latestOccurredAt = null;
                for (var txn : tailHistory) {
                  Instant occurredAt = deriveOccurredAt(txn);
                  if (latestOccurredAt == null || occurredAt.isAfter(latestOccurredAt)) {
                    latestOccurredAt = occurredAt;
                  }
                  portalTrustRepo.upsertTransaction(
                      txn.getId(),
                      rollup.customerId,
                      rollup.matterId,
                      txn.getTransactionType(),
                      txn.getAmount(),
                      rollup.balance,
                      occurredAt,
                      sanitiser.sanitise(
                          txn.getDescription(), txn.getTransactionType(), txn.getReference()),
                      txn.getReference());
                  written++;
                }
                portalTrustRepo.upsertBalance(
                    rollup.customerId, rollup.matterId, rollup.balance, latestOccurredAt);
              }
              return written;
            });

    log.info(
        "Portal trust backfill complete for org={} matters={} transactions={}",
        orgId,
        rollups.size(),
        txnCount);
    return new BackfillResult(rollups.size(), txnCount == null ? 0 : txnCount);
  }

  // ── Supporting types ──────────────────────────────────────────────────

  public record BackfillResult(int mattersProjected, int transactionsProjected) {}

  /** Accumulates trust transactions for a single matter while building a backfill view. */
  private static final class MatterRollup {
    final UUID matterId;
    final UUID customerId;
    final java.util.ArrayList<TrustTransaction> transactions = new java.util.ArrayList<>();
    BigDecimal balance = BigDecimal.ZERO;

    MatterRollup(UUID matterId, UUID customerId) {
      this.matterId = matterId;
      this.customerId = customerId;
    }

    void add(TrustTransaction txn) {
      transactions.add(txn);
    }

    List<TrustTransaction> tailHistory(int limit) {
      transactions.sort(
          (a, b) -> {
            int byDate = b.getTransactionDate().compareTo(a.getTransactionDate());
            if (byDate != 0) {
              return byDate;
            }
            Instant aCreated = a.getCreatedAt() == null ? Instant.EPOCH : a.getCreatedAt();
            Instant bCreated = b.getCreatedAt() == null ? Instant.EPOCH : b.getCreatedAt();
            return bCreated.compareTo(aCreated);
          });
      if (transactions.size() <= limit) {
        return List.copyOf(transactions);
      }
      return List.copyOf(transactions.subList(0, limit));
    }
  }

  // ── Pure helpers ──────────────────────────────────────────────────────

  /**
   * Portal surfaces are customer/matter scoped. A trust transaction must carry both to be
   * surfaceable — e.g., some inter-account FEE_TRANSFER rows carry a customer but no project.
   */
  private static boolean hasPortalScope(TrustTransaction txn) {
    return txn.getCustomerId() != null && txn.getProjectId() != null;
  }

  /**
   * REVERSAL rows are not surfaced — per the firm-side repository convention they do not affect the
   * per-matter balance and would confuse portal viewers.
   */
  private static boolean isPortalEligible(TrustTransaction txn) {
    if (txn.getTransactionType() == null) {
      return false;
    }
    return !"REVERSAL".equals(txn.getTransactionType());
  }

  private static Instant deriveOccurredAt(TrustTransaction txn) {
    if (txn.getApprovedAt() != null) {
      return txn.getApprovedAt();
    }
    if (txn.getCreatedAt() != null) {
      return txn.getCreatedAt();
    }
    // Fall back to transaction date at UTC midnight — transactionDate is always non-null.
    return txn.getTransactionDate().atStartOfDay(ZoneOffset.UTC).toInstant();
  }

  private void handleInTenantScope(String tenantId, String orgId, Runnable action) {
    if (tenantId == null) {
      log.warn("Trust portal sync event received without tenantId — skipping");
      return;
    }
    var carrier = ScopedValue.where(RequestScopes.TENANT_ID, tenantId);
    if (orgId != null) {
      carrier = carrier.where(RequestScopes.ORG_ID, orgId);
    }
    carrier.run(action);
  }
}
