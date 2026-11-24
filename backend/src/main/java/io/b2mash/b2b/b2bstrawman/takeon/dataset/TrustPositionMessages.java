package io.b2mash.b2b.b2bstrawman.takeon.dataset;

import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.IntegrityMessages;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Every user-facing sentence of the trust position dry run (Phase 93 657B), in one place: the
 * §17.10 X3 block and flag texts and the §17.3.9 account-check texts verbatim, and the product copy
 * X3 does not give (D-185; re-checked with all legal copy before release, RG-10). Amounts are the
 * house en-ZA rand ({@link IntegrityMessages#rand}, U+00A0 thousands separator), a negative shown
 * as {@code R-1 250,00} (§17.4.4); a difference quoted in a sentence is its magnitude. Dates are
 * {@code d MMMM yyyy}.
 */
public final class TrustPositionMessages {

  private TrustPositionMessages() {}

  // --- Codes ---

  public static final String ACCOUNT_NOT_BANKING = "ACCOUNT_NOT_BANKING";
  public static final String ACCOUNT_CLOSED = "ACCOUNT_CLOSED";
  public static final String ACCOUNT_OPENED_AFTER_CUTOVER = "ACCOUNT_OPENED_AFTER_CUTOVER";
  public static final String ACCOUNT_HAS_EARLIER_ENTRIES = "ACCOUNT_HAS_EARLIER_ENTRIES";
  public static final String ACCOUNT_RECONCILED = "ACCOUNT_RECONCILED";
  public static final String ACCOUNT_ALREADY_TAKEN_ON = "ACCOUNT_ALREADY_TAKEN_ON";
  public static final String INVESTMENT_TAKEN_ON_AT_OTHER_DATE =
      "INVESTMENT_TAKEN_ON_AT_OTHER_DATE";
  public static final String CUTOVER_NOT_MONTH_END = "CUTOVER_NOT_MONTH_END";

  public static final String CLIENT_IN_DEBIT = "CLIENT_IN_DEBIT";
  public static final String TRUST_DEFICIT = "TRUST_DEFICIT";
  public static final String CONTROL_TOTAL_EQ1 = "CONTROL_TOTAL_EQ1";
  public static final String CONTROL_TOTAL_DEPOSITS = "CONTROL_TOTAL_DEPOSITS";
  public static final String CONTROL_TOTAL_PAYMENTS = "CONTROL_TOTAL_PAYMENTS";
  public static final String CONTROL_TOTAL_EQ2 = "CONTROL_TOTAL_EQ2";
  public static final String CONTROL_TOTAL_INVESTMENTS = "CONTROL_TOTAL_INVESTMENTS";
  public static final String UNIDENTIFIED_DATE_MISSING = "UNIDENTIFIED_DATE_MISSING";
  public static final String CLIENT_MATTER_REQUIRED = "CLIENT_MATTER_REQUIRED";
  public static final String CLIENT_MATTER_UNRESOLVED = "CLIENT_MATTER_UNRESOLVED";
  public static final String CLIENT_MATTER_MISMATCH = "CLIENT_MATTER_MISMATCH";

  public static final String FUND_LEDGER_IN_DEBIT = "FUND_LEDGER_IN_DEBIT";
  public static final String UNIDENTIFIED_OVER_ONE_YEAR = "UNIDENTIFIED_OVER_ONE_YEAR";

  public static final String MATTER_UNRESOLVED = "MATTER_UNRESOLVED";
  public static final String DATE_AFTER_CUTOVER = "DATE_AFTER_CUTOVER";
  public static final String AMOUNT_NOT_POSITIVE = "AMOUNT_NOT_POSITIVE";
  public static final String AMOUNT_NEGATIVE = "AMOUNT_NEGATIVE";
  public static final String RATE_NEGATIVE = "RATE_NEGATIVE";
  public static final String REFERENCE_OR_DESCRIPTION_REQUIRED =
      "REFERENCE_OR_DESCRIPTION_REQUIRED";
  public static final String POSSIBLE_DUPLICATE = "POSSIBLE_DUPLICATE";
  public static final String INVESTMENT_ACCOUNT_UNRESOLVED = "INVESTMENT_ACCOUNT_UNRESOLVED";
  public static final String INVESTMENT_BASIS_MISMATCH = "INVESTMENT_BASIS_MISMATCH";
  public static final String INSTRUCTION_REQUIRED = "INSTRUCTION_REQUIRED";
  public static final String FIRM_INVESTMENT_HAS_CLIENT = "FIRM_INVESTMENT_HAS_CLIENT";
  public static final String CONFIRMATION_MISMATCH = "CONFIRMATION_MISMATCH";
  public static final String INVESTMENT_IN_TRANSIT = "INVESTMENT_IN_TRANSIT";

  // --- Formatting ---

  /** {@code R403 500,00}; a negative as {@code R-1 250,00} (§17.4.4). */
  public static String rands(BigDecimal amount) {
    return amount.signum() < 0
        ? "R-" + IntegrityMessages.rand(amount.abs()).substring(1)
        : IntegrityMessages.rand(amount);
  }

  /** {@code 31 October 2026}. */
  public static String date(LocalDate date) {
    return IntegrityMessages.date(date);
  }

  // --- Account checks (§17.3.9, verbatim) ---

  public static final String ACCOUNT_NOT_BANKING_MESSAGE =
      "Choose the trust banking account to take on. Investment accounts are taken on through their"
          + " investment rows.";

  public static String accountClosed(String accountName) {
    return "%s is closed. Take on only into open trust accounts.".formatted(accountName);
  }

  public static String openedAfterCutover(LocalDate openedDate, LocalDate cutover) {
    return ("The account's opened date (%s) is after the cutover date (%s). Change the opened date"
            + " in Trust accounting before taking on.")
        .formatted(date(openedDate), date(cutover));
  }

  public static final String HAS_EARLIER_ENTRIES_MESSAGE =
      "This account already has transactions dated on or before the cutover date. A take-on must be"
          + " the first entry.";

  public static final String RECONCILED_MESSAGE =
      "This account already has a completed reconciliation.";

  public static String alreadyTakenOn(UUID batchId) {
    return "This account has already been taken on (batch %s).".formatted(batchId);
  }

  public static String investmentTakenOnAtOtherDate(String accountName, LocalDate takenOnAt) {
    return "%s was taken on at %s; take this account on at the same date."
        .formatted(accountName, date(takenOnAt));
  }

  public static final String CUTOVER_NOT_MONTH_END_MESSAGE =
      "The cutover date is not a month end. Confirm a mid-month cutover, and reconcile the previous"
          + " system to this date.";

  /**
   * An account check on a named investment account whose §17.3.9 sentence does not name the account
   * ("This account …"): prefixed with the account's name (D-185), so the message shown above the
   * table of the banking account's batch says which account it means.
   */
  public static String onInvestmentAccount(String accountName, String message) {
    return message.contains(accountName) ? message : accountName + ": " + message;
  }

  // --- Hard blocks (§17.10 X3, verbatim) ---

  public static String clientInDebit(BigDecimal balance) {
    return ("This client's matter is in debit (%s) at the cutover date. No trust creditor may be in"
            + " debit (Rule 54.14.9). The firm must immediately report a debit balance to the"
            + " Council in writing, with the reason and proof that it was rectified (Rule"
            + " 54.14.11). Correct it in the previous system, then upload the corrected file.")
        .formatted(rands(balance));
  }

  public static String deficit(BigDecimal creditors, BigDecimal moneyHeld) {
    return ("The trust creditors (%s) exceed the trust money held (%s) by %s at the cutover date."
            + " This is a trust deficit (Rules 54.14.8 and 54.14.10). Take-on cannot continue"
            + " until it is resolved in the previous system.")
        .formatted(rands(creditors), rands(moneyHeld), rands(creditors.subtract(moneyHeld).abs()));
  }

  public static String eq2(BigDecimal creditors, BigDecimal cashbook) {
    return ("The trust creditors (%s) do not equal the cashbook balance at cutover (%s): difference"
            + " %s. Every creditor group must be included and must agree to the cent.")
        .formatted(rands(creditors), rands(cashbook), rands(creditors.subtract(cashbook).abs()));
  }

  public static String eq1(
      BigDecimal bank,
      BigDecimal deposits,
      BigDecimal payments,
      BigDecimal adjustedBank,
      BigDecimal cashbook) {
    return "Bank balance %s + outstanding deposits %s − outstanding payments %s is %s, not the cashbook balance %s."
        .formatted(
            rands(bank), rands(deposits), rands(payments), rands(adjustedBank), rands(cashbook));
  }

  /** {@code kind}: "deposits" or "payments". */
  public static String listedTotal(String kind, BigDecimal listed, BigDecimal position) {
    return "The outstanding %s listed total %s; the reconciled position says %s."
        .formatted(kind, rands(listed), rands(position));
  }

  public static final String UNIDENTIFIED_DATE_MISSING_MESSAGE =
      "This unidentified deposit has no original deposit date. The date the money was received"
          + " must be kept: the period after which unclaimed money is paid to the Legal"
          + " Practitioners' Fidelity Fund runs from it (section 87(4)).";

  public static String matterMismatch(String matterRef, String clientRef) {
    return ("Matter %s is not a matter of client %s. Every client balance must sit on one of that"
            + " client's matters.")
        .formatted(matterRef, clientRef);
  }

  public static String fundLedgerInDebit(BigDecimal fundLedger) {
    return ("The Legal Practitioners' Fidelity Fund interest and charges ledger is in debit (%s) at"
            + " the cutover date. This is allowed and is flagged, as it would be after take-on.")
        .formatted(rands(fundLedger));
  }

  /** §17.3.7. */
  public static final String INVESTMENT_IN_TRANSIT_MESSAGE =
      "Settle investment movements in the previous system before the cutover date.";

  // --- Product copy (D-185) ---

  /** {@code clientRef} null: the row gives no client either. */
  public static String matterRequired(String clientRef) {
    return clientRef == null
        ? "Enter the client and the matter. Every client balance must sit on one of that client's"
            + " matters."
        : ("Enter the matter of client %s. Every client balance must sit on one of that client's"
                + " matters.")
            .formatted(clientRef);
  }

  public static String matterUnresolved(String matterRef) {
    return "No matter matches %s. Take the matter on first, or correct the reference."
        .formatted(matterRef);
  }

  public static String dateAfterCutover(LocalDate date, LocalDate cutover) {
    return ("The date (%s) is after the cutover date (%s). Take on only what the previous system"
            + " held at the cutover date.")
        .formatted(date(date), date(cutover));
  }

  public static final String AMOUNT_NOT_POSITIVE_MESSAGE = "The amount must be more than R0,00.";

  public static final String AMOUNT_NEGATIVE_MESSAGE = "This amount cannot be negative.";

  public static final String RATE_NEGATIVE_MESSAGE = "The interest rate cannot be negative.";

  public static final String REFERENCE_OR_DESCRIPTION_REQUIRED_MESSAGE =
      "Enter the bank reference or a description of the deposit.";

  public static final String UNIDENTIFIED_OVER_ONE_YEAR_MESSAGE =
      "This deposit was received more than a year before the cutover date. It is taken on with its"
          + " original date and flagged.";

  public static String possibleDuplicateUnidentified(int firstRow) {
    return ("Same date, amount and reference as row %d in this file. Check that it is not entered"
            + " twice.")
        .formatted(firstRow);
  }

  public static String possibleDuplicateOutstanding(int firstRow) {
    return ("Same item type, reference and amount as row %d in this file. Check that it is not"
            + " entered twice.")
        .formatted(firstRow);
  }

  public static String investmentAccountUnresolved(String reference) {
    return ("No investment account matches %s. Open it in Trust accounting first, or correct the"
            + " reference.")
        .formatted(reference);
  }

  /** {@code accountSection} / {@code rowSection}: "86(3)" or "86(4)". */
  public static String investmentBasisMismatch(
      String accountName, String accountSection, String rowSection) {
    return "%s holds section %s money; this row is a section %s investment."
        .formatted(accountName, accountSection, rowSection);
  }

  public static final String INSTRUCTION_REQUIRED_MESSAGE =
      "A section 86(4) investment needs the client's instruction reference, its date and"
          + " confirmation that the account is endorsed under section 86(4) (Rule 54.17.3).";

  public static final String FIRM_INVESTMENT_HAS_CLIENT_MESSAGE =
      "A section 86(3) investment is the firm's own placement and names no client. Clear the client"
          + " and matter.";

  public static String confirmationMismatch(
      BigDecimal principal, BigDecimal fundAmount, BigDecimal balancePerConfirmation) {
    return ("The principal (%s) plus the Legal Practitioners' Fidelity Fund amount (%s) is %s, not"
            + " the balance per confirmation (%s).")
        .formatted(
            rands(principal),
            rands(fundAmount),
            rands(principal.add(fundAmount)),
            rands(balancePerConfirmation));
  }

  public static String investmentsListed(BigDecimal listed, BigDecimal invested) {
    return ("The investments listed total %s; the total invested per the institutions'"
            + " confirmations is %s.")
        .formatted(rands(listed), rands(invested));
  }
}
