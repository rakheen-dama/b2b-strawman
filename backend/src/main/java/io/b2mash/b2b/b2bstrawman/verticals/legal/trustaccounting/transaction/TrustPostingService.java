package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place posting effects are written (Phase 92, ADR-378, architecture §16.3.2). Effects come
 * only from {@link TrustTransactionType}; no caller passes a sign. Applies no sufficiency check —
 * those stay in {@link TrustTransactionService} under the client's lock anchor (§16.2.3) — so the
 * demo seeder can leave its second client in debit on purpose (§16.3.15).
 *
 * <p>Reversals keep the HEAD model in 646A: a REVERSAL row posts at zero effect and a reversed
 * original's effects are zeroed, so it drops out of every sum as it did under the status filter; an
 * approved credit reversal moves nothing (H-a, pinned — fixed in 649B by the contra model).
 *
 * <p>{@code TrustPostingRulesTest} (ArchUnit) holds that no other main class calls {@code
 * TrustTransaction.applyPosting}; the row CHECK {@code chk_trust_txn_posting} (V16) refuses a
 * posted status without {@code posted_at} at the database.
 */
@Service
public class TrustPostingService {

  private final TrustTransactionRepository transactionRepository;
  private final Clock clock;

  public TrustPostingService(TrustTransactionRepository transactionRepository, Clock clock) {
    this.transactionRepository = transactionRepository;
    this.clock = clock;
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
   * Final approval of a REQUIRED row, or of a credit REVERSAL awaiting approval: APPROVED, effects
   * from the registry (a REVERSAL: zero in 646A — H-a), posted now. The caller has done
   * maker-checker and the sufficiency check under the client's lock anchor. Sets {@code
   * approvedBy}/{@code approvedAt} only when unset (dual mode's first approver is already recorded;
   * single mode sets them before calling).
   */
  @Transactional
  public TrustTransaction postOnApproval(TrustTransaction pending, UUID approverId) {
    if (!"AWAITING_APPROVAL".equals(pending.getStatus())) {
      throw new IllegalStateException("postOnApproval on a " + pending.getStatus() + " row");
    }
    if (pending.getApprovedBy() == null) {
      pending.setApprovedBy(approverId);
      pending.setApprovedAt(clock.instant());
    }
    pending.setStatus("APPROVED");
    var type = TrustTransactionType.of(pending.getTransactionType());
    var effect =
        type == TrustTransactionType.REVERSAL
            ? TrustLedgerEffect.zero(
                pending.getLedger()) // 646A/HEAD: H-a — 649B posts the negation
            : type.effectFor(pending.getAmount());
    pending.applyPosting(effect, clock.instant());
    return transactionRepository.save(pending);
  }

  /**
   * A REVERSAL row for {@code original}, by the original type's reversal rule. IMMEDIATE / PAIR /
   * VIA_PAIR: the reversal posts RECORDED at zero effect and the original becomes REVERSED with its
   * effects zeroed (HEAD semantics; 649B keeps the original's effect and posts the negation).
   * NEEDS_APPROVAL: the reversal is saved AWAITING_APPROVAL (unposted); the original is untouched.
   */
  @Transactional
  public TrustTransaction postReversal(TrustTransaction reversal, TrustTransaction original) {
    var rule = TrustTransactionType.of(original.getTransactionType()).reversal();
    reversal.setReversalOf(original.getId());
    return switch (rule) {
      case IMMEDIATE, PAIR, VIA_PAIR -> {
        reversal.setStatus("RECORDED");
        reversal.applyPosting(TrustLedgerEffect.zero(original.getLedger()), clock.instant());
        var saved = transactionRepository.save(reversal);
        original.setReversedById(saved.getId());
        original.setStatus("REVERSED");
        original.applyPosting(TrustLedgerEffect.zero(original.getLedger()), original.getPostedAt());
        transactionRepository.save(original);
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
