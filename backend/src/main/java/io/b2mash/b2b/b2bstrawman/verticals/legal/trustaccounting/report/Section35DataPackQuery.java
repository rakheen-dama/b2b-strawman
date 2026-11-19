package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import io.b2mash.b2b.b2bstrawman.reporting.ReportQuery;
import io.b2mash.b2b.b2bstrawman.reporting.ReportResult;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountSection;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.IntegrityMessages;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.InvestmentTieRow;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedgerQueryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Composite report for a financial year: the annual trust account audit / reporting pack under the
 * Legal Practice Act 28 of 2014 and the LPC Rules (rule 54). The "Section 35" name is historical —
 * section 35 of the repealed Attorneys Act 53 of 1979 — and is kept as the report's identity.
 * Derives financial_year_start as financial_year_end - 1 year + 1 day.
 *
 * <p>v4 (Phase 92 650C, architecture §16.8.5), seven parts: (1) receipts and payments; (2) the Rule
 * 54.15 trust creditors list as at the year end — clients (general + invested), the Fund line and
 * each open unidentified deposit, the locked list's figures at a locked month end; (3) the latest
 * reconciliation on or before the year end with its stored breakdown; (4) the investments on the
 * books at the year end, by section; (5) the Rule 54.28 interest-and-charges schedule for the year,
 * one block per account (the pack's account and the investment accounts holding its investments);
 * (6) each Fund sweep with its statement file name and line number (Rule 54.14.16 evidence), and
 * the sweeps with no statement line; (7) client ledger statements, available individually.
 */
@Component
public class Section35DataPackQuery implements ReportQuery {

  static final String RECEIPTS_PAYMENTS_SECTION = "Trust Receipts & Payments";
  static final String CREDITORS_SECTION = "Trust creditors (Rule 54.15)";
  static final String RECONCILIATION_SECTION = "Trust Reconciliation";
  static final String S86_3_SECTION = "Section 86(3) Investments (Firm Discretion)";
  static final String S86_4_SECTION = "Section 86(4) Investments (Client Instruction)";
  static final String SCHEDULE_SECTION = "Interest and charges schedule (Rule 54.28)";
  static final String SWEEPS_SECTION = "Fund sweeps and their statement lines (Rule 54.14.16)";
  static final String UNMATCHED_SWEEPS_SECTION = "Fund sweeps with no statement line";
  static final String LEDGER_STATEMENTS_SECTION = "Client Ledger Statements";

  static final String NONE_IN_YEAR = "None in the financial year";
  static final String SCHEDULE_NOTE =
      "Year totals per trust account. Monthly figures: run the Interest and charges schedule (Rule"
          + " 54.28) report for the year.";

  /** The register row key part (4) adds: the investment's balance as at the year end (D-14). */
  private static final String YEAR_END_BALANCE = "yearEndBalance";

  private final TrustReceiptsPaymentsQuery receiptsPaymentsQuery;
  private final ClientTrustBalancesQuery clientBalancesQuery;
  private final TrustReconciliationReportQuery reconciliationQuery;
  private final InvestmentRegisterQuery investmentRegisterQuery;
  private final TrustInterestChargesScheduleQuery scheduleQuery;
  private final TrustReconciliationRepository reconciliationRepository;
  private final TrustReportQueryRepository reportQueries;
  private final TrustLedgerQueryRepository ledgerQueries;
  private final TrustAccountRepository trustAccountRepository;

  public Section35DataPackQuery(
      TrustReceiptsPaymentsQuery receiptsPaymentsQuery,
      ClientTrustBalancesQuery clientBalancesQuery,
      TrustReconciliationReportQuery reconciliationQuery,
      InvestmentRegisterQuery investmentRegisterQuery,
      TrustInterestChargesScheduleQuery scheduleQuery,
      TrustReconciliationRepository reconciliationRepository,
      TrustReportQueryRepository reportQueries,
      TrustLedgerQueryRepository ledgerQueries,
      TrustAccountRepository trustAccountRepository) {
    this.receiptsPaymentsQuery = receiptsPaymentsQuery;
    this.clientBalancesQuery = clientBalancesQuery;
    this.reconciliationQuery = reconciliationQuery;
    this.investmentRegisterQuery = investmentRegisterQuery;
    this.scheduleQuery = scheduleQuery;
    this.reconciliationRepository = reconciliationRepository;
    this.reportQueries = reportQueries;
    this.ledgerQueries = ledgerQueries;
    this.trustAccountRepository = trustAccountRepository;
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

    // (1) Trust Receipts & Payments
    var rpParams =
        Map.<String, Object>of(
            "trust_account_id", trustAccountId,
            "dateFrom", financialYearStart.toString(),
            "dateTo", financialYearEnd.toString());
    var rpResult = receiptsPaymentsQuery.executeAll(rpParams);
    addSection(
        allRows,
        sectionSummaries,
        RECEIPTS_PAYMENTS_SECTION,
        rpResult,
        Section35DataPackQuery::receiptPaymentLine);

    // (2) Trust creditors (Rule 54.15) as at the year end — every line of the list (D-1)
    var cbParams =
        Map.<String, Object>of(
            "trust_account_id", trustAccountId, "asOfDate", financialYearEnd.toString());
    var cbResult = creditorsSection(clientBalancesQuery.executeAll(cbParams));
    addSection(
        allRows,
        sectionSummaries,
        CREDITORS_SECTION,
        cbResult,
        Section35DataPackQuery::creditorLine);

    // (3) Trust Reconciliation (latest on or before year-end)
    assembleReconciliationSection(trustAccountId, financialYearEnd, allRows, sectionSummaries);

    // (4) Investments on the books at the year end, by section (D-14)
    var investments = investmentsOnTheBooks(trustAccountId, financialYearEnd);
    var firmInvestments =
        investments.stream()
            .filter(r -> "FIRM_DISCRETION".equals(r.get("investmentBasis")))
            .toList();
    var clientInvestments =
        investments.stream()
            .filter(r -> "CLIENT_INSTRUCTION".equals(r.get("investmentBasis")))
            .toList();
    if (!firmInvestments.isEmpty()) {
      addSection(
          allRows,
          sectionSummaries,
          S86_3_SECTION,
          new ReportResult(firmInvestments, investmentSummary(firmInvestments)),
          Section35DataPackQuery::investmentLine);
    }
    if (!clientInvestments.isEmpty()) {
      addSection(
          allRows,
          sectionSummaries,
          S86_4_SECTION,
          new ReportResult(clientInvestments, investmentSummary(clientInvestments)),
          Section35DataPackQuery::investmentLine);
    }

    // (5) The Rule 54.28 schedule for the year: the pack's account and the investment accounts
    // holding the investments of part (4) (D-11)
    var scheduleAccounts = scheduleAccounts(trustAccountId, investments);
    assembleScheduleSection(
        scheduleAccounts, financialYearStart, financialYearEnd, allRows, sectionSummaries);

    // (6) Each Fund sweep with its statement line (Rule 54.14.16), and sweeps with no line (D-13)
    assembleSweepSections(
        scheduleAccounts, financialYearStart, financialYearEnd, allRows, sectionSummaries);

    // (7) Client Ledger Statements are per-client reports that cannot be included
    // in a composite pack without a customer_id. They are available individually via the
    // client-ledger-statement report slug with trust_account_id + customer_id parameters.
    var ledgerMeta = new LinkedHashMap<String, Object>();
    ledgerMeta.put("sectionName", LEDGER_STATEMENTS_SECTION);
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
    if (cbResult.summary().containsKey("totalTrustBalance")) {
      summary.put("totalTrustBalanceAtYearEnd", cbResult.summary().get("totalTrustBalance"));
    }
    summary.put("scopeNote", TrustReportWords.TRUST_ASSETS_NOTE);

    return new ReportResult(allRows, summary);
  }

  /**
   * Part (2): every line of the Rule 54.15 list, with the list's comparison keys and the v3 keys
   * ({@code totalTrustBalance}, {@code clientCount}); the note states the list's basis (D-2).
   */
  private static ReportResult creditorsSection(ReportResult list) {
    var s = list.summary();
    var summary = new LinkedHashMap<String, Object>();
    for (var key :
        List.of(
            "totalTrustBalance",
            "clientCount",
            "creditorTotal",
            "investedTotal",
            "fundTotal",
            "unidentifiedTotal",
            "bankTotal",
            "investmentAccountTotal",
            "difference",
            "fullyLocked")) {
      summary.put(key, s.get(key));
    }
    boolean locked = Boolean.TRUE.equals(s.get("fullyLocked"));
    summary.put("source", locked ? "Locked" : "Not locked");
    summary.put("note", creditorsNote(s, locked));
    return new ReportResult(list.rows(), summary);
  }

  @SuppressWarnings("unchecked")
  private static String creditorsNote(Map<String, Object> s, boolean locked) {
    var accounts = (List<Map<String, Object>>) s.getOrDefault("accounts", List.of());
    String sourceNote =
        accounts.isEmpty() || accounts.getFirst().get("sourceNote") == null
            ? ClientTrustBalancesQuery.LIVE_NOTE
            : accounts.getFirst().get("sourceNote").toString();
    if (!locked) {
      return sourceNote;
    }
    var afterLock = (List<Map<String, Object>>) s.getOrDefault("afterLock", List.of());
    if (afterLock.isEmpty()) {
      return sourceNote + ".";
    }
    var periodEnd = afterLock.getFirst().get("periodEnd");
    var net = s.get("afterLockNet") instanceof BigDecimal b ? b : BigDecimal.ZERO;
    return "%s. %d %s dated on or before %s %s made after the lock (net %s): they are in Trust Receipts & Payments, not in this list."
        .formatted(
            sourceNote,
            afterLock.size(),
            afterLock.size() == 1 ? "entry" : "entries",
            periodEnd != null ? IntegrityMessages.date(LocalDate.parse(periodEnd.toString())) : "",
            afterLock.size() == 1 ? "was" : "were",
            IntegrityMessages.rand(net));
  }

  /**
   * The register's investments of the account, each with its balance as at the year end from its
   * posted rows on the holding account ({@code investmentTies}); an investment not yet placed at
   * the year end (nothing placed, nil balance) is not on the books and is left out (D-14).
   */
  private List<Map<String, Object>> investmentsOnTheBooks(UUID trustAccountId, LocalDate yearEnd) {
    var params = new HashMap<String, Object>();
    params.put("trust_account_id", trustAccountId);
    var register = investmentRegisterQuery.executeAll(params).rows();
    var ties = new HashMap<UUID, InvestmentTieRow>();
    register.stream()
        .map(r -> (UUID) r.get("investmentAccountId"))
        .filter(Objects::nonNull)
        .distinct()
        .forEach(
            holding ->
                ledgerQueries
                    .investmentTies(holding, yearEnd)
                    .forEach(t -> ties.put(t.getInvestmentId(), t)));
    var rows = new ArrayList<Map<String, Object>>();
    for (var r : register) {
      var tie = ties.get((UUID) r.get("investmentId"));
      var placed = tie != null && tie.getPlaced() != null ? tie.getPlaced() : BigDecimal.ZERO;
      var balance = tie != null && tie.getBalance() != null ? tie.getBalance() : BigDecimal.ZERO;
      if (placed.signum() == 0 && balance.signum() == 0) {
        continue;
      }
      var row = new LinkedHashMap<String, Object>(r);
      row.put(YEAR_END_BALANCE, balance);
      rows.add(row);
    }
    return rows;
  }

  private static Map<String, Object> investmentSummary(List<Map<String, Object>> rows) {
    var summary = new LinkedHashMap<String, Object>();
    summary.put(
        "totalBalance",
        rows.stream()
            .map(r -> money(r, YEAR_END_BALANCE))
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    summary.put("investmentCount", rows.size());
    return summary;
  }

  /** The pack's account, then each other account holding one of its investments (D-11). */
  private List<TrustAccount> scheduleAccounts(
      UUID trustAccountId, List<Map<String, Object>> investments) {
    var ids = new LinkedHashSet<UUID>();
    ids.add(trustAccountId);
    investments.stream()
        .map(r -> (UUID) r.get("investmentAccountId"))
        .filter(Objects::nonNull)
        .forEach(ids::add);
    return trustAccountRepository.findAllById(ids);
  }

  private void assembleScheduleSection(
      List<TrustAccount> accounts,
      LocalDate yearStart,
      LocalDate yearEnd,
      List<Map<String, Object>> allRows,
      List<Map<String, Object>> sectionSummaries) {
    var blocks = scheduleQuery.blocks(accounts, yearStart, yearEnd);
    var rows = new ArrayList<Map<String, Object>>();
    var blockSummaries = new ArrayList<Map<String, Object>>();
    for (var block : blocks) {
      var account = block.account();
      var t = block.totals();
      String name = account.getAccountName();
      boolean s864 = account.getSection() == TrustAccountSection.S86_4;
      rows.add(
          scheduleLine(account, name + " — Fund ledger brought forward", t, "fundBroughtForward"));
      rows.add(
          scheduleLine(
              account,
              name + " — gross interest earned (in the cashbook)",
              t,
              "grossInterestCashbook"));
      rows.add(
          scheduleLine(
              account,
              name + " — gross interest earned (at source, memo)",
              t,
              "grossInterestMemo"));
      if (s864) {
        rows.add(
            scheduleLine(account, name + " — client's share (section 86(4))", t, "clientShare"));
        rows.add(
            scheduleLine(
                account, name + " — Fund's 5% credited to the account", t, "fundShareCredited"));
        rows.add(
            scheduleLine(
                account, name + " — Fund's 5% paid at source", t, "fundSharePaidAtSource"));
      }
      var charges = money(t, "chargesRecoverable").add(money(t, "chargesNonRecoverable"));
      rows.add(scheduleRow(account, name + " — gross charges levied (in the cashbook)", charges));
      rows.add(
          scheduleLine(
              account,
              name + " — of which recoverable from the business account",
              t,
              "chargesRecoverable"));
      rows.add(scheduleLine(account, name + " — of which VAT", t, "chargesVat"));
      rows.add(
          scheduleLine(
              account, name + " — gross charges levied (at source, memo)", t, "chargesMemo"));
      rows.add(
          scheduleLine(account, name + " — VAT in charges at source (memo)", t, "chargesMemoVat"));
      rows.add(scheduleLine(account, name + " — charges refunded", t, "chargesRefunded"));
      rows.add(scheduleLine(account, name + " — paid to the Fund", t, "paidToFund"));
      rows.add(
          scheduleLine(account, name + " — Fund ledger carried forward", t, "fundCarriedForward"));

      var b = new LinkedHashMap<String, Object>();
      b.put("trustAccountId", account.getId());
      b.put("accountName", name);
      b.put("sectionLabel", TrustReportWords.sectionLabel(account.getSection()));
      b.putAll(t);
      b.put("months", block.rows());
      blockSummaries.add(b);
    }
    var summary = new LinkedHashMap<String, Object>();
    summary.put("note", SCHEDULE_NOTE);
    summary.put("accountCount", blocks.size());
    summary.put("blocks", List.copyOf(blockSummaries));
    // A schedule is not additive (b/f, movements, c/f): no Total row
    addSection(allRows, sectionSummaries, SCHEDULE_SECTION, new ReportResult(rows, summary), null);
  }

  private static Map<String, Object> scheduleLine(
      TrustAccount account, String label, Map<String, Object> totals, String key) {
    return scheduleRow(account, label, money(totals, key));
  }

  private static Map<String, Object> scheduleRow(
      TrustAccount account, String label, BigDecimal amount) {
    var row = new LinkedHashMap<String, Object>();
    row.put("trustAccountId", account.getId());
    row.put("label", label);
    row.put("amount", amount);
    return row;
  }

  private void assembleSweepSections(
      List<TrustAccount> accounts,
      LocalDate yearStart,
      LocalDate yearEnd,
      List<Map<String, Object>> allRows,
      List<Map<String, Object>> sectionSummaries) {
    var ids = accounts.stream().map(TrustAccount::getId).toList();
    var sweeps =
        ids.isEmpty()
            ? List.<TrustReportQueryRepository.FundSweepRow>of()
            : reportQueries.fundSweeps(ids, yearStart, yearEnd);
    var matched = new ArrayList<Map<String, Object>>();
    var unmatched = new ArrayList<Map<String, Object>>();
    for (var sweep : sweeps) {
      var row = new LinkedHashMap<String, Object>();
      row.put("transactionId", sweep.getTransactionId());
      row.put("trustAccountId", sweep.getTrustAccountId());
      row.put("date", sweep.getTransactionDate().toString());
      row.put("reference", sweep.getReference());
      row.put("sweepAmount", sweep.getAmount());
      row.put("fileName", sweep.getFileName());
      row.put("lineNumber", sweep.getLineNumber());
      row.put("lineDate", sweep.getLineDate() != null ? sweep.getLineDate().toString() : null);
      (sweep.getLineNumber() != null ? matched : unmatched).add(row);
    }
    addSweepSection(
        allRows, sectionSummaries, SWEEPS_SECTION, matched, Section35DataPackQuery::sweepLine);
    addSweepSection(
        allRows,
        sectionSummaries,
        UNMATCHED_SWEEPS_SECTION,
        unmatched,
        Section35DataPackQuery::unmatchedSweepLine);
  }

  private void addSweepSection(
      List<Map<String, Object>> allRows,
      List<Map<String, Object>> sectionSummaries,
      String sectionName,
      List<Map<String, Object>> rows,
      Function<Map<String, Object>, Line> projector) {
    var summary = new LinkedHashMap<String, Object>();
    summary.put("sweepCount", rows.size());
    if (rows.isEmpty()) {
      summary.put("note", NONE_IN_YEAR);
    }
    addSection(allRows, sectionSummaries, sectionName, new ReportResult(rows, summary), projector);
  }

  /** {@code date · file · line n · reference} → the amount paid out (negative). */
  private static Line sweepLine(Map<String, Object> row) {
    String label =
        Stream.of(
                text(row, "date"),
                text(row, "fileName"),
                "line " + text(row, "lineNumber"),
                text(row, "reference"))
            .filter(part -> !part.isBlank())
            .collect(Collectors.joining(" · "));
    return line(label, money(row, "sweepAmount").negate());
  }

  /** {@code date · reference — no statement line} → the amount paid out (negative). */
  private static Line unmatchedSweepLine(Map<String, Object> row) {
    String label =
        Stream.of(text(row, "date"), text(row, "reference"))
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining(" · "))
            + " — no statement line";
    return line(label, money(row, "sweepAmount").negate());
  }

  private void assembleReconciliationSection(
      UUID trustAccountId,
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
      meta.put("sectionName", RECONCILIATION_SECTION);
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
    // Reconciliation rows already carry label + amount, and they are a computation (bank ->
    // adjusted -> comparison), not an additive list: no Total row.
    addSection(allRows, sectionSummaries, RECONCILIATION_SECTION, reconResult, null);
  }

  /**
   * Adds one sub-report's rows to the pack. G89-F18: the pack's columns are {@code _section},
   * {@code label} and {@code amount}, so every row gets a {@code label} and an {@code amount} from
   * the section's {@code projector} (raw sub-report keys are kept — callers and tests read them),
   * plus one "Total" row per additive section. A {@code null} projector means the rows already
   * carry {@code label} / {@code amount} (reconciliation) and no total is added.
   */
  private void addSection(
      List<Map<String, Object>> allRows,
      List<Map<String, Object>> sectionSummaries,
      String sectionName,
      ReportResult result,
      Function<Map<String, Object>, Line> projector) {

    BigDecimal total = BigDecimal.ZERO;
    for (var row : result.rows()) {
      var taggedRow = new LinkedHashMap<String, Object>(row);
      taggedRow.put("_section", sectionName);
      if (projector != null) {
        Line projected = projector.apply(row);
        taggedRow.put("label", projected.label());
        taggedRow.put("amount", projected.amount());
        total = total.add(projected.amount());
      }
      allRows.add(taggedRow);
    }
    if (projector != null && !result.rows().isEmpty()) {
      var totalRow = new LinkedHashMap<String, Object>();
      totalRow.put("_section", sectionName);
      totalRow.put("label", "Total");
      totalRow.put("amount", total);
      totalRow.put("_total", true);
      allRows.add(totalRow);
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

  /** A pack row's {@code label} and {@code amount}. */
  private record Line(String label, BigDecimal amount) {}

  private static Line line(String label, BigDecimal amount) {
    return new Line(label, amount);
  }

  /**
   * {@code date · reference · client (type)}, plus " — {heading}" for a row not on a client ledger
   * → credit − debit (receipts positive).
   */
  private static Line receiptPaymentLine(Map<String, Object> row) {
    String type = text(row, "type");
    String heading = text(row, "ledgerHeading");
    String label =
        Stream.of(text(row, "date"), text(row, "reference"), text(row, "clientName"))
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining(" · "))
            + (type.isBlank() ? "" : " (" + type + ")")
            + (heading.isBlank() || TrustReportWords.CLIENT_HEADING.equals(heading)
                ? ""
                : " — " + heading);
    return line(label, money(row, "credit").subtract(money(row, "debit")));
  }

  /** {@code client · institution account} → the balance as at the year end (D-14). */
  private static Line investmentLine(Map<String, Object> row) {
    String placement = (text(row, "institution") + " " + text(row, "accountNumber")).trim();
    String label =
        Stream.of(text(row, "clientName"), placement)
            .filter(part -> !part.isBlank())
            .collect(Collectors.joining(" · "));
    return line(label, money(row, YEAR_END_BALANCE));
  }

  /** A creditor line: the person or creditor, an unidentified deposit with its received date. */
  private static Line creditorLine(Map<String, Object> row) {
    String label = text(row, "clientName");
    if ("UNIDENTIFIED".equals(row.get("kind")) && row.get("receivedDate") != null) {
      label = label + " · received " + text(row, "receivedDate");
    }
    return line(label, money(row, "balance"));
  }

  private static String text(Map<String, Object> row, String key) {
    Object value = row.get(key);
    return value != null ? value.toString() : "";
  }

  private static BigDecimal money(Map<String, Object> row, String key) {
    return switch (row.get(key)) {
      case BigDecimal amount -> amount;
      case Number number -> new BigDecimal(number.toString());
      case null, default -> BigDecimal.ZERO;
    };
  }
}
