package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountSection;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.ReconciliationStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedger;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType;

/**
 * The trust reports' wording in one place (Phase 92 650C, architecture §16.8): no report prints a
 * type constant, so every section, basis, status and ledger heading is written here in the Act's
 * and the Rules' terms. The words equal the frontend's ({@code TRUST_ACCOUNT_SECTION_LABELS}, the
 * investments page's {@code STATUS_LABEL}).
 */
final class TrustReportWords {

  /** Every trust report header states this (§16.8); carried as {@code summary.scopeNote} too. */
  static final String TRUST_ASSETS_NOTE =
      "Trust cash, estates and other trust assets are not recorded in Kazi and are not included in"
          + " this comparison.";

  static final String CLIENT_HEADING = "Client";
  static final String S86_4_HEADING = "Section 86(4) investment";
  static final String S86_3_HEADING = "Section 86(3) investment";

  private TrustReportWords() {}

  /** "Section 86(2) trust banking account" / "Section 86(3) …" / "Section 86(4) …". */
  static String sectionLabel(TrustAccountSection section) {
    return switch (section) {
      case S86_2 -> "Section 86(2) trust banking account";
      case S86_3 -> "Section 86(3) investment account";
      case S86_4 -> "Section 86(4) investment account";
    };
  }

  /** "Section 86(3)" / "Section 86(4)" — the register's basis column. */
  static String basisLabel(InvestmentBasis basis) {
    return switch (basis) {
      case FIRM_DISCRETION -> "Section 86(3)";
      case CLIENT_INSTRUCTION -> "Section 86(4)";
    };
  }

  /** An investment's status in words ("Active", "Matured", "Withdrawn", "Cancelled"). */
  static String investmentStatusLabel(String status) {
    if (status == null) {
      return null;
    }
    return switch (status) {
      case "ACTIVE" -> "Active";
      case "MATURED" -> "Matured";
      case "WITHDRAWN" -> "Withdrawn";
      case "CANCELLED" -> "Cancelled";
      default -> sentenceCase(status);
    };
  }

  /** A reconciliation's status in words ("Completed" / "Draft"). */
  static String reconciliationStatusLabel(ReconciliationStatus status) {
    return switch (status) {
      case COMPLETED -> "Completed";
      case DRAFT -> "Draft";
    };
  }

  /**
   * The heading a receipts-and-payments row sits under (D-15): a section 86(4) investment row on a
   * client ledger or the investment creditors' ledger, a section 86(3) placement (inter-account), a
   * non-client creditor by its type, otherwise a client.
   */
  static String ledgerHeading(
      String transactionType, TrustLedger ledger, TrustCreditorAccountType creditorType) {
    if (ledger == null) {
      return CLIENT_HEADING;
    }
    return switch (ledger) {
      case INTER_ACCOUNT -> S86_3_HEADING;
      case CLIENT_INVESTMENT -> S86_4_HEADING;
      case NON_CLIENT ->
          creditorType != null
              ? creditorType.defaultName()
              : TrustCreditorAccountType.FUND_INTEREST_AND_CHARGES.defaultName();
      case CLIENT -> isInvestmentType(transactionType) ? S86_4_HEADING : CLIENT_HEADING;
    };
  }

  private static boolean isInvestmentType(String transactionType) {
    return TrustTransactionType.find(transactionType)
        .map(TrustTransactionType::isInvestmentRow)
        .orElse(false);
  }

  private static String sentenceCase(String code) {
    var words = code.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    return words.isEmpty() ? words : Character.toUpperCase(words.charAt(0)) + words.substring(1);
  }
}
