package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.reporting.ReportQuery;
import io.b2mash.b2b.b2bstrawman.reporting.ReportResult;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliation;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * The reconciliation report: bank, cashbook and trust creditors compared at the reconciliation's
 * period end. If reconciliation_id is provided, uses that specific reconciliation; otherwise uses
 * the latest.
 *
 * <p>v4 (Phase 92 650C, §16.8.5): prints both comparisons — adjusted bank against the cashbook
 * (EQ-1) and against trust creditors — and the creditor breakdown exactly as stored on the
 * reconciliation (V19). Section values and the summary status are words (D-20).
 */
@Component
public class TrustReconciliationReportQuery implements ReportQuery {

  static final String BANK = "Bank";
  static final String ADJUSTMENTS = "Adjustments";
  static final String CASHBOOK_COMPARISON = "Comparison with the cashbook";
  static final String CREDITORS = "Trust creditors";
  static final String CREDITOR_COMPARISON = "Comparison with trust creditors";

  private final TrustReconciliationRepository reconciliationRepository;

  public TrustReconciliationReportQuery(TrustReconciliationRepository reconciliationRepository) {
    this.reconciliationRepository = reconciliationRepository;
  }

  @Override
  public String getSlug() {
    return "trust-reconciliation";
  }

  @Override
  public ReportResult execute(Map<String, Object> parameters, Pageable pageable) {
    return executeAll(parameters);
  }

  @Override
  public ReportResult executeAll(Map<String, Object> parameters) {
    var reconciliationId = ReportParamUtils.parseUuid(parameters, "reconciliation_id");

    TrustReconciliation recon;
    if (reconciliationId != null) {
      recon =
          reconciliationRepository
              .findById(reconciliationId)
              .orElseThrow(
                  () -> new ResourceNotFoundException("TrustReconciliation", reconciliationId));
    } else {
      var trustAccountId = ReportParamUtils.requireUuid(parameters, "trust_account_id");
      var allRecons =
          reconciliationRepository.findByTrustAccountIdOrderByPeriodEndDesc(trustAccountId);
      if (allRecons.isEmpty()) {
        return new ReportResult(
            List.of(),
            Map.of(
                "message",
                "No reconciliations found",
                "scopeNote",
                TrustReportWords.TRUST_ASSETS_NOTE));
      }
      recon = allRecons.getFirst();
    }

    var rows = buildRows(recon);
    var summary = buildSummary(recon);
    return new ReportResult(rows, summary);
  }

  /**
   * v4 (650C, §16.8.5, D-20): both comparisons and the creditor breakdown as stored on the
   * reconciliation, in words — never a section code. Stored values only.
   */
  private List<Map<String, Object>> buildRows(TrustReconciliation recon) {
    var rows = new ArrayList<Map<String, Object>>();
    rows.add(row(BANK, "Bank balance per statement", recon.getBankBalance()));
    rows.add(row(ADJUSTMENTS, "Add: outstanding deposits", recon.getOutstandingDeposits()));
    rows.add(
        row(ADJUSTMENTS, "Less: outstanding payments", recon.getOutstandingPayments().negate()));
    rows.add(row(ADJUSTMENTS, "Adjusted bank balance", recon.getAdjustedBankBalance()));
    rows.add(row(CASHBOOK_COMPARISON, "Cashbook balance", recon.getCashbookBalance()));
    rows.add(
        row(
            CASHBOOK_COMPARISON,
            "Difference (adjusted bank – cashbook)",
            recon.getAdjustedBankBalance().subtract(recon.getCashbookBalance())));
    rows.add(row(CREDITORS, "Client ledgers", recon.getClientLedgerTotal()));
    rows.add(
        row(CREDITORS, "Section 86(4) investment creditors", recon.getInvestmentCreditorTotal()));
    rows.add(
        row(
            CREDITORS,
            TrustCreditorAccountType.FUND_INTEREST_AND_CHARGES.defaultName(),
            recon.getFundLedgerBalance()));
    rows.add(
        row(
            CREDITORS,
            TrustCreditorAccountType.UNIDENTIFIED_DEPOSITS.defaultName(),
            recon.getUnidentifiedDepositTotal()));
    rows.add(
        row(CREDITORS, "Section 86(3) placements (inter-account)", recon.getInterAccountBalance()));
    rows.add(row(CREDITORS, "Trust creditors total", recon.getCreditorTotal()));
    rows.add(
        row(
            CREDITOR_COMPARISON,
            "Difference (adjusted bank – trust creditors)",
            recon.getDifference()));
    rows.add(
        row(
            CREDITOR_COMPARISON,
            "Difference (cashbook – trust creditors)",
            recon.getCashbookBalance().subtract(recon.getCreditorTotal())));
    return List.copyOf(rows);
  }

  private static Map<String, Object> row(String section, String label, BigDecimal amount) {
    var row = new LinkedHashMap<String, Object>();
    row.put("section", section);
    row.put("label", label);
    row.put("amount", amount);
    return row;
  }

  private Map<String, Object> buildSummary(TrustReconciliation recon) {
    var summary = new LinkedHashMap<String, Object>();
    summary.put("periodEnd", recon.getPeriodEnd().toString());
    summary.put("bankBalance", recon.getBankBalance());
    summary.put("cashbookBalance", recon.getCashbookBalance());
    summary.put("clientLedgerTotal", recon.getClientLedgerTotal());
    summary.put("outstandingDeposits", recon.getOutstandingDeposits());
    summary.put("outstandingPayments", recon.getOutstandingPayments());
    summary.put("adjustedBankBalance", recon.getAdjustedBankBalance());
    summary.put("investmentCreditorTotal", recon.getInvestmentCreditorTotal());
    summary.put("fundLedgerBalance", recon.getFundLedgerBalance());
    summary.put("unidentifiedDepositTotal", recon.getUnidentifiedDepositTotal());
    summary.put("interAccountBalance", recon.getInterAccountBalance());
    summary.put("creditorTotal", recon.getCreditorTotal());
    summary.put("difference", recon.getDifference());
    summary.put("isBalanced", recon.isBalanced());
    summary.put("status", TrustReportWords.reconciliationStatusLabel(recon.getStatus()));
    summary.put("scopeNote", TrustReportWords.TRUST_ASSETS_NOTE);
    return summary;
  }
}
