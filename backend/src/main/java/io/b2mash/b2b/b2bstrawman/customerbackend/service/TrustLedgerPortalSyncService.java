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

  /**
   * Sentinel actor id used when a system operation (the tenant-wide backfill) binds {@link
   * RequestScopes#MEMBER_ID} — downstream listeners that consume the scoped value to tag audits or
   * logs still get a non-null UUID, but it never matches a real member row.
   */
  private static final UUID SYSTEM_ACTOR_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000000");

  /**
   * Firm-side transaction statuses that produce portal-visible rows. {@code RECORDED} covers
   * auto-approved DEPOSIT/INTEREST_CREDIT and pre-approval PAYMENT/FEE_TRANSFER/REFUND rows; {@code
   * APPROVED} covers the post-approval rows.
   */
  private static final List<String> PORTAL_STATUSES = List.of("RECORDED", "APPROVED");

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
            log.error(
                "Portal trust sync failed for event=trust_transaction.approved tenant={} txn={}"
                    + " account={}",
                event.tenantId(),
                event.transactionId(),
                event.trustAccountId(),
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
            log.error(
                "Portal trust sync failed for event=interest_posted tenant={} interestRun={}"
                    + " account={}",
                event.tenantId(),
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
            log.error(
                "Portal trust sync failed for event=reconciliation_completed tenant={}"
                    + " reconciliation={} account={}",
                event.tenantId(),
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
    // Upsert this row plus replay the matter so the new transaction's running_balance and every
    // earlier row's running_balance stay consistent with the progressive sum.
    recomputeMatter(txn.getCustomerId(), txn.getProjectId());
  }

  /**
   * Recomputes the portal read-model for every {@code (customerId, projectId)} pair that has at
   * least one non-reversal trust transaction in the given account. Used by the interest-posted and
   * reconciliation-completed fan-out handlers.
   */
  private void syncForTrustAccount(UUID trustAccountId) {
    Set<UUID> seenMatters = new HashSet<>();
    for (var status : PORTAL_STATUSES) {
      for (var txn :
          trustTransactionRepository.findByStatusAndTrustAccountId(status, trustAccountId)) {
        if (!hasPortalScope(txn)) {
          continue;
        }
        if (!seenMatters.add(txn.getProjectId())) {
          continue; // Only recompute each matter once per fan-out.
        }
        recomputeMatter(txn.getCustomerId(), txn.getProjectId());
      }
    }
  }

  /**
   * Replays every portal-eligible trust transaction on {@code (customerId, matterId)} in
   * chronological order, assigning each row a progressive {@code running_balance} using the same
   * sign convention as {@code TrustTransactionRepository.calculateClientBalanceAsOfDate}:
   *
   * <ul>
   *   <li>Credit (+amount): DEPOSIT, TRANSFER_IN, INTEREST_CREDIT
   *   <li>Debit (-amount): PAYMENT, TRANSFER_OUT, FEE_TRANSFER, REFUND, INTEREST_LPFF
   *   <li>REVERSAL and any other types: excluded from portal rows
   * </ul>
   *
   * <p>Used by the event-driven fan-out (approval, interest, reconciliation) and by the backfill.
   * Fetches the matter's transactions exactly once per call.
   */
  private void recomputeMatter(UUID customerId, UUID matterId) {
    var history = trustTransactionRepository.findByProjectIdOrderByTransactionDateAsc(matterId);
    BigDecimal running = BigDecimal.ZERO;
    Instant latest = null;
    for (var txn : history) {
      if (!PORTAL_STATUSES.contains(txn.getStatus())) {
        continue;
      }
      if (!isPortalEligible(txn)) {
        continue;
      }
      running = running.add(signedAmount(txn));
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
          running,
          occurredAt,
          sanitiser.sanitise(txn.getDescription(), txn.getTransactionType(), txn.getReference()),
          txn.getReference());
    }
    portalTrustRepo.upsertBalance(customerId, matterId, running, latest);
  }

  // ── Backfill ───────────────────────────────────────────────────────────

  /**
   * Rebuilds the portal trust read-model for a tenant by upserting a row per non-reversal trust
   * transaction (capped at {@link #BACKFILL_LIMIT_PER_MATTER} per matter) and the corresponding
   * per-matter balance snapshot. Intended to be called on module activation (via {@code POST
   * /internal/portal-resync/*}) or when repairing drift.
   *
   * <p>Upsert-only semantics: rows are merged by {@code (id)} / {@code (customer_id, matter_id)}
   * via {@code ON CONFLICT DO UPDATE} — the operation does not delete stale rows for customers that
   * have had trust transactions reversed or removed firm-side since the last sync. Run a targeted
   * delete (via the portal repository's delete-by-customer helpers) first if stale-row cleanup is
   * required.
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
        .where(RequestScopes.MEMBER_ID, SYSTEM_ACTOR_ID)
        .run(
            () -> {
              // Load every trust transaction once, filter portal-eligible + portal-status inline
              // and bucket by matter. (Previously this looped over [RECORDED, APPROVED] with
              // a findAll() call inside — O(2N) heap load + silent duplicates.)
              for (var txn : trustTransactionRepository.findAll()) {
                if (!PORTAL_STATUSES.contains(txn.getStatus())) {
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
            });

    // Write portal rows inside a portal-side transaction. Each matter's transactions are walked
    // in ascending occurrence order so every row gets its own progressive running balance.
    Integer txnCount =
        portalTxTemplate.execute(
            status -> {
              int written = 0;
              for (var rollup : rollups.values()) {
                List<TrustTransaction> ordered = rollup.ascHistory();
                BigDecimal running = BigDecimal.ZERO;
                int take = Math.min(ordered.size(), BACKFILL_LIMIT_PER_MATTER);
                // Skip the portion older than the retained window so the first-retained row's
                // running balance reflects every older credit/debit.
                for (int i = 0; i < ordered.size() - take; i++) {
                  running = running.add(signedAmount(ordered.get(i)));
                }
                Instant latestOccurredAt = null;
                for (int i = ordered.size() - take; i < ordered.size(); i++) {
                  var txn = ordered.get(i);
                  running = running.add(signedAmount(txn));
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
                      running,
                      occurredAt,
                      sanitiser.sanitise(
                          txn.getDescription(), txn.getTransactionType(), txn.getReference()),
                      txn.getReference());
                  written++;
                }
                // The final `running` value after walking every transaction is the matter's
                // current balance — equivalent to calculateBalanceByProjectId but computed
                // without an extra round-trip per matter.
                portalTrustRepo.upsertBalance(
                    rollup.customerId, rollup.matterId, running, latestOccurredAt);
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

    MatterRollup(UUID matterId, UUID customerId) {
      this.matterId = matterId;
      this.customerId = customerId;
    }

    void add(TrustTransaction txn) {
      transactions.add(txn);
    }

    /** Returns the matter's transactions in ascending date / createdAt order. */
    List<TrustTransaction> ascHistory() {
      transactions.sort(
          (a, b) -> {
            int byDate = a.getTransactionDate().compareTo(b.getTransactionDate());
            if (byDate != 0) {
              return byDate;
            }
            Instant aCreated = a.getCreatedAt() == null ? Instant.EPOCH : a.getCreatedAt();
            Instant bCreated = b.getCreatedAt() == null ? Instant.EPOCH : b.getCreatedAt();
            return aCreated.compareTo(bCreated);
          });
      return List.copyOf(transactions);
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

  /**
   * Returns {@code +amount} for credit-type transactions (DEPOSIT, TRANSFER_IN, INTEREST_CREDIT),
   * {@code -amount} for debit-type transactions (PAYMENT, TRANSFER_OUT, FEE_TRANSFER, REFUND,
   * INTEREST_LPFF), and zero for any other type — matching the sign convention used by {@link
   * TrustTransactionRepository#calculateClientBalanceAsOfDate} and {@link
   * TrustTransactionRepository#calculateBalanceByProjectId}.
   */
  private static BigDecimal signedAmount(TrustTransaction txn) {
    String type = txn.getTransactionType();
    if (type == null) {
      return BigDecimal.ZERO;
    }
    return switch (type) {
      case "DEPOSIT", "TRANSFER_IN", "INTEREST_CREDIT" -> txn.getAmount();
      case "PAYMENT", "TRANSFER_OUT", "FEE_TRANSFER", "REFUND", "INTEREST_LPFF" ->
          txn.getAmount().negate();
      default -> BigDecimal.ZERO;
    };
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
