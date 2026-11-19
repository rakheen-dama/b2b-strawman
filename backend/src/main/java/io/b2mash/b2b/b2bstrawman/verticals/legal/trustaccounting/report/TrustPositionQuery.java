package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.reporting.ReportQuery;
import io.b2mash.b2b.b2bstrawman.reporting.ReportResult;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.IntegrityFlag;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService.TrustIntegrityReport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * The as-at trust position (Phase 92 650A, architecture §16.8.2) — slug {@code trust-position}. The
 * report definition is shipped by the trust-reports pack v4 (650C).
 *
 * <p>Parameters: {@code asOfDate} (required, any date — the auditor's second test date) and {@code
 * trust_account_id} (optional; omitted = firm-wide). Per account: the cashbook and each creditor
 * term as at the date, the difference (EQ-2) and the integrity flags as at the date; the bank side
 * only where a reconciliation of the account has that period end. Summary: the firm totals and
 * EQ-3. Source: {@link TrustIntegrityService#checkFirm} only.
 */
@Component
public class TrustPositionQuery implements ReportQuery {

  static final String NO_BANK_NOTE =
      "No reconciliation has this period end — the cashbook is compared with trust creditors only";

  static final String NO_FLAGS = "No integrity flags";

  private final TrustIntegrityService trustIntegrityService;
  private final TrustAccountRepository trustAccountRepository;

  public TrustPositionQuery(
      TrustIntegrityService trustIntegrityService, TrustAccountRepository trustAccountRepository) {
    this.trustIntegrityService = trustIntegrityService;
    this.trustAccountRepository = trustAccountRepository;
  }

  @Override
  public String getSlug() {
    return "trust-position";
  }

  @Override
  public ReportResult execute(Map<String, Object> parameters, Pageable pageable) {
    var all = executeAll(parameters);
    var rows = all.rows();
    int offset = (int) pageable.getOffset();
    int size = pageable.getPageSize();
    int total = rows.size();
    int totalPages = (total + size - 1) / size;
    return new ReportResult(
        rows.subList(Math.min(offset, total), Math.min(offset + size, total)),
        all.summary(),
        total,
        totalPages);
  }

  @Override
  public ReportResult executeAll(Map<String, Object> parameters) {
    var asOfDate = ReportParamUtils.requireDate(parameters, "asOfDate");
    var accountId = ReportParamUtils.parseUuid(parameters, "trust_account_id");
    if (accountId != null && !trustAccountRepository.existsById(accountId)) {
      throw new ResourceNotFoundException("TrustAccount", accountId);
    }

    var firm = trustIntegrityService.checkFirm(asOfDate);
    var accounts =
        trustAccountRepository.findAll().stream()
            .collect(Collectors.toMap(TrustAccount::getId, Function.identity()));

    List<Map<String, Object>> rows =
        firm.accounts().stream()
            .filter(r -> accountId == null || accountId.equals(r.trustAccountId()))
            .map(r -> row(r, accounts.get(r.trustAccountId())))
            .toList();

    var summary = new LinkedHashMap<String, Object>();
    summary.put("asOfDate", firm.asAt().toString());
    summary.put("cashbook", firm.cashbook());
    summary.put("clientLedgers", firm.clientLedgers());
    summary.put("investmentCreditors", firm.investmentCreditors());
    summary.put("fundLedger", firm.fundLedger());
    summary.put("unidentifiedDeposits", firm.unidentifiedDeposits());
    summary.put("otherNonClient", firm.otherNonClient());
    summary.put("interAccount", firm.interAccount());
    summary.put("creditorTotal", firm.creditorTotal());
    summary.put("difference", firm.difference());
    summary.put("balanced", firm.balanced());
    summary.put("firmFlags", flags(firm.flags()));
    summary.put("accountCount", rows.size());
    summary.put("scopeNote", TrustReportWords.TRUST_ASSETS_NOTE);
    return new ReportResult(rows, summary);
  }

  private static Map<String, Object> row(TrustIntegrityReport report, TrustAccount account) {
    var p = report.position();
    var row = new LinkedHashMap<String, Object>();
    row.put("trustAccountId", report.trustAccountId());
    row.put("accountName", account != null ? account.getAccountName() : null);
    row.put("accountType", account != null ? account.getAccountType().name() : null);
    // 650C (D-20): the section in words for the report columns; the raw type stays for callers
    row.put(
        "sectionLabel",
        account != null ? TrustReportWords.sectionLabel(account.getSection()) : null);
    row.put("cashbook", p.cashbook());
    row.put("clientLedgers", p.clientLedgers());
    row.put("investmentCreditors", p.investmentCreditors());
    row.put("fundLedger", p.fundLedger());
    row.put("unidentifiedDeposits", p.unidentifiedDeposits());
    row.put("otherNonClient", p.otherNonClient());
    row.put("interAccount", p.interAccount());
    row.put("creditorTotal", p.creditorTotal());
    row.put("difference", p.difference());
    row.put("bankBalance", report.bank() != null ? report.bank().bankBalance() : null);
    row.put(
        "adjustedBankBalance", report.bank() != null ? report.bank().adjustedBankBalance() : null);
    row.put("bankNote", report.bank() != null ? null : NO_BANK_NOTE);
    row.put("balanced", report.balanced());
    row.put("flags", flags(report.flags()));
    row.put("integrity", integrity(report.flags()));
    return row;
  }

  /** The flags of one account as one sentence for the "Integrity" column (650C, D-20). */
  static String integrity(List<IntegrityFlag> flags) {
    if (flags.isEmpty()) {
      return NO_FLAGS;
    }
    return flags.stream().map(IntegrityFlag::message).collect(Collectors.joining(" · "));
  }

  /** The flags as sentences with their severity — never the raw code (no label map here). */
  private static List<Map<String, Object>> flags(List<IntegrityFlag> flags) {
    return flags.stream()
        .map(
            f -> {
              var m = new LinkedHashMap<String, Object>();
              m.put("severity", TrustIntegrityService.isBlocking(f) ? "Blocking" : "Warning");
              m.put("message", f.message());
              return (Map<String, Object>) m;
            })
        .toList();
  }
}
