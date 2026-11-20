package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
 * <p>A {@code WITH_PAIR} row (647D: the {@code UNIDENT_ALLOC_IN} of an approved allocation) posts
 * only with its pair, through {@link #recordWithPair}.
 *
 * <p>Every posting for a client runs under the client's lock anchor: callers take it with {@link
 * #lockClient} before reading a derived balance and posting (§16.2.3, T5).
 *
 * <p>Every {@code posted_at} is stamped under the account's posting lock ({@link #postedNow}, 650A
 * F-1): a shared row lock on the trust account, held to commit, which the Rule 54.15 list's lock
 * waits for — so no posting can be stamped before a list's {@code locked_at} and commit after the
 * list has read the books.
 *
 * <p>The global lock order (650A F-1, F-10, F-11) — every trust path takes the locks it needs in
 * this order, and none from an earlier step after one from a later step:
 *
 * <ol>
 *   <li>the path's own rows, in the order documented at each path — a bank statement line, an
 *       unidentified deposit item, an investment's placement row then its investment row then an
 *       interest entry, trust transaction rows — and the bank statement;
 *   <li>client lock anchors ({@link #lockClient}; two clients in ascending UUID order);
 *   <li>trust account rows: the posting lock ({@code FOR SHARE}, {@link #postedNow}) or an
 *       account-level lock ({@code FOR UPDATE}: the Rule 54.15 list, an account's update or
 *       closing, a placement request, a section 86(3) placement's approval, and — FU-9 — the final
 *       approval of a payment, refund, fee transfer or section 86(4) placement, for its cashbook
 *       check, after the client's anchor). A path that touches two accounts — only an investment's
 *       placement, approval or withdrawal, never two accounts of the same type — takes the trust
 *       banking account before the investment account.
 * </ol>
 *
 * Account rows come last because the posting lock is taken here, after the caller's locks: a path
 * that takes an account lock itself takes its client anchors first ({@code
 * TrustInvestmentService.placeInvestment}) and both accounts before the first leg posts ({@code
 * withdrawInvestment}). One exception: a placement's approval locks its new investment row after
 * the funding account — safe because it holds the placement row, which a withdrawal or rejection of
 * that investment takes first, and interest on an investment whose placement is unapproved is
 * refused before it posts. Within one transaction an account row held {@code FOR UPDATE} may be
 * re-taken {@code FOR SHARE} (already held), never the reverse (a lock upgrade).
 */
@Service
public class TrustPostingService {

  private final TrustTransactionRepository transactionRepository;
  private final ClientLedgerCardRepository ledgerCardRepository;
  private final TrustAccountRepository trustAccountRepository;
  private final Clock clock;

  public TrustPostingService(
      TrustTransactionRepository transactionRepository,
      ClientLedgerCardRepository ledgerCardRepository,
      TrustAccountRepository trustAccountRepository,
      Clock clock) {
    this.transactionRepository = transactionRepository;
    this.ledgerCardRepository = ledgerCardRepository;
    this.trustAccountRepository = trustAccountRepository;
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
    draft.applyPosting(type.effectFor(draft.getAmount()), postedNow(draft));
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
   * A WITH_PAIR row (§16.2.1 — {@code UNIDENT_ALLOC_IN} from 647D; {@code INV_RECEIVE}, {@code
   * INV_RETURN} and their {@code FIRM_INV_*} twins from 648A): never recorded on its own; posted
   * now, in the transaction that posts {@code pair}, taking its pair's status and — when the pair
   * was approved — its approvers ("take effect with their pair and inherit whatever control their
   * pair had"). The pair is posted and {@code APPROVED} (a placement's receive leg) or {@code
   * RECORDED} (a withdrawal's return leg — the withdrawal needs no approval, §16.3.7). {@code
   * MANDATORY}: a pair leg must never post outside its pair's transaction.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public TrustTransaction recordWithPair(TrustTransaction draft, TrustTransaction pair) {
    var type = TrustTransactionType.of(draft.getTransactionType());
    require(type.approval() == TrustTransactionType.Approval.WITH_PAIR, type, "recordWithPair");
    boolean approved = "APPROVED".equals(pair.getStatus());
    if (pair.getPostedAt() == null || !(approved || "RECORDED".equals(pair.getStatus()))) {
      throw new IllegalStateException("recordWithPair: pair " + pair.getId() + " is not posted");
    }
    // The pair's status must be the one its own type posts with: REQUIRED → APPROVED, NONE →
    // RECORDED — so a leg never inherits a status (and approvers) its pair could not have earned.
    var pairApproval = TrustTransactionType.of(pair.getTransactionType()).approval();
    var pairPostsAs =
        switch (pairApproval) {
          case REQUIRED -> "APPROVED";
          case NONE -> "RECORDED";
          case WITH_PAIR, WITH_ORIGINAL -> null;
        };
    if (!pair.getStatus().equals(pairPostsAs)) {
      throw new IllegalStateException(
          "recordWithPair: pair "
              + pair.getId()
              + " is "
              + pair.getStatus()
              + " but its type "
              + pair.getTransactionType()
              + " posts with approval "
              + pairApproval);
    }
    draft.setStatus(pair.getStatus());
    if (approved) {
      draft.setApprovedBy(pair.getApprovedBy());
      draft.setApprovedAt(pair.getApprovedAt());
      draft.setSecondApprovedBy(pair.getSecondApprovedBy());
      draft.setSecondApprovedAt(pair.getSecondApprovedAt());
    }
    draft.applyPosting(type.effectFor(draft.getAmount()), postedNow(draft));
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
    pending.applyPosting(type.effectFor(pending.getAmount()), postedNow(pending));
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
    pending.applyPosting(contraOf(original), postedNow(pending));
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
   * date is the caller's ({@code reversalDate}). Either way the contra carries the original's
   * ledger target — creditor account, investment and unidentified deposit (647A, 646B found.md,
   * 647B; {@link #contraDraft} copies client and matter) — so a {@code NON_CLIENT} contra satisfies
   * {@code chk_trust_txn_ledger_target}. Charge detail is not copied ({@code
   * chk_trust_txn_charge}).
   */
  @Transactional
  public TrustTransaction postReversal(TrustTransaction reversal, TrustTransaction original) {
    var rule = TrustTransactionType.of(original.getTransactionType()).reversal();
    reversal.setReversalOf(original.getId());
    reversal.copyLedgerTargetFrom(original);
    requireContraOf(reversal, original);
    return switch (rule) {
      case IMMEDIATE, PAIR, VIA_PAIR, OWNER -> {
        reversal.setStatus("RECORDED");
        reversal.applyPosting(contraOf(original), postedNow(reversal));
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

  /**
   * A {@code REVERSAL} draft for {@code original} (648B OQ-2: moved here from {@code
   * TrustTransactionService} so every reversing caller — the generic and unmatch paths, and {@code
   * TrustInvestmentService.reverseInterest} — builds the same shape without a bean cycle): the
   * original's account, amount, client, matter and counterparty, reference {@code REV-} + the
   * original's (truncated to the 200-character column), {@code reason} as its description, dated
   * {@code reversalDate}, recorded by {@code memberId}; no status — {@link #postReversal} decides
   * it from the original's reversal rule and copies the rest of the ledger target.
   */
  public static TrustTransaction contraDraft(
      TrustTransaction original, String reason, LocalDate reversalDate, UUID memberId) {
    return new TrustTransaction(
        original.getTrustAccountId(),
        "REVERSAL",
        original.getAmount(),
        original.getCustomerId(),
        original.getProjectId(),
        original.getCounterpartyCustomerId(),
        reversalReference(original.getReference()),
        reason,
        reversalDate,
        null, // status: decided by postReversal from the original's reversal rule
        memberId);
  }

  /** {@code REV-} + the original reference, truncated to the 200-character column. */
  private static String reversalReference(String originalReference) {
    String ref = "REV-" + originalReference;
    if (ref.length() > 200) {
      ref = ref.substring(0, 200);
    }
    return ref;
  }

  /**
   * §16.10.3: the registry's effect for the original, negated, on the original's ledger — the one
   * place the contra's sign is decided (also read by the approval check in {@code
   * TrustTransactionService}).
   */
  static TrustLedgerEffect contraOf(TrustTransaction original) {
    return TrustTransactionType.of(original.getTransactionType())
        .effectFor(original.getAmount())
        .negated();
  }

  /**
   * The {@code posted_at} of a row about to post, read under its account's posting lock (650A F-1):
   * {@code FOR SHARE} on the trust account row, held until the posting's transaction ends. Postings
   * share it; the Rule 54.15 list's {@code FOR UPDATE} on the same row waits for every posting in
   * flight to commit before it reads the books, and a posting that starts while the list is locking
   * waits for it — so its {@code posted_at} falls after the list's {@code locked_at}. Taken here,
   * the one place {@code posted_at} is written, after the caller's own locks — the last step of the
   * global lock order (class Javadoc).
   */
  private Instant postedNow(TrustTransaction row) {
    trustAccountRepository.findByIdForShare(row.getTrustAccountId());
    return clock.instant();
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
