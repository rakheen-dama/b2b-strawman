package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.reporting.ReportQuery;
import io.b2mash.b2b.b2bstrawman.reporting.ReportResult;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.IntegrityMessages;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedgerQueryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.UnidentifiedItemBalanceRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * The unidentified deposits age analysis (Phase 92 650C, architecture §16.8.4) — slug {@code
 * unidentified-deposits-age-analysis}.
 *
 * <p>Parameters: {@code asOfDate} (required) and {@code trust_account_id} (optional; omitted =
 * every trust account of the firm). The items open as at the date — by each item's balance as at
 * the date from its own rows (EQ-4), never by its current status — in five bands (0–30, 31–90,
 * 91–180 and 181–365 days, over one year: the band L9 turns on), each with its received date, bank
 * description, amount and age; then the items allocated to a client or paid to the Fund in the
 * twelve months to the date, with how they were resolved — the resolution in effect as at the date,
 * from the posted rows and their contras dated on or before it, never the item's current state. Σ
 * open = {@code unidentified(A, D)}.
 */
@Component
public class UnidentifiedDepositsAgeQuery implements ReportQuery {

  static final String BAND_0_30 = "0–30 days";
  static final String BAND_31_90 = "31–90 days";
  static final String BAND_91_180 = "91–180 days";
  static final String BAND_181_365 = "181–365 days";
  static final String BAND_OVER_ONE_YEAR = "Over one year";
  static final String RESOLVED_BAND = "Resolved";
  static final String OPEN_POSITION = "Open";

  private static final List<String> BANDS =
      List.of(BAND_0_30, BAND_31_90, BAND_91_180, BAND_181_365, BAND_OVER_ONE_YEAR);

  private final TrustAccountRepository trustAccountRepository;
  private final TrustLedgerQueryRepository ledgerQueries;
  private final TrustReportQueryRepository reportQueries;
  private final CustomerRepository customerRepository;

  public UnidentifiedDepositsAgeQuery(
      TrustAccountRepository trustAccountRepository,
      TrustLedgerQueryRepository ledgerQueries,
      TrustReportQueryRepository reportQueries,
      CustomerRepository customerRepository) {
    this.trustAccountRepository = trustAccountRepository;
    this.ledgerQueries = ledgerQueries;
    this.reportQueries = reportQueries;
    this.customerRepository = customerRepository;
  }

  @Override
  public String getSlug() {
    return "unidentified-deposits-age-analysis";
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

  /**
   * The age band of an item open as at {@code asOf}, received {@code received}. "Over one year" is
   * exactly L9's predicate ({@code UNIDENTIFIED_OVER_ONE_YEAR}: received before the date less one
   * year), so the report and the integrity flag never disagree across 29 February — an item 366
   * days old that is not yet a year old stays in the 181–365 days band (A-M1).
   */
  static String band(LocalDate received, LocalDate asOf) {
    if (received.isBefore(asOf.minusYears(1))) return BAND_OVER_ONE_YEAR;
    long ageDays = ChronoUnit.DAYS.between(received, asOf);
    if (ageDays <= 30) return BAND_0_30;
    if (ageDays <= 90) return BAND_31_90;
    if (ageDays <= 180) return BAND_91_180;
    return BAND_181_365;
  }

  private record OpenItem(
      TrustAccount account, UnidentifiedItemBalanceRow item, long ageDays, String band) {}

  /** An item resolved as at the date, with its resolution from its posted rows (CR-8). */
  private record ResolvedItem(
      TrustAccount account,
      UnidentifiedItemBalanceRow item,
      TrustReportQueryRepository.UnidentifiedResolutionRow resolution) {}

  @Override
  public ReportResult executeAll(Map<String, Object> parameters) {
    var asOf = ReportParamUtils.requireDate(parameters, "asOfDate");
    var accountId = ReportParamUtils.parseUuid(parameters, "trust_account_id");
    List<TrustAccount> accounts;
    if (accountId != null) {
      accounts =
          List.of(
              trustAccountRepository
                  .findById(accountId)
                  .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", accountId)));
    } else {
      accounts =
          trustAccountRepository.findAll().stream()
              .sorted(
                  Comparator.comparing(TrustAccount::getAccountName, String.CASE_INSENSITIVE_ORDER)
                      .thenComparing(TrustAccount::getId))
              .toList();
    }
    var resolvedFrom = asOf.minusYears(1).plusDays(1);

    var open = new ArrayList<OpenItem>();
    var closedAtTheDate = new LinkedHashMap<UUID, ClosedItem>();
    for (var account : accounts) {
      for (var item : ledgerQueries.unidentifiedItemBalances(account.getId(), asOf)) {
        var amount = item.getAmount();
        var balance = item.getBalance() != null ? item.getBalance() : BigDecimal.ZERO;
        // EQ-4: open as at the date exactly when its balance from its own rows equals its amount
        if (amount != null && amount.signum() > 0 && balance.compareTo(amount) == 0) {
          open.add(
              new OpenItem(
                  account,
                  item,
                  ChronoUnit.DAYS.between(item.getReceivedDate(), asOf),
                  band(item.getReceivedDate(), asOf)));
        } else if (balance.signum() == 0) {
          closedAtTheDate.put(item.getId(), new ClosedItem(account, item));
        }
      }
    }

    // Resolved in the twelve months to the date, by the resolution in effect as at the date from
    // the posted rows — never the item's current status, which a later reversal resets (CR-8). A
    // nil-balance item with no such resolution (its deposit reversed: the statement line was
    // unmatched — the deposit never happened) is not resolved.
    var resolved =
        (closedAtTheDate.isEmpty()
                ? List.<TrustReportQueryRepository.UnidentifiedResolutionRow>of()
                : reportQueries.unidentifiedResolutions(closedAtTheDate.keySet(), asOf))
            .stream()
                .filter(r -> closedAtTheDate.containsKey(r.getUnidentifiedDepositId()))
                // the latest resolution in effect per item (rows come oldest first)
                .collect(
                    Collectors.toMap(
                        TrustReportQueryRepository.UnidentifiedResolutionRow
                            ::getUnidentifiedDepositId,
                        r -> r,
                        (earlier, later) -> later,
                        LinkedHashMap::new))
                .values()
                .stream()
                .filter(r -> !r.getResolvedDate().isBefore(resolvedFrom))
                .map(
                    r -> {
                      var c = closedAtTheDate.get(r.getUnidentifiedDepositId());
                      return new ResolvedItem(c.account(), c.item(), r);
                    })
                .sorted(
                    Comparator.comparing((ResolvedItem r) -> r.resolution().getResolvedDate())
                        .thenComparing(r -> r.item().getReceivedDate())
                        .thenComparing(r -> r.item().getId()))
                .toList();
    var customerIds =
        resolved.stream()
            .map(r -> r.resolution().getCustomerId())
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Map<UUID, String> customerNames =
        customerIds.isEmpty()
            ? Map.of()
            : customerRepository.findByIdIn(customerIds).stream()
                .collect(Collectors.toMap(Customer::getId, Customer::getName));

    var rows = new ArrayList<Map<String, Object>>();
    var bandTotals = new LinkedHashMap<String, BigDecimal>();
    BANDS.forEach(b -> bandTotals.put(b, BigDecimal.ZERO));
    BigDecimal openTotal = BigDecimal.ZERO;
    open.sort(
        Comparator.comparing((OpenItem o) -> BANDS.indexOf(o.band()))
            .thenComparing(o -> o.item().getReceivedDate())
            .thenComparing(o -> o.item().getId()));
    for (var o : open) {
      var band = o.band();
      var row = new LinkedHashMap<String, Object>();
      row.put("accountName", o.account().getAccountName());
      row.put("trustAccountId", o.account().getId());
      row.put("unidentifiedDepositId", o.item().getId());
      row.put("receivedDate", o.item().getReceivedDate().toString());
      row.put("bankDescription", o.item().getBankDescription());
      row.put("amount", o.item().getAmount());
      row.put("ageDays", o.ageDays());
      row.put("band", band);
      row.put("position", OPEN_POSITION);
      rows.add(row);
      bandTotals.merge(band, o.item().getAmount(), BigDecimal::add);
      openTotal = openTotal.add(o.item().getAmount());
    }
    for (var r : resolved) {
      var item = r.item();
      var row = new LinkedHashMap<String, Object>();
      row.put("accountName", r.account().getAccountName());
      row.put("trustAccountId", r.account().getId());
      row.put("unidentifiedDepositId", item.getId());
      row.put("receivedDate", item.getReceivedDate().toString());
      row.put("bankDescription", item.getBankDescription());
      row.put("amount", item.getAmount());
      row.put("ageDays", null);
      row.put("band", RESOLVED_BAND);
      row.put("position", resolution(r.resolution(), customerNames));
      rows.add(row);
    }

    var summary = new LinkedHashMap<String, Object>();
    summary.put("asOfDate", asOf.toString());
    summary.put("openTotal", openTotal);
    // Band totals: keys never contain "days" (the report screen formats money by key name)
    summary.put("upToOneMonthTotal", bandTotals.get(BAND_0_30));
    summary.put("oneToThreeMonthsTotal", bandTotals.get(BAND_31_90));
    summary.put("threeToSixMonthsTotal", bandTotals.get(BAND_91_180));
    summary.put("sixToTwelveMonthsTotal", bandTotals.get(BAND_181_365));
    summary.put("overOneYearTotal", bandTotals.get(BAND_OVER_ONE_YEAR));
    summary.put("openItems", open.size());
    summary.put("resolvedItems", resolved.size());
    summary.put("scopeNote", TrustReportWords.TRUST_ASSETS_NOTE);
    return new ReportResult(List.copyOf(rows), summary);
  }

  /** "Allocated to {client} on {date}" or "Paid to the Fund on {date}". */
  private static String resolution(
      TrustReportQueryRepository.UnidentifiedResolutionRow r, Map<UUID, String> customerNames) {
    var date = IntegrityMessages.date(r.getResolvedDate());
    if (TrustTransactionType.UNIDENT_ALLOC_OUT.name().equals(r.getTxType())) {
      var client =
          r.getCustomerId() != null
              ? customerNames.getOrDefault(r.getCustomerId(), "a client")
              : "a client";
      return "Allocated to " + client + " on " + date;
    }
    return "Paid to the Fund on " + date;
  }

  private record ClosedItem(TrustAccount account, UnidentifiedItemBalanceRow item) {}
}
