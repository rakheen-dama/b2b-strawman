package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import io.b2mash.b2b.b2bstrawman.reporting.ReportQuery;
import io.b2mash.b2b.b2bstrawman.reporting.ReportResult;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest.InterestRunRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Composite report combining all 6 sub-reports for a financial year, as required by Section 35 of
 * the Attorneys Act. Derives financial_year_start as financial_year_end - 1 year + 1 day.
 */
@Component
public class Section35DataPackQuery implements ReportQuery {

  private final TrustReceiptsPaymentsQuery receiptsPaymentsQuery;
  private final ClientTrustBalancesQuery clientBalancesQuery;
  private final TrustReconciliationReportQuery reconciliationQuery;
  private final InvestmentRegisterQuery investmentRegisterQuery;
  private final InterestAllocationReportQuery interestAllocationQuery;
  private final InterestRunRepository interestRunRepository;
  private final TrustReconciliationRepository reconciliationRepository;

  public Section35DataPackQuery(
      TrustReceiptsPaymentsQuery receiptsPaymentsQuery,
      ClientTrustBalancesQuery clientBalancesQuery,
      TrustReconciliationReportQuery reconciliationQuery,
      InvestmentRegisterQuery investmentRegisterQuery,
      InterestAllocationReportQuery interestAllocationQuery,
      InterestRunRepository interestRunRepository,
      TrustReconciliationRepository reconciliationRepository) {
    this.receiptsPaymentsQuery = receiptsPaymentsQuery;
    this.clientBalancesQuery = clientBalancesQuery;
    this.reconciliationQuery = reconciliationQuery;
    this.investmentRegisterQuery = investmentRegisterQuery;
    this.interestAllocationQuery = interestAllocationQuery;
    this.interestRunRepository = interestRunRepository;
    this.reconciliationRepository = reconciliationRepository;
  }

  @Override
  public String getSlug() {
    return "section-35-data-pack";
  }

  @Override
  public ReportResult execute(Map<String, Object> parameters, Pageable pageable) {
    return executeAll(parameters);
  }

  @Override
  public ReportResult executeAll(Map<String, Object> parameters) {
    var trustAccountId = ReportParamUtils.requireUuid(parameters, "trust_account_id");
    var financialYearEnd = ReportParamUtils.requireDate(parameters, "financial_year_end");
    var financialYearStart = financialYearEnd.minusYears(1).plusDays(1);

    var allRows = new ArrayList<Map<String, Object>>();
    var sectionSummaries = new ArrayList<Map<String, Object>>();

    // Section 1: Trust Receipts & Payments
    var rpParams =
        Map.<String, Object>of(
            "trust_account_id", trustAccountId,
            "dateFrom", financialYearStart.toString(),
            "dateTo", financialYearEnd.toString());
    var rpResult = receiptsPaymentsQuery.executeAll(rpParams);
    addSection(allRows, sectionSummaries, "Trust Receipts & Payments", rpResult);

    // Section 2: Client Trust Balances (as of year-end)
    var cbParams =
        Map.<String, Object>of(
            "trust_account_id", trustAccountId, "asOfDate", financialYearEnd.toString());
    var cbResult = clientBalancesQuery.executeAll(cbParams);
    addSection(allRows, sectionSummaries, "Client Trust Balances", cbResult);

    // Section 3: Trust Reconciliation (latest on or before year-end)
    assembleReconciliationSection(trustAccountId, financialYearEnd, allRows, sectionSummaries);

    // Section 4: Investment Register
    var irParams = Map.<String, Object>of("trust_account_id", trustAccountId);
    var irResult = investmentRegisterQuery.executeAll(irParams);
    addSection(allRows, sectionSummaries, "Investment Register", irResult);

    // Section 5: Interest Allocation (latest run within year)
    assembleInterestAllocationSection(
        trustAccountId, financialYearStart, financialYearEnd, allRows, sectionSummaries);

    // Section 6: Client Ledger Statements are per-client reports that cannot be included
    // in a composite pack without a customer_id. They are available individually via the
    // client-ledger-statement report slug with trust_account_id + customer_id parameters.
    var ledgerMeta = new LinkedHashMap<String, Object>();
    ledgerMeta.put("sectionName", "Client Ledger Statements");
    ledgerMeta.put("status", "AVAILABLE_INDIVIDUALLY");
    ledgerMeta.put(
        "note",
        "Per-client ledger statements must be generated individually using the "
            + "'client-ledger-statement' report with customer_id parameter");
    ledgerMeta.put("reportSlug", "client-ledger-statement");
    ledgerMeta.put("rowCount", 0);
    sectionSummaries.add(ledgerMeta);

    // Build summary
    var summary = new LinkedHashMap<String, Object>();
    summary.put("sectionCount", sectionSummaries.size());
    summary.put("financialYearStart", financialYearStart.toString());
    summary.put("financialYearEnd", financialYearEnd.toString());
    summary.put("sections", sectionSummaries);

    // Get total trust balance at year end from client balances section
    if (cbResult.summary().containsKey("totalTrustBalance")) {
      summary.put("totalTrustBalanceAtYearEnd", cbResult.summary().get("totalTrustBalance"));
    }

    return new ReportResult(allRows, summary);
  }

  private void assembleReconciliationSection(
      java.util.UUID trustAccountId,
      LocalDate financialYearEnd,
      List<Map<String, Object>> allRows,
      List<Map<String, Object>> sectionSummaries) {

    // Find the latest reconciliation on or before the financial year end
    var allRecons =
        reconciliationRepository.findByTrustAccountIdOrderByPeriodEndDesc(trustAccountId);
    var yearEndRecon =
        allRecons.stream().filter(r -> !r.getPeriodEnd().isAfter(financialYearEnd)).findFirst();

    if (yearEndRecon.isEmpty()) {
      var meta = new LinkedHashMap<String, Object>();
      meta.put("sectionName", "Trust Reconciliation");
      meta.put("note", "No reconciliation found on or before financial year end");
      meta.put("rowCount", 0);
      sectionSummaries.add(meta);
      return;
    }

    // Use the specific reconciliation ID to avoid fetching the absolute latest
    var trParams = new HashMap<String, Object>();
    trParams.put("trust_account_id", trustAccountId);
    trParams.put("reconciliation_id", yearEndRecon.get().getId());
    var reconResult = reconciliationQuery.executeAll(trParams);
    addSection(allRows, sectionSummaries, "Trust Reconciliation", reconResult);
  }

  private void assembleInterestAllocationSection(
      java.util.UUID trustAccountId,
      LocalDate yearStart,
      LocalDate yearEnd,
      List<Map<String, Object>> allRows,
      List<Map<String, Object>> sectionSummaries) {

    // Find the latest interest run within the financial year
    var runs =
        interestRunRepository.findByTrustAccountIdOrderByPeriodEndDesc(trustAccountId).stream()
            .filter(
                r -> !r.getPeriodEnd().isBefore(yearStart) && !r.getPeriodEnd().isAfter(yearEnd))
            .toList();

    if (runs.isEmpty()) {
      var meta = new LinkedHashMap<String, Object>();
      meta.put("sectionName", "Interest Allocation");
      meta.put("note", "No interest runs found in financial year");
      meta.put("rowCount", 0);
      sectionSummaries.add(meta);
      return;
    }

    var latestRun = runs.getFirst();
    var iaParams = new HashMap<String, Object>();
    iaParams.put("trust_account_id", trustAccountId);
    iaParams.put("interest_run_id", latestRun.getId());
    var iaResult = interestAllocationQuery.executeAll(iaParams);
    addSection(allRows, sectionSummaries, "Interest Allocation", iaResult);
  }

  private void addSection(
      List<Map<String, Object>> allRows,
      List<Map<String, Object>> sectionSummaries,
      String sectionName,
      ReportResult result) {

    // Add section marker to each row
    for (var row : result.rows()) {
      var taggedRow = new LinkedHashMap<>(row);
      taggedRow.put("_section", sectionName);
      allRows.add(taggedRow);
    }

    // Add section summary metadata
    var sectionMeta = new LinkedHashMap<String, Object>();
    sectionMeta.put("sectionName", sectionName);
    sectionMeta.put("rowCount", result.rows().size());
    if (result.summary() != null) {
      sectionMeta.putAll(result.summary());
    }
    sectionSummaries.add(sectionMeta);
  }
}
