package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon;

import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatch;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchRepository;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchStatus;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnDataset;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.IntegrityMessages;
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
 *
 * <p>The account checks' codes and §17.3.9 texts live here ({@link Messages}), beside the checks
 * that produce them, so the dependency runs one way only: the take-on dataset handler reads this
 * class; this class never reads the take-on dataset package.
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
      return fail(Messages.ACCOUNT_NOT_BANKING, Messages.ACCOUNT_NOT_BANKING_MESSAGE);
    }
    // 2. Open.
    if (account.getStatus() != TrustAccountStatus.ACTIVE) {
      return fail(Messages.ACCOUNT_CLOSED, Messages.accountClosed(name));
    }
    // 3. Opened on or before the cutover date.
    if (account.getOpenedDate() != null && account.getOpenedDate().isAfter(cutover)) {
      return fail(
          kind,
          name,
          Messages.ACCOUNT_OPENED_AFTER_CUTOVER,
          Messages.openedAfterCutover(account.getOpenedDate(), cutover));
    }
    // 4. A take-on is the first entry. A shared investment account may already hold the take-on
    //    rows of the other banking account's completed batch at the same date — never another date.
    if (kind == Kind.BANKING) {
      if (transactions.existsOnOrBefore(account.getId(), cutover)) {
        return fail(Messages.ACCOUNT_HAS_EARLIER_ENTRIES, Messages.HAS_EARLIER_ENTRIES_MESSAGE);
      }
    } else {
      LocalDate takenOnAt = account.getTakeOnCutoverDate();
      if (takenOnAt != null && !takenOnAt.equals(cutover)) {
        return fail(
            Messages.INVESTMENT_TAKEN_ON_AT_OTHER_DATE,
            Messages.investmentTakenOnAtOtherDate(name, takenOnAt));
      }
      if (transactions.existsOnOrBeforeOtherThanTakeOnAt(account.getId(), cutover)) {
        return fail(
            kind, name, Messages.ACCOUNT_HAS_EARLIER_ENTRIES, Messages.HAS_EARLIER_ENTRIES_MESSAGE);
      }
    }
    // 5. No completed reconciliation.
    if (reconciliations
        .findFirstByTrustAccountIdAndStatusOrderByPeriodEndDescCompletedAtDesc(
            account.getId(), ReconciliationStatus.COMPLETED)
        .isPresent()) {
      return fail(kind, name, Messages.ACCOUNT_RECONCILED, Messages.RECONCILED_MESSAGE);
    }
    // 6. The banking account was never taken on, and no other batch of it is taking it on.
    if (kind == Kind.BANKING) {
      if (account.getTakeOnBatchId() != null || account.getTakeOnCutoverDate() != null) {
        return fail(
            Messages.ACCOUNT_ALREADY_TAKEN_ON, Messages.alreadyTakenOn(account.getTakeOnBatchId()));
      }
      var other =
          batches.findFirstByTrustAccountIdAndDatasetAndStatusInAndIdNot(
              account.getId(), TakeOnDataset.TRUST_POSITION, TAKEN_ON, batchId);
      if (other.isPresent()) {
        return fail(
            Messages.ACCOUNT_ALREADY_TAKEN_ON,
            Messages.alreadyTakenOn(other.map(TakeOnBatch::getId).orElseThrow()));
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
        code, kind == Kind.INVESTMENT ? Messages.onInvestmentAccount(name, message) : message);
  }

  /**
   * The codes and the user-facing sentences of the account checks (§17.3.9, verbatim; the
   * investment-account prefix is D-185's product copy). Dates are {@code d MMMM yyyy}.
   */
  public static final class Messages {

    private Messages() {}

    public static final String ACCOUNT_NOT_BANKING = "ACCOUNT_NOT_BANKING";
    public static final String ACCOUNT_CLOSED = "ACCOUNT_CLOSED";
    public static final String ACCOUNT_OPENED_AFTER_CUTOVER = "ACCOUNT_OPENED_AFTER_CUTOVER";
    public static final String ACCOUNT_HAS_EARLIER_ENTRIES = "ACCOUNT_HAS_EARLIER_ENTRIES";
    public static final String ACCOUNT_RECONCILED = "ACCOUNT_RECONCILED";
    public static final String ACCOUNT_ALREADY_TAKEN_ON = "ACCOUNT_ALREADY_TAKEN_ON";
    public static final String INVESTMENT_TAKEN_ON_AT_OTHER_DATE =
        "INVESTMENT_TAKEN_ON_AT_OTHER_DATE";

    public static final String ACCOUNT_NOT_BANKING_MESSAGE =
        "Choose the trust banking account to take on. Investment accounts are taken on through"
            + " their investment rows.";

    public static String accountClosed(String accountName) {
      return "%s is closed. Take on only into open trust accounts.".formatted(accountName);
    }

    public static String openedAfterCutover(LocalDate openedDate, LocalDate cutover) {
      return ("The account's opened date (%s) is after the cutover date (%s). Change the opened"
              + " date in Trust accounting before taking on.")
          .formatted(IntegrityMessages.date(openedDate), IntegrityMessages.date(cutover));
    }

    public static final String HAS_EARLIER_ENTRIES_MESSAGE =
        "This account already has transactions dated on or before the cutover date. A take-on must"
            + " be the first entry.";

    public static final String RECONCILED_MESSAGE =
        "This account already has a completed reconciliation.";

    public static String alreadyTakenOn(UUID batchId) {
      return "This account has already been taken on (batch %s).".formatted(batchId);
    }

    public static String investmentTakenOnAtOtherDate(String accountName, LocalDate takenOnAt) {
      return "%s was taken on at %s; take this account on at the same date."
          .formatted(accountName, IntegrityMessages.date(takenOnAt));
    }

    /**
     * An account check on a named investment account whose §17.3.9 sentence does not name the
     * account ("This account …"): prefixed with the account's name (D-185), so the message shown
     * above the table of the banking account's batch says which account it means.
     */
    public static String onInvestmentAccount(String accountName, String message) {
      return message.contains(accountName) ? message : accountName + ": " + message;
    }
  }
}
