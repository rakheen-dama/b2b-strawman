package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.reporting.ReportQuery;
import io.b2mash.b2b.b2bstrawman.reporting.ReportResult;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestment;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentInterestRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentInterestRepository.InterestTotals;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.InvestmentBalanceRow;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedgerQueryRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Investment list with status, principal, interest earned for a trust account. Each row's {@code
 * balance} is derived from the posted rows ({@link TrustLedgerQueryRepository#investmentBalances},
 * 648E): what the investment holds now — zero while its placement awaits approval and after the
 * withdrawal.
 *
 * <p>v4 (Phase 92 650C, §16.8.5, D-19): per investment the holding account, the basis and status in
 * words, principal, balance and interest — gross, the Fund's 5% and net ({@code interestEarned}).
 * The raw {@code status} / {@code investmentBasis} keys stay on the rows for callers (D-17) but are
 * no longer columns of the definition. The retired per-account rate key left the rows with the rate
 * table it read (651E, ADR-383): the Fund's statutory share is {@code fundShare}.
 */
@Component
public class InvestmentRegisterQuery implements ReportQuery {

  /** The client column of a section 86(3) investment — the firm's own placement (648A). */
  static final String FIRM_CLIENT_LABEL = "Firm (section 86(3))";

  // Row keys another query reads (Section35DataPackQuery, B-M3): one definition, both sides
  static final String INVESTMENT_ID = "investmentId";
  static final String INVESTMENT_ACCOUNT_ID = "investmentAccountId";
  static final String INVESTMENT_BASIS = "investmentBasis";

  private final TrustInvestmentRepository investmentRepository;
  private final CustomerRepository customerRepository;
  private final TrustLedgerQueryRepository ledgerQueries;
  private final TrustInvestmentInterestRepository interestRepository;
  private final TrustAccountRepository trustAccountRepository;

  public InvestmentRegisterQuery(
      TrustInvestmentRepository investmentRepository,
      CustomerRepository customerRepository,
      TrustLedgerQueryRepository ledgerQueries,
      TrustInvestmentInterestRepository interestRepository,
      TrustAccountRepository trustAccountRepository) {
    this.investmentRepository = investmentRepository;
    this.customerRepository = customerRepository;
    this.ledgerQueries = ledgerQueries;
    this.interestRepository = interestRepository;
    this.trustAccountRepository = trustAccountRepository;
  }

  @Override
  public String getSlug() {
    return "investment-register";
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
    var statusFilter = ReportParamUtils.parseString(parameters, "status");
    var basisFilter = ReportParamUtils.parseString(parameters, "investmentBasis");

    List<TrustInvestment> investments;
    if (basisFilter != null) {
      InvestmentBasis basis;
      try {
        basis = InvestmentBasis.valueOf(basisFilter);
      } catch (IllegalArgumentException e) {
        throw new InvalidStateException(
            "Invalid Investment Basis",
            "Unsupported investmentBasis value: '"
                + basisFilter
                + "'. Valid values are: FIRM_DISCRETION, CLIENT_INSTRUCTION");
      }
      investments =
          investmentRepository
              .findByFundingOrInvestmentAccount(trustAccountId, basis, PageRequest.of(0, 10_000))
              .getContent();
    } else {
      investments =
          investmentRepository
              .findByFundingOrInvestmentAccount(trustAccountId, null, PageRequest.of(0, 10_000))
              .getContent();
    }

    // Filter by status if provided. A CANCELLED investment (its placement payment was rejected, so
    // no money was ever invested; G89-F87) is not on the s86 register — nor, through this query, in
    // the Section 35 data pack — unless it is asked for by status.
    if (statusFilter != null) {
      investments =
          investments.stream().filter(inv -> statusFilter.equals(inv.getStatus())).toList();
    } else {
      investments =
          investments.stream().filter(inv -> !"CANCELLED".equals(inv.getStatus())).toList();
    }

    // Batch-load customer names (a section 86(3) investment names no client — 648A)
    var customerIds =
        investments.stream()
            .map(TrustInvestment::getCustomerId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    var customerNames =
        customerIds.isEmpty()
            ? Map.<UUID, String>of()
            : customerRepository.findByIdIn(customerIds).stream()
                .collect(Collectors.toMap(c -> c.getId(), c -> c.getName()));

    // Batch-load each investment's derived balance (648E; IN () is invalid SQL, so skip when empty)
    var investmentIds = investments.stream().map(TrustInvestment::getId).toList();
    Map<UUID, BigDecimal> balances =
        investmentIds.isEmpty()
            ? Map.of()
            : ledgerQueries.investmentBalances(investmentIds).stream()
                .collect(
                    Collectors.toMap(
                        InvestmentBalanceRow::getInvestmentId, InvestmentBalanceRow::getBalance));
    // 648B (OQ-8 a): interestEarned is the client's net interest — Σ net of the investment's POSTED
    // entries (consistent with balance = principal + net interest); v4 (650C) adds the gross and
    // the Fund's 5% from the same totals. One batch query.
    Map<UUID, InterestTotals> interestTotals =
        investmentIds.isEmpty()
            ? Map.of()
            : interestRepository.sumPostedByInvestmentIds(investmentIds).stream()
                .collect(Collectors.toMap(InterestTotals::getInvestmentId, t -> t, (a, b) -> a));
    // The holding account of each investment, by name (v4 "Investment Account" column)
    var holdingIds =
        investments.stream()
            .map(TrustInvestment::getInvestmentAccountId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Map<UUID, String> holdingNames =
        holdingIds.isEmpty()
            ? Map.of()
            : trustAccountRepository.findAllById(holdingIds).stream()
                .collect(Collectors.toMap(TrustAccount::getId, TrustAccount::getAccountName));

    return investments.stream()
        .map(
            inv -> {
              var row = new LinkedHashMap<String, Object>();
              row.put(INVESTMENT_ID, inv.getId());
              row.put(INVESTMENT_ACCOUNT_ID, inv.getInvestmentAccountId());
              row.put(
                  "clientName",
                  inv.getCustomerId() == null
                      ? FIRM_CLIENT_LABEL
                      : customerNames.getOrDefault(inv.getCustomerId(), "Unknown Client"));
              row.put("institution", inv.getInstitution());
              row.put("accountNumber", inv.getAccountNumber());
              row.put("principal", inv.getPrincipal());
              row.put("balance", balances.getOrDefault(inv.getId(), BigDecimal.ZERO));
              row.put("interestRate", inv.getInterestRate());
              row.put("depositDate", inv.getDepositDate().toString());
              row.put(
                  "maturityDate",
                  inv.getMaturityDate() != null ? inv.getMaturityDate().toString() : null);
              var totals = interestTotals.get(inv.getId());
              row.put("interestEarned", amountOrZero(totals != null ? totals.getNet() : null));
              row.put("grossInterest", amountOrZero(totals != null ? totals.getGross() : null));
              row.put("fundShare", amountOrZero(totals != null ? totals.getFundShare() : null));
              row.put(
                  "investmentAccountName",
                  inv.getInvestmentAccountId() != null
                      ? holdingNames.get(inv.getInvestmentAccountId())
                      : null);
              row.put("status", inv.getStatus());
              row.put("statusLabel", TrustReportWords.investmentStatusLabel(inv.getStatus()));
              row.put(INVESTMENT_BASIS, inv.getInvestmentBasis().name());
              row.put("basisLabel", TrustReportWords.basisLabel(inv.getInvestmentBasis()));
              return (Map<String, Object>) row;
            })
        .toList();
  }

  private static BigDecimal amountOrZero(BigDecimal amount) {
    return amount != null ? amount : BigDecimal.ZERO;
  }

  private Map<String, Object> computeSummary(List<Map<String, Object>> rows) {
    var summary = new LinkedHashMap<String, Object>();
    BigDecimal totalPrincipal = BigDecimal.ZERO;
    BigDecimal totalBalance = BigDecimal.ZERO;
    BigDecimal totalGrossInterest = BigDecimal.ZERO;
    BigDecimal totalFundShare = BigDecimal.ZERO;
    BigDecimal totalInterestEarned = BigDecimal.ZERO;
    int activeCount = 0;

    for (var row : rows) {
      totalPrincipal = totalPrincipal.add((BigDecimal) row.get("principal"));
      totalBalance = totalBalance.add((BigDecimal) row.get("balance"));
      totalGrossInterest = totalGrossInterest.add((BigDecimal) row.get("grossInterest"));
      totalFundShare = totalFundShare.add((BigDecimal) row.get("fundShare"));
      totalInterestEarned = totalInterestEarned.add((BigDecimal) row.get("interestEarned"));
      if ("ACTIVE".equals(row.get("status"))) {
        activeCount++;
      }
    }

    summary.put("totalPrincipal", totalPrincipal);
    summary.put("totalBalance", totalBalance);
    summary.put("totalGrossInterest", totalGrossInterest);
    summary.put("totalFundShare", totalFundShare);
    summary.put("totalInterestEarned", totalInterestEarned);
    summary.put("activeCount", activeCount);
    summary.put("totalCount", rows.size());
    summary.put("scopeNote", TrustReportWords.TRUST_ASSETS_NOTE);
    return summary;
  }
}
