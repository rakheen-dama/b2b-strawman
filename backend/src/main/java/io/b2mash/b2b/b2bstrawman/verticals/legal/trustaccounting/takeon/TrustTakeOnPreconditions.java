package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon;

import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatch;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchRepository;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchStatus;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnDataset;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionMessages;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.ReconciliationStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The account checks of a trust take-on (Phase 93 657B, §17.3.9) for one trust account at a cutover
 * date, in the §17.3.9 order, stopping at the first that fails. Read-only. The dry run runs them on
 * the banking account and on every investment account its rows name; the commit (657C) re-runs them
 * under lock, so they live beside it.
 *
 * <p>The cutover-is-a-month-end check is a batch input, not an account fact: the dry run makes it.
 */
@Component
public class TrustTakeOnPreconditions {

  /** Which account of the take-on is checked. */
  public enum Kind {
    /** The batch's trust banking account. */
    BANKING,
    /** An investment account the batch's investment rows name. */
    INVESTMENT
  }

  /** The first check that failed: its code and the §17.3.9 text. */
  public record Failure(String code, String message) {}

  private static final List<TakeOnBatchStatus> TAKEN_ON =
      List.of(TakeOnBatchStatus.COMMITTING, TakeOnBatchStatus.COMPLETED);

  private final TrustTransactionRepository transactions;
  private final TrustReconciliationRepository reconciliations;
  private final TakeOnBatchRepository batches;

  public TrustTakeOnPreconditions(
      TrustTransactionRepository transactions,
      TrustReconciliationRepository reconciliations,
      TakeOnBatchRepository batches) {
    this.transactions = transactions;
    this.reconciliations = reconciliations;
    this.batches = batches;
  }

  /**
   * The first failing check of {@code account} at {@code cutover}, or empty. {@code batchId} is the
   * batch being checked (it never counts against itself).
   */
  public Optional<Failure> firstFailure(
      TrustAccount account, LocalDate cutover, Kind kind, UUID batchId) {
    String name = account.getAccountName();
    // 1. The banking account is a GENERAL or SECTION_86 trust account.
    if (kind == Kind.BANKING && account.getAccountType() == TrustAccountType.INVESTMENT) {
      return fail(
          TrustPositionMessages.ACCOUNT_NOT_BANKING,
          TrustPositionMessages.ACCOUNT_NOT_BANKING_MESSAGE);
    }
    // 2. Open.
    if (account.getStatus() != TrustAccountStatus.ACTIVE) {
      return fail(TrustPositionMessages.ACCOUNT_CLOSED, TrustPositionMessages.accountClosed(name));
    }
    // 3. Opened on or before the cutover date.
    if (account.getOpenedDate() != null && account.getOpenedDate().isAfter(cutover)) {
      return fail(
          kind,
          name,
          TrustPositionMessages.ACCOUNT_OPENED_AFTER_CUTOVER,
          TrustPositionMessages.openedAfterCutover(account.getOpenedDate(), cutover));
    }
    // 4. A take-on is the first entry. A shared investment account may already hold the take-on
    //    rows of the other banking account's completed batch at the same date — never another date.
    if (kind == Kind.BANKING) {
      if (transactions.existsOnOrBefore(account.getId(), cutover)) {
        return fail(
            TrustPositionMessages.ACCOUNT_HAS_EARLIER_ENTRIES,
            TrustPositionMessages.HAS_EARLIER_ENTRIES_MESSAGE);
      }
    } else {
      LocalDate takenOnAt = account.getTakeOnCutoverDate();
      if (takenOnAt != null && !takenOnAt.equals(cutover)) {
        return fail(
            TrustPositionMessages.INVESTMENT_TAKEN_ON_AT_OTHER_DATE,
            TrustPositionMessages.investmentTakenOnAtOtherDate(name, takenOnAt));
      }
      if (transactions.existsOnOrBeforeOtherThanTakeOnAt(account.getId(), cutover)) {
        return fail(
            kind,
            name,
            TrustPositionMessages.ACCOUNT_HAS_EARLIER_ENTRIES,
            TrustPositionMessages.HAS_EARLIER_ENTRIES_MESSAGE);
      }
    }
    // 5. No completed reconciliation.
    if (reconciliations
        .findFirstByTrustAccountIdAndStatusOrderByPeriodEndDescCompletedAtDesc(
            account.getId(), ReconciliationStatus.COMPLETED)
        .isPresent()) {
      return fail(
          kind,
          name,
          TrustPositionMessages.ACCOUNT_RECONCILED,
          TrustPositionMessages.RECONCILED_MESSAGE);
    }
    // 6. The banking account was never taken on, and no other batch of it is taking it on.
    if (kind == Kind.BANKING) {
      if (account.getTakeOnBatchId() != null || account.getTakeOnCutoverDate() != null) {
        return fail(
            TrustPositionMessages.ACCOUNT_ALREADY_TAKEN_ON,
            TrustPositionMessages.alreadyTakenOn(account.getTakeOnBatchId()));
      }
      var other =
          batches.findFirstByTrustAccountIdAndDatasetAndStatusInAndIdNot(
              account.getId(), TakeOnDataset.TRUST_POSITION, TAKEN_ON, batchId);
      if (other.isPresent()) {
        return fail(
            TrustPositionMessages.ACCOUNT_ALREADY_TAKEN_ON,
            TrustPositionMessages.alreadyTakenOn(other.map(TakeOnBatch::getId).orElseThrow()));
      }
    }
    return Optional.empty();
  }

  private static Optional<Failure> fail(String code, String message) {
    return Optional.of(new Failure(code, message));
  }

  /** On an investment account, a sentence that does not name it is prefixed with its name. */
  private static Optional<Failure> fail(Kind kind, String name, String code, String message) {
    return fail(
        code,
        kind == Kind.INVESTMENT
            ? TrustPositionMessages.onInvestmentAccount(name, message)
            : message);
  }
}
