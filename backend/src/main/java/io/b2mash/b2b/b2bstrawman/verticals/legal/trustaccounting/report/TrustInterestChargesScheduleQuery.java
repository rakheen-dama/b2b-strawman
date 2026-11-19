package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.reporting.ReportQuery;
import io.b2mash.b2b.b2bstrawman.reporting.ReportResult;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountSection;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.IntegrityMessages;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustInterestMemo;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustInterestMemoRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.InterestPresentation;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedgerQueryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * The Rule 54.28 interest-and-charges schedule (Phase 92 650C, architecture §16.8.3) — slug {@code
 * trust-interest-charges-schedule}; replaces {@code interest-allocation}.
 *
 * <p>Parameters: {@code dateFrom} and {@code dateTo} (required, snapped to the first and last day
 * of their months, {@code dateTo} not before {@code dateFrom}; at most {@value #MAX_MONTHS} months)
 * and {@code trust_account_id} (optional; omitted = every trust account of the firm in use at some
 * time in the period — not closed before it, and opened by its end or holding a posting on or
 * before it). One block per account, headed by its section (86(2), 86(3) or 86(4)) — each account
 * has its own Fund ledger and no figure is set off across accounts (OL-9) — and one row per month.
 *
 * <p>The Fund ledger brought forward and carried forward are read from the ledger, never computed,
 * so a month whose movements do not explain the change shows {@code identityHolds = false}. For a
 * section 86(2)/(3) block: b/f + gross interest in the cashbook − gross charges in the cashbook +
 * charges refunded − paid to the Fund = c/f. For a section 86(4) block the interest term is only
 * the Fund's 5% credited to the account. Each column sums its rows' own {@code ledger_effect} by
 * transaction month, a reversal counted under its original's type (D-8): a pair in one month nets
 * to nil (a recorded-in-error line never happened). Memo figures (ADR-384) and a Fund 5% paid at
 * source are separate columns and never enter a ledger figure. Each row and each block's period
 * total carry the check in words ({@code check}); an entry type the schedule has no column for is
 * reported as {@code unexplainedMovement} and the month does not balance.
 */
@Component
public class TrustInterestChargesScheduleQuery implements ReportQuery {

  static final int MAX_MONTHS = 36;

  /** The {@code check} column: the identity holds. */
  static final String CHECK_BALANCED = "Balanced";

  /** The {@code check} column: the movements do not explain the change in the Fund ledger. */
  static final String CHECK_NOT_BALANCED = "Does not balance";

  /**
   * The {@code check} column: the Fund ledger holds an entry type the schedule has no column for.
   */
  static final String CHECK_UNEXPLAINED =
      "Does not balance — the Fund ledger holds an entry the schedule does not recognise";

  private final TrustAccountRepository trustAccountRepository;
  private final TrustCreditorAccountRepository creditorAccountRepository;
  private final TrustLedgerQueryRepository ledgerQueries;
  private final TrustReportQueryRepository reportQueries;
  private final TrustInterestMemoRepository memoRepository;

  public TrustInterestChargesScheduleQuery(
      TrustAccountRepository trustAccountRepository,
      TrustCreditorAccountRepository creditorAccountRepository,
      TrustLedgerQueryRepository ledgerQueries,
      TrustReportQueryRepository reportQueries,
      TrustInterestMemoRepository memoRepository) {
    this.trustAccountRepository = trustAccountRepository;
    this.creditorAccountRepository = creditorAccountRepository;
    this.ledgerQueries = ledgerQueries;
    this.reportQueries = reportQueries;
    this.memoRepository = memoRepository;
  }

  /** One account's schedule: its monthly rows and the period's totals. */
  record Block(TrustAccount account, List<Map<String, Object>> rows, Map<String, Object> totals) {}

  /** The money columns of a row, in column order (totals sum the additive ones). */
  static final List<String> MOVEMENT_COLUMNS =
      List.of(
          "grossInterestCashbook",
          "grossInterestMemo",
          "clientShare",
          "fundShareCredited",
          "fundSharePaidAtSource",
          "chargesRecoverable",
          "chargesNonRecoverable",
          "chargesVat",
          "chargesMemo",
          "chargesMemoVat",
          "chargesRefunded",
          "paidToFund",
          "unexplainedMovement");

  @Override
  public String getSlug() {
    return "trust-interest-charges-schedule";
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
    var dateFrom = ReportParamUtils.requireDate(parameters, "dateFrom");
    var dateTo = ReportParamUtils.requireDate(parameters, "dateTo");
    // The dates themselves are compared (an inverted range inside one month too), then snapped
    if (dateTo.isBefore(dateFrom)) {
      throw new InvalidStateException(
          "Invalid parameter", "Parameter 'dateTo' is before 'dateFrom'");
    }
    var first = YearMonth.from(dateFrom);
    var last = YearMonth.from(dateTo);
    if (first.until(last, ChronoUnit.MONTHS) + 1 > MAX_MONTHS) {
      throw new InvalidStateException(
          "Invalid parameter",
          "The schedule covers at most %d months at a time".formatted(MAX_MONTHS));
    }
    var from = first.atDay(1);
    var to = last.atEndOfMonth();

    var accountId = ReportParamUtils.parseUuid(parameters, "trust_account_id");
    List<TrustAccount> accounts;
    if (accountId != null) {
      accounts =
          List.of(
              trustAccountRepository
                  .findById(accountId)
                  .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", accountId)));
    } else {
      // Every account in use at some time in the period: not closed before it, and opened by its
      // end or holding a posting on or before it (a posting may predate the opened date; an
      // account never yet opened and empty stays out — F-650C-OBS-3)
      var withPostings = Set.copyOf(reportQueries.accountsWithPostingsOnOrBefore(to));
      accounts =
          trustAccountRepository.findAll().stream()
              .filter(a -> a.getClosedDate() == null || !a.getClosedDate().isBefore(from))
              .filter(
                  a ->
                      a.getOpenedDate() == null
                          || !a.getOpenedDate().isAfter(to)
                          || withPostings.contains(a.getId()))
              .toList();
    }

    var blocks = blocks(accounts, from, to);
    var rows = blocks.stream().flatMap(b -> b.rows().stream()).toList();

    var summary = new LinkedHashMap<String, Object>();
    summary.put("periodFrom", from.toString());
    summary.put("periodTo", to.toString());
    summary.put("accountCount", blocks.size());
    // No block, no identity to claim: null ("—"), never a vacuous "Yes" (F-650C-OBS-3)
    summary.put(
        "identityHolds",
        blocks.isEmpty()
            ? null
            : rows.stream().allMatch(r -> Boolean.TRUE.equals(r.get("identityHolds"))));
    summary.put("scopeNote", TrustReportWords.TRUST_ASSETS_NOTE);
    summary.put(
        "blocks",
        blocks.stream()
            .map(
                b -> {
                  var block = new LinkedHashMap<String, Object>();
                  block.put("trustAccountId", b.account().getId());
                  block.put("accountName", b.account().getAccountName());
                  block.put(
                      "sectionLabel", TrustReportWords.sectionLabel(b.account().getSection()));
                  block.putAll(b.totals());
                  return (Map<String, Object>) block;
                })
            .toList());
    return new ReportResult(rows, summary);
  }

  /**
   * The blocks of {@code accounts} for exactly {@code from} to {@code to}, ordered by section, then
   * account name, then id. One row per calendar month the period touches; a month the period only
   * partly covers is a partial row (its own first and last day — e.g. a financial year ending 15
   * March). The standalone report passes whole months (its parameters are snapped); the Section 35
   * data pack passes the exact financial year, so its schedule agrees with its receipts and
   * payments and its sweep evidence.
   */
  List<Block> blocks(List<TrustAccount> accounts, LocalDate from, LocalDate to) {
    return accounts.stream()
        .sorted(
            Comparator.comparing(TrustAccount::getSection)
                .thenComparing(TrustAccount::getAccountName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(TrustAccount::getId))
        .map(a -> block(a, from, to))
        .toList();
  }

  /** One (type, recoverable) group's Σ ledger effect and VAT, for one month. */
  private static final class MonthMovements {
    BigDecimal fundInterest = BigDecimal.ZERO;
    BigDecimal fundInvShare = BigDecimal.ZERO;
    BigDecimal chargesRecoverable = BigDecimal.ZERO;
    BigDecimal chargesNonRecoverable = BigDecimal.ZERO;
    BigDecimal chargesVat = BigDecimal.ZERO;
    BigDecimal refunds = BigDecimal.ZERO;
    BigDecimal sweeps = BigDecimal.ZERO;
    BigDecimal entriesGross = BigDecimal.ZERO;
    BigDecimal entriesNet = BigDecimal.ZERO;
    BigDecimal fundSharePaidAtSource = BigDecimal.ZERO;

    /** Σ ledger effect of groups whose type has no column here; never part of the identity. */
    BigDecimal unexplained = BigDecimal.ZERO;

    /** A group of such a type was met (even one netting to nil): the month does not balance. */
    boolean hasUnexplained;
  }

  private Block block(TrustAccount account, LocalDate from, LocalDate to) {
    var section = account.getSection();
    boolean s864 = section == TrustAccountSection.S86_4;
    var first = YearMonth.from(from);
    var last = YearMonth.from(to);
    UUID fundId =
        creditorAccountRepository
            .findByTrustAccountIdAndAccountType(
                account.getId(), TrustCreditorAccountType.FUND_INTEREST_AND_CHARGES)
            .map(TrustCreditorAccount::getId)
            .orElse(null);

    var movements = new HashMap<YearMonth, MonthMovements>();
    if (fundId != null) {
      for (var g : reportQueries.fundLedgerByMonth(fundId, from, to)) {
        var m =
            movements.computeIfAbsent(
                YearMonth.from(g.getPeriodMonth()), k -> new MonthMovements());
        addFundGroup(m, g);
      }
    }
    if (s864) {
      for (var e : reportQueries.investmentInterestByMonth(account.getId(), from, to)) {
        var m =
            movements.computeIfAbsent(
                YearMonth.from(e.getPeriodMonth()), k -> new MonthMovements());
        m.entriesGross = m.entriesGross.add(orZero(e.getGross()));
        m.entriesNet = m.entriesNet.add(orZero(e.getNet()));
        if (InterestPresentation.NET.name().equals(e.getPresentation())) {
          m.fundSharePaidAtSource = m.fundSharePaidAtSource.add(orZero(e.getFundShare()));
        }
      }
    }
    // A memo is a whole statement month's figure: it belongs to the period holding the month's
    // last day, so consecutive financial years ending mid-month never both count it
    Map<YearMonth, TrustInterestMemo> memos =
        memoRepository
            .findByTrustAccountIdAndPeriodMonthBetweenOrderByPeriodMonthAsc(
                account.getId(), first.atDay(1), last.atDay(1))
            .stream()
            .filter(m -> !YearMonth.from(m.getPeriodMonth()).atEndOfMonth().isAfter(to))
            .collect(Collectors.toMap(m -> YearMonth.from(m.getPeriodMonth()), m -> m));

    var sectionLabel = TrustReportWords.sectionLabel(section);
    var rows = new ArrayList<Map<String, Object>>();
    // b/f of the period and c/f of every row are read from the ledger (never computed)
    BigDecimal broughtForward = fundBalance(fundId, from.minusDays(1));
    for (var month = first; !month.isAfter(last); month = month.plusMonths(1)) {
      var rowFrom = month.equals(first) ? from : month.atDay(1);
      var rowTo = month.equals(last) ? to : month.atEndOfMonth();
      var m = movements.getOrDefault(month, new MonthMovements());
      var memo = memos.get(month);
      var carriedForward = fundBalance(fundId, rowTo);

      var grossInterestCashbook = s864 ? m.entriesGross : m.fundInterest;
      var interestTerm = s864 ? m.fundInvShare : grossInterestCashbook;
      var charges = m.chargesRecoverable.add(m.chargesNonRecoverable);
      boolean identityHolds =
          !m.hasUnexplained
              && broughtForward
                      .add(interestTerm)
                      .subtract(charges)
                      .add(m.refunds)
                      .subtract(m.sweeps)
                      .compareTo(carriedForward)
                  == 0;

      var row = new LinkedHashMap<String, Object>();
      row.put("trustAccountId", account.getId());
      row.put("accountName", account.getAccountName());
      row.put("sectionLabel", sectionLabel);
      row.put("month", periodLabel(month, rowFrom, rowTo));
      row.put("monthStart", rowFrom.toString());
      row.put("fundBroughtForward", broughtForward);
      row.put("grossInterestCashbook", grossInterestCashbook);
      row.put("grossInterestMemo", memo != null ? memo.getGrossInterest() : BigDecimal.ZERO);
      row.put("clientShare", s864 ? m.entriesNet : null);
      row.put("fundShareCredited", s864 ? m.fundInvShare : null);
      row.put("fundSharePaidAtSource", s864 ? m.fundSharePaidAtSource : null);
      row.put("chargesRecoverable", m.chargesRecoverable);
      row.put("chargesNonRecoverable", m.chargesNonRecoverable);
      row.put("chargesVat", m.chargesVat);
      row.put("chargesMemo", memo != null ? memo.getGrossCharges() : BigDecimal.ZERO);
      row.put("chargesMemoVat", memo != null ? memo.getChargesVat() : BigDecimal.ZERO);
      row.put("chargesRefunded", m.refunds);
      row.put("paidToFund", m.sweeps);
      row.put("fundCarriedForward", carriedForward);
      row.put("unexplainedMovement", m.unexplained);
      row.put("identityHolds", identityHolds);
      row.put("check", check(identityHolds, m.hasUnexplained));
      rows.add(row);
      broughtForward = carriedForward;
    }
    return new Block(account, List.copyOf(rows), totals(rows, s864));
  }

  /**
   * Adds one Fund ledger group to its month by its registry type. A type with no column here — a
   * registry type that never moves a Fund ledger, or a code the registry no longer knows — is never
   * dropped: its amount is unexplained and the month does not balance (B-H1).
   */
  private static void addFundGroup(
      MonthMovements m, TrustReportQueryRepository.FundLedgerMonthRow g) {
    var effect = orZero(g.getLedgerEffect());
    var type = TrustTransactionType.find(g.getTxType()).orElse(null);
    if (type == null) {
      addUnexplained(m, effect);
      return;
    }
    switch (type) {
      case FUND_INTEREST -> m.fundInterest = m.fundInterest.add(effect);
      case FUND_INV_SHARE -> m.fundInvShare = m.fundInvShare.add(effect);
      case BANK_CHARGE -> {
        if (Boolean.TRUE.equals(g.getRecoverable())) {
          m.chargesRecoverable = m.chargesRecoverable.subtract(effect);
        } else {
          m.chargesNonRecoverable = m.chargesNonRecoverable.subtract(effect);
        }
        m.chargesVat = m.chargesVat.add(orZero(g.getVat()));
      }
      case CHARGE_REFUND -> m.refunds = m.refunds.add(effect);
      case FUND_SWEEP -> m.sweeps = m.sweeps.subtract(effect);
      default -> addUnexplained(m, effect);
    }
  }

  private static void addUnexplained(MonthMovements m, BigDecimal effect) {
    m.unexplained = m.unexplained.add(effect);
    m.hasUnexplained = true;
  }

  /** The check in words: never a bare boolean in a report a person reads (CR-6). */
  private static String check(boolean identityHolds, boolean hasUnexplained) {
    if (hasUnexplained) {
      return CHECK_UNEXPLAINED;
    }
    return identityHolds ? CHECK_BALANCED : CHECK_NOT_BALANCED;
  }

  /**
   * "March 2026", or "16 March 2025 to 31 March 2025" for a month the period only partly covers.
   */
  private static String periodLabel(YearMonth month, LocalDate rowFrom, LocalDate rowTo) {
    if (rowFrom.equals(month.atDay(1)) && rowTo.equals(month.atEndOfMonth())) {
      return IntegrityMessages.monthYear(rowFrom);
    }
    return IntegrityMessages.date(rowFrom) + " to " + IntegrityMessages.date(rowTo);
  }

  /** The period's totals of one block: b/f of the first month, c/f of the last, Σ the movements. */
  private static Map<String, Object> totals(List<Map<String, Object>> rows, boolean s864) {
    var totals = new LinkedHashMap<String, Object>();
    totals.put("fundBroughtForward", rows.getFirst().get("fundBroughtForward"));
    for (var column : MOVEMENT_COLUMNS) {
      boolean s864Only =
          column.equals("clientShare")
              || column.equals("fundShareCredited")
              || column.equals("fundSharePaidAtSource");
      if (s864Only && !s864) {
        totals.put(column, null);
        continue;
      }
      totals.put(
          column,
          rows.stream()
              .map(r -> (BigDecimal) r.get(column))
              .reduce(BigDecimal.ZERO, BigDecimal::add));
    }
    totals.put("fundCarriedForward", rows.getLast().get("fundCarriedForward"));
    boolean identityHolds =
        rows.stream().allMatch(r -> Boolean.TRUE.equals(r.get("identityHolds")));
    boolean unexplained = rows.stream().anyMatch(r -> CHECK_UNEXPLAINED.equals(r.get("check")));
    totals.put("identityHolds", identityHolds);
    totals.put("check", check(identityHolds, unexplained));
    return totals;
  }

  private BigDecimal fundBalance(UUID fundId, LocalDate asAt) {
    return fundId == null
        ? BigDecimal.ZERO
        : orZero(ledgerQueries.creditorAccountBalance(fundId, asAt));
  }

  private static BigDecimal orZero(BigDecimal amount) {
    return amount != null ? amount : BigDecimal.ZERO;
  }
}
