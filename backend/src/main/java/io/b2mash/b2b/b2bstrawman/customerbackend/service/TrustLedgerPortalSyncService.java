package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalTrustReadModelRepository;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustDomainEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustTransactionApprovalEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustTransactionRecordedEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedger;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionHistoryRow;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionTypeLabels;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
 *   <li>{@link TrustTransactionRecordedEvent} — the same, for rows posted when recorded.
 *   <li>{@link TrustDomainEvent.ReconciliationCompleted} — fans out across every matter in the
 *       account; the reconciliation may have adjusted many matters' running balances.
 *   <li>{@link TrustDomainEvent.TakeOnPositionCommitted} (Phase 93 657C, §17.6.6) — fans out across
 *       every matter of every account a trust take-on posted to, each matter recomputed once: the
 *       take-on publishes no per-row event (and so sends no email).
 * </ul>
 *
 * <p>Raw firm-side descriptions pass through {@link PortalTrustDescriptionSanitiser} before
 * persistence — the portal schema only ever stores sanitised text (ADR-254).
 *
 * <p>Projection rules (Phase 92 648D, §16.6.3): a posted row with a client and a matter is
 * projected by its ledger. {@code CLIENT} rows drive {@code running_balance} and {@code
 * current_balance}; every {@code CLIENT_INVESTMENT} row drives {@code invested_balance}, and of
 * those only the interest credited to the client ({@code INV_INTEREST}) and its {@code REVERSAL}
 * contra are visible rows. {@code NON_CLIENT} and {@code INTER_ACCOUNT} rows — the Fund's interest
 * and share, bank charges, unidentified deposits, section 86(3) placements — never reach the
 * portal. The read-model stores the type code and the ledger; the client label is resolved on read
 * ({@code PortalTrustLedgerService}).
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
   * Firm-side transaction statuses that produce portal-visible rows — the posted statuses of {@code
   * chk_trust_txn_posting}. {@code RECORDED} covers rows posted when recorded (DEPOSIT, transfers,
   * immediate contra entries); {@code APPROVED} covers the post-approval rows
   * (PAYMENT/FEE_TRANSFER/REFUND, an approved credit contra); {@code REVERSED} covers a reversed
   * original, which still counts under the contra model (649B, ADR-378) — its {@code REVERSAL} row
   * carries the negation. Both halves of the pair must be replayed, or the portal balance would be
   * off by the amount.
   */
  private static final List<String> PORTAL_STATUSES = List.of("RECORDED", "APPROVED", "REVERSED");

  private final PortalTrustReadModelRepository portalTrustRepo;
  private final TrustTransactionRepository trustTransactionRepository;
  private final CustomerRepository customerRepository;
  private final PortalTrustDescriptionSanitiser sanitiser;
  private final OrgSchemaMappingRepository orgSchemaMappingRepository;
  private final TransactionTemplate portalTxTemplate;

  public TrustLedgerPortalSyncService(
      PortalTrustReadModelRepository portalTrustRepo,
      TrustTransactionRepository trustTransactionRepository,
      CustomerRepository customerRepository,
      PortalTrustDescriptionSanitiser sanitiser,
      OrgSchemaMappingRepository orgSchemaMappingRepository,
      @Qualifier("portalTransactionManager") PlatformTransactionManager portalTxManager) {
    this.portalTrustRepo = portalTrustRepo;
    this.trustTransactionRepository = trustTransactionRepository;
    this.customerRepository = customerRepository;
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
    RequestScopes.runForTenant(
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

  /**
   * Syncs the portal read-model for trust transactions created directly in {@code RECORDED} status
   * — i.e., the no-approval-threshold DEPOSIT path and the paired TRANSFER_OUT/TRANSFER_IN rows
   * produced by {@code TrustTransactionService.recordTransfer}. These paths bypass the
   * awaiting-approval / approved lifecycle, so {@link
   * #onTrustTransactionApproval(TrustTransactionApprovalEvent)} never fires for them (GAP-L-52).
   * Since 649B every contra ({@code REVERSAL}) row publishes this event when it posts — at once for
   * an immediate reversal, and at final approval for a contra that needed approval (a deposit
   * reversal) — so the matter re-projects with the reversed original and its contra together.
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTrustTransactionRecorded(TrustTransactionRecordedEvent event) {
    RequestScopes.runForTenant(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            syncSingleTransaction(event.transactionId());
          } catch (Exception e) {
            log.error(
                "Portal trust sync failed for event=trust_transaction.recorded tenant={} txn={}"
                    + " account={}",
                event.tenantId(),
                event.transactionId(),
                event.trustAccountId(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onReconciliationCompleted(TrustDomainEvent.ReconciliationCompleted event) {
    RequestScopes.runForTenant(
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

  /**
   * A committed trust take-on (657C): every matter with a posted row on any of the accounts is
   * recomputed once — the brought-forward balance ({@code TAKEON_CLIENT}, the one visible take-on
   * row) and the invested balance; offsets, items and non-client rows stay hidden by the projection
   * rules. One failure per account is logged and does not stop the others.
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTakeOnPositionCommitted(TrustDomainEvent.TakeOnPositionCommitted event) {
    RequestScopes.runForTenant(
        event.tenantId(),
        event.orgId(),
        () -> {
          Set<UUID> seenMatters = new HashSet<>();
          for (UUID accountId : event.accountIds()) {
            try {
              syncForTrustAccount(accountId, seenMatters);
            } catch (Exception e) {
              log.error(
                  "Portal trust sync failed for event=take_on_position_committed tenant={}"
                      + " batch={} account={}",
                  event.tenantId(),
                  event.batchId(),
                  accountId,
                  e);
            }
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
    if (!hasPortalScope(txn.getCustomerId(), txn.getProjectId())) {
      return;
    }
    // Upsert this row plus replay the matter so the new transaction's running_balance and every
    // earlier row's running_balance stay consistent with the progressive sum. Each row is
    // stamped with its own customer_id (see recomputeMatter).
    recomputeMatter(txn.getProjectId());
  }

  /**
   * Recomputes the portal read-model for every matter that has at least one posted trust
   * transaction in the given account. Used by the reconciliation-completed fan-out handler.
   */
  private void syncForTrustAccount(UUID trustAccountId) {
    syncForTrustAccount(trustAccountId, new HashSet<>());
  }

  /** As above, skipping the matters already recomputed in this fan-out ({@code seenMatters}). */
  private void syncForTrustAccount(UUID trustAccountId, Set<UUID> seenMatters) {
    for (var status : PORTAL_STATUSES) {
      for (var txn :
          trustTransactionRepository.findByStatusAndTrustAccountId(status, trustAccountId)) {
        if (!hasPortalScope(txn.getCustomerId(), txn.getProjectId())) {
          continue;
        }
        if (!seenMatters.add(txn.getProjectId())) {
          continue; // Only recompute each matter once per fan-out.
        }
        recomputeMatter(txn.getProjectId());
      }
    }
  }

  /**
   * Replays every projected trust transaction on {@code matterId} in chronological order, stamping
   * each row with that transaction's own {@code customer_id} and a per-customer progressive {@code
   * running_balance} of the row's own ledger. Each row moves its ledger by its posted {@code
   * ledger_effect} (Phase 92, ADR-378) — the figure of {@code TrustLedgerQueryRepository}. Rows are
   * split by ledger (648D, §16.6.3, {@link #projectedLedger}): {@code CLIENT} rows move the general
   * balance, {@code CLIENT_INVESTMENT} rows the invested balance, and only {@link #isVisibleRow}
   * rows are written as lines — so an {@code INV_INTEREST} line carries the invested running
   * balance (OQ-1). A reversed original and its {@code REVERSAL} contra are both replayed, each
   * with its own effect (649B). Rows are upserted only: a row no longer projected is not deleted
   * here (that is {@link #backfillForTenant}'s drift repair).
   *
   * <p>Used by the event-driven fan-out (approval, recorded, interest, reconciliation). Fetches the
   * matter's transactions exactly once per call.
   *
   * <p><b>Why per-row customer attribution:</b> A trust transfer within the same matter writes two
   * rows (TRANSFER_OUT/source_customer, TRANSFER_IN/target_customer) sharing a single {@code
   * matter_id}. Publishing one event per leg triggers two recompute calls on the same matter —
   * keying each row by its own {@code customer_id} (rather than the event's customerId) prevents
   * the second leg's replay from re-attributing the first leg's portal rows to the wrong customer
   * (CodeRabbit follow-up to GAP-L-52).
   *
   * <p><b>Atomic and in order (Phase 92 648D F-4 / F-5):</b> the whole replay runs in one portal
   * transaction, so its rows and its balance snapshot commit or roll back together — a failed write
   * never leaves rows whose running balance disagrees with the stored balance. Its first statement
   * takes the matter's replay lock ({@link PortalTrustReadModelRepository#lockMatterReplay}), held
   * until that commit; the firm history is read only after the lock is granted, at READ COMMITTED,
   * as {@link TrustTransactionHistoryRow} projections built from column values ({@link
   * TrustTransactionRepository#findHistoryRowsByProjectId}) — never entities, so a row the
   * committed transaction's still-open persistence context holds from before cannot come back
   * stale. A replay that runs after another has committed therefore reads at least every committed
   * row the other read: two concurrent replays of one matter can no longer write an older balance
   * last. The listeners run {@code AFTER_COMMIT}, so the replay holds no firm-side lock, and its
   * history read is a plain {@code SELECT} that waits on none; a fan-out takes one matter's lock at
   * a time (each released at that matter's commit).
   */
  private void recomputeMatter(UUID matterId) {
    String tenantSchema = RequestScopes.requireTenantId();
    portalTxTemplate.executeWithoutResult(
        status -> {
          portalTrustRepo.lockMatterReplay(tenantSchema, matterId);
          var history = trustTransactionRepository.findHistoryRowsByProjectId(matterId);
          Map<UUID, Balances> balancesByCustomer = new LinkedHashMap<>();
          for (var txn : history) {
            if (!PORTAL_STATUSES.contains(txn.status()) || !hasPortalScope(txn)) {
              continue;
            }
            var ledger = projectedLedger(txn);
            if (ledger.isEmpty()) {
              continue;
            }
            UUID rowCustomerId = txn.clientId();
            Instant occurredAt = deriveOccurredAt(txn);
            BigDecimal running =
                balancesByCustomer
                    .computeIfAbsent(rowCustomerId, id -> new Balances())
                    .apply(txn, ledger.get(), occurredAt);
            if (isVisibleRow(txn, ledger.get())) {
              upsertRow(txn, rowCustomerId, matterId, ledger.get(), running, occurredAt);
            }
          }
          balancesByCustomer.forEach(
              (customerId, balances) ->
                  portalTrustRepo.upsertBalance(
                      customerId, matterId, balances.general, balances.invested, balances.latest));
        });
  }

  // ── Backfill ───────────────────────────────────────────────────────────

  /**
   * Rebuilds the portal trust read-model for a tenant by writing a row per posted, visible trust
   * transaction (capped at {@link #BACKFILL_LIMIT_PER_MATTER} per matter) and the corresponding
   * per-matter balance snapshot — the same split by ledger as {@link #recomputeMatter} (648D).
   *
   * <p><b>No production caller.</b> Nothing in the application invokes this method — no route, job
   * or listener (the {@code POST /internal/portal/resync/{orgId}} endpoint rebuilds projects,
   * documents, tasks and the directory, not trust). Only tests call it today; it is not an operator
   * repair procedure until a caller that binds the organisation scope is added.
   *
   * <p>Wipe-and-rewrite semantics: after upserting the fresh current-window projection for each
   * touched customer, the method deletes portal balance/transaction rows that fall outside the new
   * projection. Concretely, per customer:
   *
   * <ul>
   *   <li>Balance rows whose {@code matter_id} is no longer in the retained matter set are dropped
   *       (e.g., matters that have had every trust transaction reversed firm-side).
   *   <li>Transaction rows whose {@code matter_id} is no longer in the retained matter set are
   *       dropped for the same reason.
   *   <li>For matters still in the set, transaction rows whose {@code id} is outside the retained
   *       window (per {@link #BACKFILL_LIMIT_PER_MATTER}) are trimmed.
   * </ul>
   *
   * Customers with no portal-eligible matters have their entire read-model wiped.
   */
  public BackfillResult backfillForTenant(String orgId) {
    if (!RequestScopes.ORG_ID.isBound()) {
      throw new IllegalStateException(
          "backfillForTenant must run inside an authenticated request scope");
    }
    // Tenant-isolation guard: the authenticated caller's scoped orgId MUST match the argument.
    // Otherwise an authenticated request for org A could call backfillForTenant("orgB") and the
    // RequestScopes.runForTenantWithMember(...) below would silently rebind to org B — a
    // cross-tenant escape. Issue #1267 / ADR-T008 follow-up.
    String scopedOrgId = RequestScopes.ORG_ID.get();
    // Null-safe equality: a null orgId argument should fall into the deny branch, not throw NPE
    // and produce a 500 instead of the intended 403. Per CodeRabbit review on PR #1272.
    if (!Objects.equals(orgId, scopedOrgId)) {
      throw new ForbiddenException(
          "Cross-tenant backfill denied",
          "Authenticated orgId does not match backfill target orgId");
    }
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
    // All customer ids that live in this tenant's schema — used later to bound the cross-tenant
    // portal wipe so we only ever touch this tenant's customers (the portal schema is shared).
    Set<UUID> tenantCustomerIds = new HashSet<>();

    RequestScopes.runForTenantWithMember(
        schema,
        orgId,
        SYSTEM_ACTOR_ID,
        () -> {
          // Load every trust transaction once, filter projected + portal-status inline and
          // bucket by matter. (Previously this looped over the statuses with a findAll() call
          // inside — O(2N) heap load + silent duplicates.)
          for (var entity : trustTransactionRepository.findAll()) {
            var txn = TrustTransactionHistoryRow.of(entity);
            if (!PORTAL_STATUSES.contains(txn.status())) {
              continue;
            }
            if (!hasPortalScope(txn) || projectedLedger(txn).isEmpty()) {
              continue;
            }
            UUID matterId = txn.matterId();
            rollups.computeIfAbsent(matterId, id -> new MatterRollup(id, txn.clientId())).add(txn);
          }
          // Snapshot the tenant's customer id set inside the same scope binding — needed for
          // the wipe-and-rewrite drift repair below.
          for (var customer : customerRepository.findAll()) {
            tenantCustomerIds.add(customer.getId());
          }
        });

    // Write portal rows inside a portal-side transaction. Each matter's transactions are walked
    // in ascending occurrence order so every row gets its own progressive running balance.
    // Track (customerId → matter ids) and (customerId,matterId) → retained txn ids so we can
    // drop portal rows that no longer belong to the current projection.
    Map<UUID, Set<UUID>> retainedMattersByCustomer = new HashMap<>();
    Map<UUID, Map<UUID, Set<UUID>>> retainedTxnsByCustomerMatter = new HashMap<>();

    Integer txnCount =
        portalTxTemplate.execute(
            status -> {
              int written = 0;
              for (var rollup : rollups.values()) {
                List<TrustTransactionHistoryRow> ordered = rollup.ascHistory();
                // The window counts visible rows only; every projected row still moves its
                // ledger, so the first retained row's running balance reflects every older
                // credit/debit (and an INV_RECEIVE outside the window still counts as invested).
                long visible =
                    ordered.stream()
                        .filter(t -> isVisibleRow(t, projectedLedger(t).orElseThrow()))
                        .count();
                long olderThanWindow = Math.max(0, visible - BACKFILL_LIMIT_PER_MATTER);
                var balances = new Balances();
                Set<UUID> retainedTxnIds = new HashSet<>();
                long visibleSeen = 0;
                for (var txn : ordered) {
                  var ledger = projectedLedger(txn).orElseThrow();
                  Instant occurredAt = deriveOccurredAt(txn);
                  BigDecimal running = balances.apply(txn, ledger, occurredAt);
                  if (!isVisibleRow(txn, ledger) || visibleSeen++ < olderThanWindow) {
                    continue;
                  }
                  upsertRow(txn, rollup.customerId, rollup.matterId, ledger, running, occurredAt);
                  retainedTxnIds.add(txn.id());
                  written++;
                }
                // The final balances after walking every transaction are the matter's current
                // general and invested balances — equivalent to the derived balances but computed
                // without an extra round-trip per matter.
                portalTrustRepo.upsertBalance(
                    rollup.customerId,
                    rollup.matterId,
                    balances.general,
                    balances.invested,
                    balances.latest);

                retainedMattersByCustomer
                    .computeIfAbsent(rollup.customerId, id -> new HashSet<>())
                    .add(rollup.matterId);
                retainedTxnsByCustomerMatter
                    .computeIfAbsent(rollup.customerId, id -> new HashMap<>())
                    .put(rollup.matterId, retainedTxnIds);
              }

              // Drift repair: drop portal rows that fall outside the fresh projection.
              // Per touched customer: balances + transactions for matters not in the retained
              // set go away; for retained matters, trim transaction rows outside the retained
              // window.
              for (var entry : retainedMattersByCustomer.entrySet()) {
                UUID customerId = entry.getKey();
                Set<UUID> retainedMatters = entry.getValue();
                portalTrustRepo.deleteBalancesForCustomerNotInMatters(customerId, retainedMatters);
                portalTrustRepo.deleteTransactionsForCustomerNotInMatters(
                    customerId, retainedMatters);
                for (var matterEntry : retainedTxnsByCustomerMatter.get(customerId).entrySet()) {
                  portalTrustRepo.deleteTransactionsForMatterNotInIds(
                      customerId, matterEntry.getKey(), matterEntry.getValue());
                }
              }

              // Customers belonging to this tenant that previously had portal rows but no longer
              // have any portal-eligible matters in the current firm-side projection: wipe
              // them entirely. Constrained to tenantCustomerIds so other tenants' rows in the
              // shared portal schema are never touched.
              Set<UUID> touchedCustomers = retainedMattersByCustomer.keySet();
              for (UUID staleCustomerId : portalTrustRepo.findCustomerIdsWithPortalTrustRows()) {
                if (!tenantCustomerIds.contains(staleCustomerId)) {
                  continue;
                }
                if (touchedCustomers.contains(staleCustomerId)) {
                  continue;
                }
                portalTrustRepo.deleteBalancesByCustomer(staleCustomerId);
                portalTrustRepo.deleteTransactionsByCustomer(staleCustomerId);
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

  /**
   * One client's running balances on a matter while a replay walks it (648D): the general ({@code
   * CLIENT}) and invested ({@code CLIENT_INVESTMENT}) ledgers, and the latest occurrence over every
   * row that moved either — the balance snapshot's {@code last_transaction_at}.
   */
  private static final class Balances {
    BigDecimal general = BigDecimal.ZERO;
    BigDecimal invested = BigDecimal.ZERO;
    Instant latest;

    /**
     * Moves {@code ledger} by the row's effect and {@code latest} to the row's {@code occurredAt}
     * ({@link #deriveOccurredAt}); returns that ledger's running balance.
     */
    BigDecimal apply(TrustTransactionHistoryRow txn, TrustLedger ledger, Instant occurredAt) {
      if (latest == null || occurredAt.isAfter(latest)) {
        latest = occurredAt;
      }
      if (ledger == TrustLedger.CLIENT_INVESTMENT) {
        invested = invested.add(signedAmount(txn));
        return invested;
      }
      general = general.add(signedAmount(txn));
      return general;
    }
  }

  /** Accumulates trust transactions for a single matter while building a backfill view. */
  private static final class MatterRollup {
    final UUID matterId;
    final UUID customerId;
    final java.util.ArrayList<TrustTransactionHistoryRow> transactions =
        new java.util.ArrayList<>();

    MatterRollup(UUID matterId, UUID customerId) {
      this.matterId = matterId;
      this.customerId = customerId;
    }

    void add(TrustTransactionHistoryRow txn) {
      transactions.add(txn);
    }

    /** Returns the matter's transactions in ascending date / createdAt order. */
    List<TrustTransactionHistoryRow> ascHistory() {
      transactions.sort(
          (a, b) -> {
            int byDate = a.transactionDate().compareTo(b.transactionDate());
            if (byDate != 0) {
              return byDate;
            }
            Instant aCreated = a.createdAt() == null ? Instant.EPOCH : a.createdAt();
            Instant bCreated = b.createdAt() == null ? Instant.EPOCH : b.createdAt();
            int byCreated = aCreated.compareTo(bCreated);
            // Same final tiebreak as findHistoryRowsByProjectId and the portal
            // read order (G89-F39), so running balances read in display order.
            return byCreated != 0 ? byCreated : compareInPostgresOrder(a.id(), b.id());
          });
      return List.copyOf(transactions);
    }
  }

  // ── Pure helpers ──────────────────────────────────────────────────────

  /**
   * Orders two ids as PostgreSQL orders {@code uuid} values — by their 16 bytes, unsigned. {@link
   * UUID#compareTo} compares the two halves as signed longs, so it disagrees whenever the top bit
   * of a half differs (an id starting {@code 9…} sorts before one starting {@code 1…}). The
   * backfill's tiebreak must match the database's, or two rows on the same date and recorded
   * instant carry each other's running balance (PR #1802 review).
   */
  static int compareInPostgresOrder(UUID a, UUID b) {
    int byHigh = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
    return byHigh != 0
        ? byHigh
        : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
  }

  /** Upserts one visible row under {@code customerId} with its own ledger's running balance. */
  private void upsertRow(
      TrustTransactionHistoryRow txn,
      UUID customerId,
      UUID matterId,
      TrustLedger ledger,
      BigDecimal runningBalance,
      Instant occurredAt) {
    portalTrustRepo.upsertTransaction(
        txn.id(),
        customerId,
        matterId,
        txn.transactionType(),
        ledger.name(),
        txn.amount(),
        runningBalance,
        txn.transactionDate(),
        recordedAt(txn),
        occurredAt,
        portalDescription(txn),
        txn.reference());
  }

  /**
   * The description the portal stores for a row: the client-surface rule ({@link
   * TrustTransactionTypeLabels#clientDescription} — a {@code REVERSAL} row reads "{@code Reversal —
   * <reference>}", never the firm user's free-text reason, PR #1789 review) through the ADR-254
   * sanitiser (140-char cap, {@code [internal]} strip, blank fallback). The fallback names the type
   * by its client label ("Payment — TX-1"), never the raw code (648D, OQ-5).
   */
  private String portalDescription(TrustTransactionHistoryRow txn) {
    return sanitiser.sanitise(
        TrustTransactionTypeLabels.clientDescription(
            txn.transactionType(), txn.description(), txn.reference()),
        TrustTransactionTypeLabels.label(txn.transactionType()),
        txn.reference());
  }

  /**
   * Portal surfaces are customer/matter scoped. A trust transaction must carry both to be
   * surfaceable — e.g., some inter-account FEE_TRANSFER rows carry a customer but no project.
   */
  private static boolean hasPortalScope(TrustTransactionHistoryRow txn) {
    return txn.clientId() != null && txn.matterId() != null;
  }

  private static boolean hasPortalScope(UUID customerId, UUID projectId) {
    return customerId != null && projectId != null;
  }

  /**
   * The ledger a row is projected under, or empty when the row never reaches the portal (648D,
   * §16.6.3): a client's general ledger ({@code CLIENT}) and its section 86(4) investment ledger
   * ({@code CLIENT_INVESTMENT}). {@code NON_CLIENT} and {@code INTER_ACCOUNT} rows are not the
   * client's business and never reach the portal (the database already refuses a client or a matter
   * on them, {@code chk_trust_txn_ledger_target}; this is the stated rule, kept as defence in
   * depth). A general-ledger row counts only when the registry marks its type client-visible
   * ({@link TrustTransactionType#clientVisible()} — every {@code CLIENT} type is, REVERSAL included
   * since 649B); a null or unknown code is not surfaced (lenient lookup — the type CHECK admits no
   * other code). Every investment-ledger row counts, visible or not, so the invested balance equals
   * the firm's.
   */
  private static Optional<TrustLedger> projectedLedger(TrustTransactionHistoryRow txn) {
    TrustLedger ledger = txn.ledger();
    if (ledger == TrustLedger.CLIENT) {
      boolean clientVisible =
          TrustTransactionType.find(txn.transactionType())
              .map(TrustTransactionType::clientVisible)
              .orElse(false);
      return clientVisible ? Optional.of(ledger) : Optional.empty();
    }
    return ledger == TrustLedger.CLIENT_INVESTMENT ? Optional.of(ledger) : Optional.empty();
  }

  /**
   * Whether a projected row is a line the client sees: every general-ledger row; on the investment
   * ledger only the interest credited to the client ({@code INV_INTEREST}, "Section 86(4)
   * investment interest") and — OQ-4 — its {@code REVERSAL} contra, so a reversed credit shows with
   * the movement that undid it (649B). {@code INV_RECEIVE} / {@code INV_WITHDRAW} move the invested
   * balance without a line: the placement and the return show on the general ledger as {@code
   * INV_PLACE} / {@code INV_RETURN}.
   */
  private static boolean isVisibleRow(TrustTransactionHistoryRow txn, TrustLedger ledger) {
    if (ledger == TrustLedger.CLIENT) {
      return true;
    }
    return TrustTransactionType.find(txn.transactionType())
        .filter(t -> t == TrustTransactionType.INV_INTEREST || t == TrustTransactionType.REVERSAL)
        .isPresent();
  }

  /**
   * The row's own posted effect on its client ledger (Phase 92, ADR-378): {@code +amount} for a
   * credit, {@code -amount} for a debit, zero for an unposted or zero-effect row — the figure every
   * derived balance in {@code TrustLedgerQueryRepository} sums.
   */
  private static BigDecimal signedAmount(TrustTransactionHistoryRow txn) {
    return txn.ledgerEffect() != null ? txn.ledgerEffect() : BigDecimal.ZERO;
  }

  /**
   * The firm row's recorded time ({@code created_at}): the portal ledger's tiebreak after the
   * transaction date, matching {@code findHistoryRowsByProjectId} (G89-F39). Falls back to {@link
   * #deriveOccurredAt} for a row without one.
   */
  private static Instant recordedAt(TrustTransactionHistoryRow txn) {
    return txn.createdAt() != null ? txn.createdAt() : deriveOccurredAt(txn);
  }

  private static Instant deriveOccurredAt(TrustTransactionHistoryRow txn) {
    if (txn.approvedAt() != null) {
      return txn.approvedAt();
    }
    if (txn.createdAt() != null) {
      return txn.createdAt();
    }
    // Fall back to transaction date at UTC midnight — transactionDate is always non-null.
    return txn.transactionDate().atStartOfDay(ZoneOffset.UTC).toInstant();
  }
}
