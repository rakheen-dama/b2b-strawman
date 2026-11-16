package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place posting effects are written (Phase 92, ADR-378, architecture §16.3.2). Effects come
 * only from {@link TrustTransactionType}; no caller passes a sign. Applies no sufficiency check —
 * those stay in {@link TrustTransactionService} under the client's lock anchor (§16.2.3) — so the
 * demo seeder can leave its second client in debit on purpose (§16.3.15).
 *
 * <p>A reversal is a contra entry (649B, ADR-378, §16.3.11): the {@code REVERSAL} row carries the
 * negation of its original's registry effect on the original's ledger, on its own date; the
 * original keeps its effects and is only marked {@code REVERSED} with {@code reversedById}. So a
 * balance as at any date before the reversal still shows the original — history is never rewritten.
 * A credit contra awaiting approval posts in {@link #postReversalOnApproval}.
 *
 * <p>It is also the only main class that saves a trust transaction: the unposted state changes
 * (first approval in dual mode, rejection) and reconciliation's bank-line link go through the
 * narrow methods below, none of which touches effects or {@code posted_at}. {@code
 * TrustPostingRulesTest} (ArchUnit) holds that no other main class calls {@code
 * TrustTransaction.applyPosting} or {@code TrustTransactionRepository.save*}; the row CHECK {@code
 * chk_trust_txn_posting} (V16, tightened in V17) refuses a posted status without {@code posted_at},
 * or a posted row that does not move its ledger by exactly its amount, at the database.
 *
 * <p>Every posting for a client runs under the client's lock anchor: callers take it with {@link
 * #lockClient} before reading a derived balance and posting (§16.2.3, T5).
 */
@Service
public class TrustPostingService {

  private final TrustTransactionRepository transactionRepository;
  private final ClientLedgerCardRepository ledgerCardRepository;
  private final Clock clock;

  public TrustPostingService(
      TrustTransactionRepository transactionRepository,
      ClientLedgerCardRepository ledgerCardRepository,
      Clock clock) {
    this.transactionRepository = transactionRepository;
    this.ledgerCardRepository = ledgerCardRepository;
    this.clock = clock;
  }

  /**
   * Ensures the client's lock anchor exists, then takes its row lock (ADR-231 lock kept, ADR-378).
   * The native {@code INSERT … ON CONFLICT DO NOTHING} handles two concurrent first postings for
   * the client; the {@code FOR UPDATE} re-query then serialises every posting for it, so a
   * sufficiency check on the derived balance and the posting it guards cannot race. Two clients are
   * locked in ascending UUID order by the caller. Joins the caller's transaction (mandatory): the
   * lock is held until that transaction ends.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockClient(UUID trustAccountId, UUID customerId) {
    ledgerCardRepository.insertLockAnchorIfAbsent(trustAccountId, customerId);
    ledgerCardRepository
        .findByAccountAndCustomerForUpdate(trustAccountId, customerId)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Lock anchor missing after upsert for account="
                        + trustAccountId
                        + " customer="
                        + customerId));
  }

  /** A row whose type posts when recorded (approval = NONE): persisted RECORDED and posted now. */
  @Transactional
  public TrustTransaction record(TrustTransaction draft) {
    var type = TrustTransactionType.of(draft.getTransactionType());
    require(type.approval() == TrustTransactionType.Approval.NONE, type, "record");
    draft.setStatus("RECORDED");
    draft.applyPosting(type.effectFor(draft.getAmount()), clock.instant());
    return transactionRepository.save(draft);
  }

  /**
   * A row whose type needs approval (approval = REQUIRED): persisted AWAITING_APPROVAL with its
   * ledger and zero effects (unposted).
   */
  @Transactional
  public TrustTransaction recordAwaitingApproval(TrustTransaction draft) {
    var type = TrustTransactionType.of(draft.getTransactionType());
    require(
        type.approval() == TrustTransactionType.Approval.REQUIRED, type, "recordAwaitingApproval");
    draft.setStatus("AWAITING_APPROVAL");
    draft.applyPosting(TrustLedgerEffect.zero(type.ledger()), null);
    return transactionRepository.save(draft);
  }

  /**
   * Final approval of a REQUIRED row: APPROVED, effects from the registry, posted now. The caller
   * has done maker-checker and the sufficiency check under the client's lock anchor. Sets {@code
   * approvedBy}/{@code approvedAt} only when unset (dual mode's first approver is already recorded;
   * single mode sets them before calling). A credit contra posts through {@link
   * #postReversalOnApproval}, never here.
   */
  @Transactional
  public TrustTransaction postOnApproval(TrustTransaction pending, UUID approverId) {
    requireAwaitingApproval(pending, "postOnApproval");
    var type = TrustTransactionType.of(pending.getTransactionType());
    require(type.approval() == TrustTransactionType.Approval.REQUIRED, type, "postOnApproval");
    markApproved(pending, approverId);
    pending.applyPosting(type.effectFor(pending.getAmount()), clock.instant());
    return transactionRepository.save(pending);
  }

  /**
   * Final approval of a credit contra ({@code NEEDS_APPROVAL} rule): the {@code REVERSAL} becomes
   * APPROVED and posts the negation of {@code original}'s effect now; the original keeps its
   * effects and is marked {@code REVERSED} (§16.3.11 — the H-a fix). The caller has locked the
   * pending row and the original, done maker-checker and checked the client still covers it.
   */
  @Transactional
  public TrustTransaction postReversalOnApproval(
      TrustTransaction pending, TrustTransaction original, UUID approverId) {
    requireAwaitingApproval(pending, "postReversalOnApproval");
    requireContraOf(pending, original);
    markApproved(pending, approverId);
    pending.applyPosting(contraOf(original), clock.instant());
    var saved = transactionRepository.save(pending);
    markReversed(original, saved);
    return saved;
  }

  /**
   * Dual mode's first approval of a REQUIRED row: records the first approver and leaves the row
   * AWAITING_APPROVAL and unposted (the second approval posts it through {@link #postOnApproval}).
   */
  @Transactional
  public TrustTransaction recordFirstApproval(TrustTransaction pending, UUID approverId) {
    requireAwaitingApproval(pending, "recordFirstApproval");
    pending.setApprovedBy(approverId);
    pending.setApprovedAt(clock.instant());
    return transactionRepository.save(pending);
  }

  /** Rejects a row awaiting approval: REJECTED, never posted (effects stay zero). */
  @Transactional
  public TrustTransaction reject(TrustTransaction pending, UUID rejecterId, String reason) {
    requireAwaitingApproval(pending, "reject");
    pending.setRejectedBy(rejecterId);
    pending.setRejectedAt(clock.instant());
    pending.setRejectionReason(reason);
    pending.setStatus("REJECTED");
    return transactionRepository.save(pending);
  }

  /**
   * Links (or, with a null {@code bankStatementLineId}, unlinks) a row and a bank statement line —
   * reconciliation's only write to a trust row. Effects, status and {@code posted_at} are
   * untouched.
   */
  @Transactional
  public TrustTransaction linkBankLine(TrustTransaction transaction, UUID bankStatementLineId) {
    transaction.setBankStatementLineId(bankStatementLineId);
    return transactionRepository.save(transaction);
  }

  /**
   * A {@code REVERSAL} row for {@code original}, by the original type's reversal rule — this method
   * alone decides the reversal's status (§16.3.11). IMMEDIATE / PAIR / VIA_PAIR / OWNER (the caller
   * has already confined OWNER to its owner's own path): the contra posts RECORDED now with the
   * negation of the original's effect, and the original is marked {@code REVERSED} (its effects
   * untouched). NEEDS_APPROVAL: the contra is saved AWAITING_APPROVAL (unposted) and the original
   * is untouched until {@link #postReversalOnApproval}. Any other rule is refused. The reversal's
   * date is the caller's ({@code reversalDate}).
   */
  @Transactional
  public TrustTransaction postReversal(TrustTransaction reversal, TrustTransaction original) {
    var rule = TrustTransactionType.of(original.getTransactionType()).reversal();
    reversal.setReversalOf(original.getId());
    requireContraOf(reversal, original);
    return switch (rule) {
      case IMMEDIATE, PAIR, VIA_PAIR, OWNER -> {
        reversal.setStatus("RECORDED");
        reversal.applyPosting(contraOf(original), clock.instant());
        var saved = transactionRepository.save(reversal);
        markReversed(original, saved);
        yield saved;
      }
      case NEEDS_APPROVAL -> {
        reversal.setStatus("AWAITING_APPROVAL");
        reversal.applyPosting(TrustLedgerEffect.zero(original.getLedger()), null);
        yield transactionRepository.save(reversal);
      }
      default ->
          throw new InvalidStateException(
              "Cannot reverse transaction",
              "Transaction type " + original.getTransactionType() + " cannot be reversed");
    };
  }

  /** §16.10.3: the registry's effect for the original, negated, on the original's ledger. */
  private static TrustLedgerEffect contraOf(TrustTransaction original) {
    return TrustTransactionType.of(original.getTransactionType())
        .effectFor(original.getAmount())
        .negated();
  }

  /** The original stays posted with its effects; it is only marked as reversed by the contra. */
  private void markReversed(TrustTransaction original, TrustTransaction contra) {
    if (original.getPostedAt() == null) {
      throw new IllegalStateException(
          "Reversing an unposted " + original.getStatus() + " row " + original.getId());
    }
    original.setReversedById(contra.getId());
    original.setStatus("REVERSED");
    transactionRepository.save(original);
  }

  private void markApproved(TrustTransaction pending, UUID approverId) {
    if (pending.getApprovedBy() == null) {
      pending.setApprovedBy(approverId);
      pending.setApprovedAt(clock.instant());
    }
    pending.setStatus("APPROVED");
  }

  private static void requireContraOf(TrustTransaction reversal, TrustTransaction original) {
    if (!"REVERSAL".equals(reversal.getTransactionType())
        || !original.getId().equals(reversal.getReversalOf())
        || reversal.getAmount().compareTo(original.getAmount()) != 0) {
      throw new IllegalStateException(
          "Row " + reversal.getId() + " is not the contra of " + original.getId());
    }
  }

  private static void requireAwaitingApproval(TrustTransaction pending, String operation) {
    if (!"AWAITING_APPROVAL".equals(pending.getStatus())) {
      throw new IllegalStateException(operation + " on a " + pending.getStatus() + " row");
    }
  }

  private static void require(boolean ok, TrustTransactionType type, String operation) {
    if (!ok) {
      throw new IllegalStateException(
          operation
              + " is not the posting path for "
              + type
              + " (approval "
              + type.approval()
              + ")");
    }
  }
}
