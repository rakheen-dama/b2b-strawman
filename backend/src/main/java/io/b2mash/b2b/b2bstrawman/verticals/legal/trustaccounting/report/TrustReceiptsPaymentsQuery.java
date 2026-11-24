package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.reporting.ReportQuery;
import io.b2mash.b2b.b2bstrawman.reporting.ReportResult;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.IntegrityMessages;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon.TakeOnStatementLines;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionTypeLabels;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Chronological journal of all trust receipts and payments of a trust account for a date range —
 * the cashbook order, every posted row by its own date: a reversed original and its contra both
 * count (649B), each with its own cashbook effect.
 *
 * <p>v4 (Phase 92 650C, §16.8.5, D-15): receipt or payment and the running balance come from the
 * row's {@code cashbook_effect} (a cashbook-neutral row — a transfer between clients, an
 * unidentified allocation — shows 0 / 0); the type is the registry's firm label; each row carries
 * the ledger heading it sits under ("Client", "Section 86(4) investment", "Section 86(3)
 * investment", the Fund, "Unidentified deposits") and the summary totals each heading.
 *
 * <p>Phase 93 (657D, §17.3.15): the take-on is one brought-forward figure, never a movement.
 * Take-on rows are not listed; when the range contains the account's cutover date C the report
 * opens with one line dated C, "Balance brought forward — take-on", credit CB (the cashbook the
 * take-on posted), {@code broughtForward: true}, and the running balance starts from it. The totals
 * exclude that line; {@code summary.broughtForward} carries it with {@code
 * summary.broughtForwardNote}. A range starting after C is unchanged (its running balance starts at
 * 0, as for every range — P-21).
 */
@Component
public class TrustReceiptsPaymentsQuery implements ReportQuery {

  private final TrustTransactionRepository transactionRepository;
  private final CustomerRepository customerRepository;
  private final TrustCreditorAccountRepository creditorAccountRepository;
  private final TrustAccountRepository trustAccountRepository;

  /** The row key marking the take-on brought-forward line (657D). */
  public static final String BROUGHT_FORWARD = "broughtForward";

  public TrustReceiptsPaymentsQuery(
      TrustTransactionRepository transactionRepository,
      CustomerRepository customerRepository,
      TrustCreditorAccountRepository creditorAccountRepository,
      TrustAccountRepository trustAccountRepository) {
    this.transactionRepository = transactionRepository;
    this.customerRepository = customerRepository;
    this.creditorAccountRepository = creditorAccountRepository;
    this.trustAccountRepository = trustAccountRepository;
  }

  /** Whether {@code row} is the take-on brought-forward line. */
  public static boolean isBroughtForward(Map<String, Object> row) {
    return Boolean.TRUE.equals(row.get(BROUGHT_FORWARD));
  }

  @Override
  public String getSlug() {
    return "trust-receipts-payments";
  }

  @Override
  public java.util.Optional<String> requiredCapability() {
    return java.util.Optional.of(io.b2mash.b2b.b2bstrawman.orgrole.Capability.VIEW_TRUST.name());
  }

  @Override
  public ReportResult execute(Map<String, Object> parameters, Pageable pageable) {
    var allRows = queryRows(parameters);
    var summary = computeSummary(allRows);

    int offset = (int) pageable.getOffset();
    int size = pageable.getPageSize();
    int total = allRows.size();
    int totalPages = (total + size - 1) / size;

    var pagedRows = allRows.subList(Math.min(offset, total), Math.min(offset + size, total));
    return new ReportResult(pagedRows, summary, total, totalPages);
  }

  @Override
  public ReportResult executeAll(Map<String, Object> parameters) {
    var allRows = queryRows(parameters);
    var summary = computeSummary(allRows);
    return new ReportResult(allRows, summary);
  }

  private List<Map<String, Object>> queryRows(Map<String, Object> parameters) {
    var trustAccountId = ReportParamUtils.requireUuid(parameters, "trust_account_id");
    var dateFrom = ReportParamUtils.requireDate(parameters, "dateFrom");
    var dateTo = ReportParamUtils.requireDate(parameters, "dateTo");

    // Query transactions filtered by date range and status at database level
    var transactions =
        transactionRepository.findForReceiptsPayments(trustAccountId, dateFrom, dateTo);

    // Batch-load customer names
    var customerIds =
        transactions.stream()
            .map(t -> t.getCustomerId())
            .filter(id -> id != null)
            .collect(Collectors.toSet());
    var customerNames =
        customerRepository.findByIdIn(customerIds).stream()
            .collect(Collectors.toMap(c -> c.getId(), c -> c.getName()));
    Map<UUID, TrustCreditorAccountType> creditorTypes =
        creditorAccountRepository.findByTrustAccountId(trustAccountId).stream()
            .collect(
                Collectors.toMap(
                    TrustCreditorAccount::getId, TrustCreditorAccount::getAccountType));

    // Build rows sorted by date ascending
    var sorted =
        transactions.stream()
            .sorted(
                (a, b) -> {
                  int dateCompare = a.getTransactionDate().compareTo(b.getTransactionDate());
                  if (dateCompare != 0) return dateCompare;
                  return a.getCreatedAt().compareTo(b.getCreatedAt());
                })
            .toList();

    BigDecimal runningBalance = BigDecimal.ZERO;
    var rows = new ArrayList<Map<String, Object>>();
    LocalDate cutover = trustAccountRepository.findTakeOnCutoverDate(trustAccountId).orElse(null);
    if (cutover != null && !cutover.isBefore(dateFrom) && !cutover.isAfter(dateTo)) {
      // 657D (§17.3.15): the take-on as one brought-forward line, read in this transaction
      var broughtForward = transactionRepository.sumTakeOnCashbookEffect(trustAccountId);
      var row = new LinkedHashMap<String, Object>();
      row.put("date", cutover.toString());
      row.put("reference", null);
      row.put("type", TakeOnStatementLines.LABEL);
      row.put("clientName", "-");
      row.put("ledgerHeading", "");
      row.put("credit", broughtForward);
      row.put("debit", BigDecimal.ZERO);
      row.put("balance", broughtForward);
      row.put(BROUGHT_FORWARD, true);
      runningBalance = broughtForward;
      rows.add(row);
    }
    for (var tx : sorted) {
      var row = new LinkedHashMap<String, Object>();
      row.put("date", tx.getTransactionDate().toString());
      row.put("reference", tx.getReference());
      row.put("type", TrustTransactionTypeLabels.firmLabel(tx.getTransactionType()));
      row.put(
          "clientName",
          tx.getCustomerId() != null ? customerNames.getOrDefault(tx.getCustomerId(), "-") : "-");

      row.put(
          "ledgerHeading",
          TrustReportWords.ledgerHeading(
              tx.getTransactionType(),
              tx.getLedger(),
              tx.getCreditorAccountId() != null
                  ? creditorTypes.get(tx.getCreditorAccountId())
                  : null));

      // v4 (650C, D-15): receipt or payment by the row's own cashbook effect (ADR-378) — the
      // cashbook journal. A cashbook-neutral row (a transfer between clients, an unidentified
      // allocation) moves neither column and stays listed under its heading.
      var effect = tx.getCashbookEffect();
      if (effect.signum() > 0) {
        row.put("credit", effect);
        row.put("debit", BigDecimal.ZERO);
      } else if (effect.signum() < 0) {
        row.put("credit", BigDecimal.ZERO);
        row.put("debit", effect.negate());
      } else {
        row.put("credit", BigDecimal.ZERO);
        row.put("debit", BigDecimal.ZERO);
      }
      runningBalance = runningBalance.add(effect);
      row.put("balance", runningBalance);
      rows.add(row);
    }
    return rows;
  }

  private Map<String, Object> computeSummary(List<Map<String, Object>> rows) {
    var summary = new LinkedHashMap<String, Object>();
    BigDecimal totalReceipts = BigDecimal.ZERO;
    BigDecimal totalPayments = BigDecimal.ZERO;
    Map<String, Object> broughtForward = null;
    var movements = new ArrayList<Map<String, Object>>();
    for (var row : rows) {
      if (isBroughtForward(row)) {
        broughtForward = row;
      } else {
        movements.add(row);
      }
    }

    for (var row : movements) {
      totalReceipts = totalReceipts.add((BigDecimal) row.get("credit"));
      totalPayments = totalPayments.add((BigDecimal) row.get("debit"));
    }

    summary.put("totalReceipts", totalReceipts);
    summary.put("totalPayments", totalPayments);
    summary.put("netMovement", totalReceipts.subtract(totalPayments));
    summary.put("transactionCount", movements.size());

    // Per-heading totals (a nested list: the report screen shows flat values only)
    var byHeading = new LinkedHashMap<String, BigDecimal[]>();
    for (var row : movements) {
      var totals =
          byHeading.computeIfAbsent(
              (String) row.get("ledgerHeading"),
              h -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
      totals[0] = totals[0].add((BigDecimal) row.get("credit"));
      totals[1] = totals[1].add((BigDecimal) row.get("debit"));
    }
    var headings = new ArrayList<Map<String, Object>>();
    byHeading.forEach(
        (heading, totals) -> {
          var h = new LinkedHashMap<String, Object>();
          h.put("heading", heading);
          h.put("receipts", totals[0]);
          h.put("payments", totals[1]);
          headings.add(h);
        });
    summary.put("headings", List.copyOf(headings));
    summary.put("scopeNote", TrustReportWords.TRUST_ASSETS_NOTE);
    if (broughtForward != null) {
      summary.put("broughtForward", broughtForward.get("credit"));
      summary.put(
          "broughtForwardNote",
          broughtForwardNote(LocalDate.parse((String) broughtForward.get("date"))));
    }
    return summary;
  }

  /** The §17.10 X3 footnote under a report that carries the take-on brought-forward line. */
  public static String broughtForwardNote(LocalDate cutover) {
    return "The opening position taken on at "
        + IntegrityMessages.date(cutover)
        + " is shown as one brought-forward line, not as receipts.";
  }
}
