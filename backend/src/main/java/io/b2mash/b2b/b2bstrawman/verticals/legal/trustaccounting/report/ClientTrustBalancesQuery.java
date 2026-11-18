package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.reporting.ReportQuery;
import io.b2mash.b2b.b2bstrawman.reporting.ReportResult;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.CreditorLine;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.IntegrityMessages;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustMonthlyList;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustMonthlyListRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustMonthlyListService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustMonthlyListService.AdjustmentEntry;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.LedgerTotals;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.ClientBalanceRow;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedgerQueryRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * The Rule 54.15 trust creditors list (Phase 92 650A, architecture §16.8.1, ADR-382) — the {@code
 * client-trust-balances} slug, evolved rather than paralleled. Query internals only: the report
 * definition (columns, name, parameters) is edited in 650C, so every v3 column is still on every
 * row.
 *
 * <p>Parameters: {@code trust_account_id} (optional — omitted means firm-wide, every trust account
 * of the firm) and {@code asOfDate} (optional, today when absent; a month end to obtain locked
 * lists).
 *
 * <p>Source per account: the locked {@code trust_monthly_lists} row for that account and date where
 * one exists ("Locked {date} by {member}"), otherwise the live list as at the date ("Not locked —
 * this account's reconciliation for the month is not complete"). For each locked account the rows
 * entered after the lock and dated on or before the period end are a separate schedule ({@code
 * summary.afterLock}), never folded into the list.
 *
 * <p>Rows: every person for whom money is held (general + invested balance; firm-wide one row per
 * person across accounts), then one Legal Practitioners' Fidelity Fund line per account, one line
 * per open unidentified deposit and, for one account, the section 86(3) line where non-zero.
 * Firm-wide, section 86(3) placements net to nil across accounts and are a note, not a creditor.
 * {@code balance} is what is held for the row: a person's general + invested balance (the general
 * balance on a trust banking account), a line's balance otherwise.
 */
@Component
public class ClientTrustBalancesQuery implements ReportQuery {

  static final String LIVE_NOTE =
      "Not locked — this account's reconciliation for the month is not complete";
  static final String TRUST_CASH_NOTE = "Trust cash is not recorded in Kazi";
  static final String INTER_ACCOUNT_NOTE =
      "Section 86(3) placements net to nil across the trust accounts and are not listed as a"
          + " creditor.";

  private final TrustLedgerQueryRepository ledgerQueries;
  private final TrustAccountRepository trustAccountRepository;
  private final TrustMonthlyListRepository monthlyListRepository;
  private final TrustMonthlyListService monthlyListService;
  private final MemberNameResolver memberNameResolver;
  private final Clock clock;

  public ClientTrustBalancesQuery(
      TrustLedgerQueryRepository ledgerQueries,
      TrustAccountRepository trustAccountRepository,
      TrustMonthlyListRepository monthlyListRepository,
      TrustMonthlyListService monthlyListService,
      MemberNameResolver memberNameResolver,
      Clock clock) {
    this.ledgerQueries = ledgerQueries;
    this.trustAccountRepository = trustAccountRepository;
    this.monthlyListRepository = monthlyListRepository;
    this.monthlyListService = monthlyListService;
    this.memberNameResolver = memberNameResolver;
    this.clock = clock;
  }

  @Override
  public String getSlug() {
    return "client-trust-balances";
  }

  @Override
  public ReportResult execute(Map<String, Object> parameters, Pageable pageable) {
    var assembled = assemble(parameters);
    var allRows = assembled.rows();

    int offset = (int) pageable.getOffset();
    int size = pageable.getPageSize();
    int total = allRows.size();
    int totalPages = (total + size - 1) / size;

    var pagedRows = allRows.subList(Math.min(offset, total), Math.min(offset + size, total));
    return new ReportResult(pagedRows, assembled.summary(), total, totalPages);
  }

  @Override
  public ReportResult executeAll(Map<String, Object> parameters) {
    var assembled = assemble(parameters);
    return new ReportResult(assembled.rows(), assembled.summary());
  }

  private record Assembled(List<Map<String, Object>> rows, Map<String, Object> summary) {}

  /** One account's list: its lines, where they came from, and its money side. */
  private record AccountList(
      TrustAccount account,
      List<CreditorLine> lines,
      TrustMonthlyList locked,
      String sourceNote,
      BigDecimal moneySide,
      boolean bankKnown,
      List<AdjustmentEntry> afterLock) {

    String source() {
      return locked != null ? "LOCKED" : "LIVE";
    }
  }

  /** A person's balances, merged over the lines in scope (one account, or firm-wide). */
  private static final class Person {
    final UUID clientId;
    String name;
    BigDecimal general = BigDecimal.ZERO;
    BigDecimal invested = BigDecimal.ZERO;
    final List<Map<String, Object>> matters = new ArrayList<>();
    final List<AccountList> sources = new ArrayList<>();

    Person(UUID clientId) {
      this.clientId = clientId;
    }
  }

  private Assembled assemble(Map<String, Object> parameters) {
    var accountId = ReportParamUtils.parseUuid(parameters, "trust_account_id");
    var parsedDate = ReportParamUtils.parseDate(parameters, "asOfDate");
    var asOf = parsedDate != null ? parsedDate : LocalDate.now(clock);
    boolean firmWide = accountId == null;

    List<TrustAccount> accounts;
    if (firmWide) {
      accounts =
          trustAccountRepository.findAll().stream()
              .sorted(
                  Comparator.comparing(TrustAccount::getAccountName, String.CASE_INSENSITIVE_ORDER)
                      .thenComparing(TrustAccount::getId))
              .toList();
    } else {
      accounts =
          List.of(
              trustAccountRepository
                  .findById(accountId)
                  .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", accountId)));
    }
    var lists = accounts.stream().map(a -> accountList(a, asOf)).toList();

    // People first (merged by client over the lines in scope), by name
    var people = new LinkedHashMap<UUID, Person>();
    var others = new ArrayList<Map<String, Object>>();
    boolean interAccountOmitted = false;
    for (var list : lists) {
      for (var line : list.lines()) {
        switch (line.kind()) {
          case CLIENT, CLIENT_INVESTMENT -> {
            var person = people.computeIfAbsent(line.clientId(), Person::new);
            person.name = person.name != null ? person.name : line.name();
            if (line.kind() == CreditorLine.Kind.CLIENT) {
              person.general = person.general.add(line.balance());
              line.matters().forEach(m -> person.matters.add(matter(m)));
            } else {
              person.invested = person.invested.add(line.balance());
            }
            if (!person.sources.contains(list)) {
              person.sources.add(list);
            }
          }
          case INTER_ACCOUNT -> {
            if (firmWide) {
              interAccountOmitted = true;
            } else {
              others.add(lineRow(line, list, asOf));
            }
          }
          default -> others.add(lineRow(line, list, asOf));
        }
      }
    }
    var rows = new ArrayList<Map<String, Object>>();
    rows.addAll(personRows(people, lists, asOf, firmWide));
    others.sort(Comparator.comparing(r -> kindOrder((String) r.get("kind"))));
    rows.addAll(others);

    return new Assembled(List.copyOf(rows), summary(rows, lists, firmWide, interAccountOmitted));
  }

  private AccountList accountList(TrustAccount account, LocalDate asOf) {
    var locked =
        monthlyListRepository.findByTrustAccountIdAndPeriodEnd(account.getId(), asOf).orElse(null);
    if (locked != null) {
      var lockedBy = memberNameResolver.resolveNameOrNull(locked.getLockedBy());
      var note =
          "Locked "
              + IntegrityMessages.date(LocalDate.ofInstant(locked.getLockedAt(), clock.getZone()))
              + (lockedBy != null ? " by " + lockedBy : "");
      return new AccountList(
          account,
          locked.getLines(),
          locked,
          note,
          locked.getAdjustedBankBalance(),
          true,
          monthlyListService.adjustmentsSinceLock(locked).entries());
    }
    var live = monthlyListService.liveList(account.getId(), asOf);
    boolean bankKnown = live.bank() != null;
    return new AccountList(
        account,
        live.lines(),
        null,
        LIVE_NOTE,
        bankKnown ? live.bank().adjustedBankBalance() : live.position().cashbook(),
        bankKnown,
        List.of());
  }

  private List<Map<String, Object>> personRows(
      Map<UUID, Person> people, List<AccountList> lists, LocalDate asOf, boolean firmWide) {
    if (people.isEmpty()) {
      return List.of();
    }
    // The v3 columns: live per-client totals and last posted date as at the date, over the
    // accounts in scope (one batch query per account)
    var deposits = new HashMap<UUID, BigDecimal>();
    var payments = new HashMap<UUID, BigDecimal>();
    var feeTransfers = new HashMap<UUID, BigDecimal>();
    var lastDates = new HashMap<UUID, LocalDate>();
    for (var list : lists) {
      UUID accountId = list.account().getId();
      var clientIds =
          people.values().stream()
              .filter(p -> p.sources.contains(list))
              .map(p -> p.clientId)
              .toList();
      if (clientIds.isEmpty()) {
        continue;
      }
      Map<UUID, LedgerTotals> totals =
          LedgerTotals.byCustomer(ledgerQueries.clientTotalsByType(accountId, clientIds, asOf));
      totals.forEach(
          (id, t) -> {
            deposits.merge(id, t.deposits(), BigDecimal::add);
            payments.merge(id, t.payments(), BigDecimal::add);
            feeTransfers.merge(id, t.feeTransfers(), BigDecimal::add);
          });
      for (ClientBalanceRow row : ledgerQueries.clientBalances(accountId, asOf)) {
        if (row.getLastTransactionDate() != null && clientIds.contains(row.getCustomerId())) {
          lastDates.merge(
              row.getCustomerId(), row.getLastTransactionDate(), (a, b) -> a.isAfter(b) ? a : b);
        }
      }
    }

    return people.values().stream()
        .sorted(
            Comparator.comparing(
                    (Person p) -> p.name == null ? "" : p.name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(p -> p.clientId))
        .map(
            p -> {
              var total = p.general.add(p.invested);
              boolean allLocked = p.sources.stream().allMatch(l -> l.locked() != null);
              var row = new LinkedHashMap<String, Object>();
              row.put("clientName", p.name != null ? p.name : "Unknown client");
              row.put("customerId", p.clientId);
              row.put("balance", total);
              row.put("totalDeposits", deposits.getOrDefault(p.clientId, BigDecimal.ZERO));
              row.put("totalPayments", payments.getOrDefault(p.clientId, BigDecimal.ZERO));
              row.put("totalFeeTransfers", feeTransfers.getOrDefault(p.clientId, BigDecimal.ZERO));
              // Column kept (§16.8.6, definition edits are 650C); no interest is posted any more
              row.put("totalInterestCredited", BigDecimal.ZERO);
              var last = lastDates.get(p.clientId);
              row.put("lastTransactionDate", last != null ? last.toString() : null);
              row.put("asOfDate", asOf.toString());
              row.put("kind", CreditorLine.Kind.CLIENT.name());
              row.put("name", p.name);
              row.put("generalBalance", p.general);
              row.put("investedBalance", p.invested);
              row.put("totalBalance", total);
              row.put("matters", List.copyOf(p.matters));
              row.put(
                  "trustAccountId",
                  !firmWide || p.sources.size() == 1
                      ? p.sources.getFirst().account().getId()
                      : null);
              row.put("receivedDate", null);
              row.put("bankDescription", null);
              row.put("source", allLocked ? "LOCKED" : "LIVE");
              row.put(
                  "sourceNote", p.sources.size() == 1 ? p.sources.getFirst().sourceNote() : null);
              return (Map<String, Object>) row;
            })
        .toList();
  }

  private static Map<String, Object> lineRow(CreditorLine line, AccountList list, LocalDate asOf) {
    var row = new LinkedHashMap<String, Object>();
    row.put("clientName", line.name());
    row.put("customerId", null);
    row.put("balance", line.balance());
    row.put("totalDeposits", BigDecimal.ZERO);
    row.put("totalPayments", BigDecimal.ZERO);
    row.put("totalFeeTransfers", BigDecimal.ZERO);
    row.put("totalInterestCredited", BigDecimal.ZERO);
    row.put("lastTransactionDate", line.receivedDate());
    row.put("asOfDate", asOf.toString());
    row.put("kind", line.kind().name());
    row.put("name", line.name());
    row.put("generalBalance", BigDecimal.ZERO);
    row.put("investedBalance", BigDecimal.ZERO);
    row.put("totalBalance", line.balance());
    row.put("matters", List.of());
    row.put("trustAccountId", list.account().getId());
    row.put("receivedDate", line.receivedDate());
    row.put("bankDescription", line.bankDescription());
    row.put("source", list.source());
    row.put("sourceNote", list.sourceNote());
    return row;
  }

  private static Map<String, Object> matter(CreditorLine.MatterBalance m) {
    var map = new LinkedHashMap<String, Object>();
    map.put("matterId", m.matterId());
    map.put("name", m.name());
    map.put("balance", m.balance());
    return map;
  }

  private static int kindOrder(String kind) {
    return switch (CreditorLine.Kind.valueOf(kind)) {
      case CLIENT, CLIENT_INVESTMENT -> 0;
      case FUND -> 1;
      case UNIDENTIFIED -> 2;
      case INTER_ACCOUNT -> 3;
    };
  }

  private static Map<String, Object> summary(
      List<Map<String, Object>> rows,
      List<AccountList> lists,
      boolean firmWide,
      boolean interAccountOmitted) {
    BigDecimal totalTrustBalance = BigDecimal.ZERO;
    BigDecimal invested = BigDecimal.ZERO;
    BigDecimal fund = BigDecimal.ZERO;
    BigDecimal unidentified = BigDecimal.ZERO;
    BigDecimal creditorTotal = BigDecimal.ZERO;
    int clientCount = 0;
    for (var row : rows) {
      var balance = (BigDecimal) row.get("balance");
      creditorTotal = creditorTotal.add(balance);
      switch ((String) row.get("kind")) {
        case "CLIENT" -> {
          clientCount++;
          totalTrustBalance = totalTrustBalance.add(balance);
          invested = invested.add((BigDecimal) row.get("investedBalance"));
        }
        case "FUND" -> fund = fund.add(balance);
        case "UNIDENTIFIED" -> unidentified = unidentified.add(balance);
        default -> {}
      }
    }

    BigDecimal bankTotal = BigDecimal.ZERO;
    BigDecimal investmentAccountTotal = BigDecimal.ZERO;
    var accounts = new ArrayList<Map<String, Object>>();
    var afterLock = new ArrayList<Map<String, Object>>();
    BigDecimal afterLockNet = BigDecimal.ZERO;
    for (var list : lists) {
      var account = list.account();
      if (account.getAccountType() == TrustAccountType.INVESTMENT) {
        investmentAccountTotal = investmentAccountTotal.add(list.moneySide());
      } else {
        bankTotal = bankTotal.add(list.moneySide());
      }
      var a = new LinkedHashMap<String, Object>();
      a.put("trustAccountId", account.getId());
      a.put("accountName", account.getAccountName());
      a.put("accountType", account.getAccountType().name());
      a.put("source", list.source());
      a.put("sourceNote", list.sourceNote());
      a.put("monthlyListId", list.locked() != null ? list.locked().getId() : null);
      a.put("bankKnown", list.bankKnown());
      a.put("moneySide", list.moneySide());
      accounts.add(a);
      for (var entry : list.afterLock()) {
        var e = new LinkedHashMap<String, Object>();
        e.put("trustAccountId", account.getId());
        e.put("accountName", account.getAccountName());
        e.put("periodEnd", list.locked().getPeriodEnd().toString());
        e.put("transactionDate", entry.transactionDate().toString());
        e.put("reference", entry.reference());
        e.put("typeLabel", entry.typeLabel());
        e.put("clientName", entry.clientName());
        e.put("cashbookEffect", entry.cashbookEffect());
        e.put("ledgerEffect", entry.ledgerEffect());
        afterLock.add(e);
        afterLockNet = afterLockNet.add(entry.cashbookEffect());
      }
    }

    var summary = new LinkedHashMap<String, Object>();
    summary.put("totalTrustBalance", totalTrustBalance);
    summary.put("clientCount", clientCount);
    summary.put("creditorTotal", creditorTotal);
    summary.put("investedTotal", invested);
    summary.put("fundTotal", fund);
    summary.put("unidentifiedTotal", unidentified);
    summary.put("bankTotal", bankTotal);
    summary.put("investmentAccountTotal", investmentAccountTotal);
    summary.put("trustCashNote", TRUST_CASH_NOTE);
    summary.put("difference", creditorTotal.subtract(bankTotal).subtract(investmentAccountTotal));
    summary.put("fullyLocked", lists.stream().allMatch(l -> l.locked() != null));
    summary.put("accounts", List.copyOf(accounts));
    summary.put("afterLock", List.copyOf(afterLock));
    summary.put("afterLockNet", afterLockNet);
    if (firmWide && interAccountOmitted) {
      summary.put("interAccountNote", INTER_ACCOUNT_NOTE);
    }
    return summary;
  }
}
