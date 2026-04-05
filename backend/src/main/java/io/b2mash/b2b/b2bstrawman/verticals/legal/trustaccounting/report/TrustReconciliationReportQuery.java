package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.reporting.ReportQuery;
import io.b2mash.b2b.b2bstrawman.reporting.ReportResult;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliation;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Three-way reconciliation report: bank balance vs cashbook balance vs client ledger total. If
 * reconciliation_id is provided, uses that specific reconciliation; otherwise uses the latest.
 */
@Component
public class TrustReconciliationReportQuery implements ReportQuery {

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
    var trustAccountId = ReportParamUtils.parseUuid(parameters, "trust_account_id");
    var reconciliationId = ReportParamUtils.parseUuid(parameters, "reconciliation_id");

    TrustReconciliation recon;
    if (reconciliationId != null) {
      recon =
          reconciliationRepository
              .findById(reconciliationId)
              .orElseThrow(
                  () -> new ResourceNotFoundException("TrustReconciliation", reconciliationId));
    } else {
      var allRecons =
          reconciliationRepository.findByTrustAccountIdOrderByPeriodEndDesc(trustAccountId);
      if (allRecons.isEmpty()) {
        return new ReportResult(List.of(), Map.of("message", "No reconciliations found"));
      }
      recon = allRecons.getFirst();
    }

    var rows = buildRows(recon);
    var summary = buildSummary(recon);
    return new ReportResult(rows, summary);
  }

  private List<Map<String, Object>> buildRows(TrustReconciliation recon) {
    // Row 1: Bank balance section
    var bankRow = new LinkedHashMap<String, Object>();
    bankRow.put("section", "BANK");
    bankRow.put("label", "Bank Balance per Statement");
    bankRow.put("amount", recon.getBankBalance());

    // Row 2: Outstanding deposits
    var outDepRow = new LinkedHashMap<String, Object>();
    outDepRow.put("section", "ADJUSTMENTS");
    outDepRow.put("label", "Add: Outstanding Deposits");
    outDepRow.put("amount", recon.getOutstandingDeposits());

    // Row 3: Outstanding payments
    var outPayRow = new LinkedHashMap<String, Object>();
    outPayRow.put("section", "ADJUSTMENTS");
    outPayRow.put("label", "Less: Outstanding Payments");
    outPayRow.put("amount", recon.getOutstandingPayments().negate());

    // Row 4: Adjusted bank balance
    var adjRow = new LinkedHashMap<String, Object>();
    adjRow.put("section", "ADJUSTED");
    adjRow.put("label", "Adjusted Bank Balance");
    adjRow.put("amount", recon.getAdjustedBankBalance());

    // Row 5: Cashbook balance
    var cashRow = new LinkedHashMap<String, Object>();
    cashRow.put("section", "COMPARISON");
    cashRow.put("label", "Cashbook Balance");
    cashRow.put("amount", recon.getCashbookBalance());

    // Row 6: Client ledger total
    var ledgerRow = new LinkedHashMap<String, Object>();
    ledgerRow.put("section", "COMPARISON");
    ledgerRow.put("label", "Client Ledger Total");
    ledgerRow.put("amount", recon.getClientLedgerTotal());

    return List.of(bankRow, outDepRow, outPayRow, adjRow, cashRow, ledgerRow);
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
    summary.put("isBalanced", recon.isBalanced());
    summary.put("status", recon.getStatus().name());
    return summary;
  }
}
