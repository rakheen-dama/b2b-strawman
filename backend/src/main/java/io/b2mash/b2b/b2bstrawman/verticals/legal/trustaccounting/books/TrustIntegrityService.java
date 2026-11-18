package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books;

import static io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.IntegrityMessages.rand;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustBooksService.BankPosition;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustBooksService.TrustPosition;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.BankStatement;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.BankStatementLineRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.BankStatementRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.ReconciliationStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliation;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.UnresolvedStatementRow;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.ClientBalanceRow;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedgerQueryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.UnidentifiedItemBalanceRow;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The trust integrity check (Phase 92 649C, architecture §16.3.9, ADR-377): the trust equation made
 * visible, as a list of flags. Every EQ-1 / EQ-2 / EQ-3 term comes from {@link TrustBooksService}
 * (the six-term creditor definition lives there, once); this service adds only what it does not
 * compute — EQ-4 per item, EQ-5 per investment, unresolved statement lines, client balances and
 * bank charges.
 *
 * <p>A {@code BLOCKING} flag means the books are not provably right; a {@code WARNING} is
 * information the firm acts on. The report is {@code balanced} when no flag is blocking. The check
 * is read-only and <b>never runs on a posting path and never refuses a posting</b> (founder
 * decision 7): its only callers are {@link TrustIntegrityController}, the reconciliation's
 * calculate and completion gate, the dashboard trust position, the live Rule 54.15 list and the
 * trust-position report (650A) and the test harness.
 *
 * <p>The reads are separate statements: {@code REPEATABLE_READ} keeps them on one snapshot when the
 * check runs on its own (648E R-2). Joined to a caller's transaction (calculate, complete, the
 * dashboard) the caller's isolation applies.
 */
@Service
public class TrustIntegrityService {

  private static final String MODULE_ID = "trust_accounting";

  public static final String BLOCKING = "BLOCKING";
  public static final String WARNING = "WARNING";

  // The twelve codes of §16.3.9.
  /** EQ-1 fails at a reconciliation's period end. Blocking. */
  public static final String BANK_DIFFERENCE = "BANK_DIFFERENCE";

  /** EQ-2 fails: the cashbook differs from the trust creditors. Blocking. */
  public static final String BOOKS_OUT_OF_BALANCE = "BOOKS_OUT_OF_BALANCE";

  /** EQ-3 fails: the inter-account terms do not net to zero. Blocking, firm report only. */
  public static final String INTER_ACCOUNT_NOT_ZERO = "INTER_ACCOUNT_NOT_ZERO";

  /** EQ-4 fails: an unidentified deposit does not agree with its ledger rows. Blocking. */
  public static final String UNIDENTIFIED_ITEMS_MISMATCH = "UNIDENTIFIED_ITEMS_MISMATCH";

  /** EQ-5 fails: an investment does not agree with its ledger rows. Blocking. */
  public static final String INVESTMENT_BALANCE_MISMATCH = "INVESTMENT_BALANCE_MISMATCH";

  /** Statement lines neither matched, recorded nor excluded: blocking with a statement. */
  public static final String UNRESOLVED_STATEMENT_LINES = "UNRESOLVED_STATEMENT_LINES";

  /** The Fund interest and charges ledger is in debit (decision 9, A1). Warning. */
  public static final String FUND_LEDGER_IN_DEBIT = "FUND_LEDGER_IN_DEBIT";

  /** A client ledger is in debit (ruling #5, OL-7: flag only). Warning. */
  public static final String CLIENT_LEDGER_IN_DEBIT = "CLIENT_LEDGER_IN_DEBIT";

  /** An unidentified deposit open for more than a year (L9). Warning. */
  public static final String UNIDENTIFIED_OVER_ONE_YEAR = "UNIDENTIFIED_OVER_ONE_YEAR";

  /** Non-recoverable bank charges not yet refunded from the business account (E3). Warning. */
  public static final String CHARGES_TO_REFUND = "CHARGES_TO_REFUND";

  /**
   * Entries dated in a month whose Rule 54.15 list is locked and posted after the lock (650A,
   * §16.3.10): recorded and shown against the list, never refused. Warning.
   */
  public static final String BACKDATED_AFTER_LOCK = "BACKDATED_AFTER_LOCK";

  /** Subject kinds (D-5). */
  public static final String SUBJECT_CLIENT = "CLIENT";

  public static final String SUBJECT_CREDITOR_ACCOUNT = "CREDITOR_ACCOUNT";
  public static final String SUBJECT_UNIDENTIFIED_DEPOSIT = "UNIDENTIFIED_DEPOSIT";
  public static final String SUBJECT_INVESTMENT = "INVESTMENT";
  public static final String SUBJECT_BANK_STATEMENT = "BANK_STATEMENT";
  public static final String SUBJECT_TRUST_ACCOUNT = "TRUST_ACCOUNT";
  public static final String SUBJECT_MONTHLY_LIST = "MONTHLY_LIST";

  /**
   * One trust account as at {@code asAt}: the EQ-2 terms, the EQ-1 terms when a statement is given
   * or a reconciliation of the account has period end {@code asAt} (null otherwise), and the flags
   * — blocking first, each in the §16.3.9 table order.
   */
  public record TrustIntegrityReport(
      UUID trustAccountId,
      LocalDate asAt,
      TrustPosition position,
      BankPosition bank,
      boolean balanced,
      List<IntegrityFlag> flags,
      UUID lastCompletedReconciliationId,
      LocalDate lastReconciledThrough) {}

  /**
   * Every trust account of the firm (open or closed) as at {@code asAt}, the firm totals of each
   * term (from {@link TrustBooksService#firmPosition}) and the firm-level flags ({@code
   * INTER_ACCOUNT_NOT_ZERO}); {@code balanced} when no flag is blocking firm-level or in any
   * account.
   */
  public record FirmIntegrityReport(
      LocalDate asAt,
      boolean balanced,
      BigDecimal cashbook,
      BigDecimal clientLedgers,
      BigDecimal investmentCreditors,
      BigDecimal fundLedger,
      BigDecimal unidentifiedDeposits,
      BigDecimal otherNonClient,
      BigDecimal interAccount,
      BigDecimal creditorTotal,
      BigDecimal difference,
      List<IntegrityFlag> flags,
      List<TrustIntegrityReport> accounts) {}

  private final TrustBooksService trustBooksService;
  private final TrustLedgerQueryRepository ledgerQueries;
  private final TrustAccountRepository trustAccountRepository;
  private final TrustReconciliationRepository reconciliationRepository;
  private final BankStatementRepository bankStatementRepository;
  private final BankStatementLineRepository bankStatementLineRepository;
  private final TrustCreditorAccountRepository creditorAccountRepository;
  private final CustomerRepository customerRepository;
  private final TrustMonthlyListRepository monthlyListRepository;
  private final VerticalModuleGuard moduleGuard;
  private final Clock clock;

  public TrustIntegrityService(
      TrustBooksService trustBooksService,
      TrustLedgerQueryRepository ledgerQueries,
      TrustAccountRepository trustAccountRepository,
      TrustReconciliationRepository reconciliationRepository,
      BankStatementRepository bankStatementRepository,
      BankStatementLineRepository bankStatementLineRepository,
      TrustCreditorAccountRepository creditorAccountRepository,
      CustomerRepository customerRepository,
      TrustMonthlyListRepository monthlyListRepository,
      VerticalModuleGuard moduleGuard,
      Clock clock) {
    this.trustBooksService = trustBooksService;
    this.ledgerQueries = ledgerQueries;
    this.trustAccountRepository = trustAccountRepository;
    this.reconciliationRepository = reconciliationRepository;
    this.bankStatementRepository = bankStatementRepository;
    this.bankStatementLineRepository = bankStatementLineRepository;
    this.creditorAccountRepository = creditorAccountRepository;
    this.customerRepository = customerRepository;
    this.monthlyListRepository = monthlyListRepository;
    this.moduleGuard = moduleGuard;
    this.clock = clock;
  }

  /**
   * One account as at {@code asAt} (null = today). With {@code bankStatementId} — the completion
   * gate — EQ-1 is checked against that statement and its unresolved lines block; without it — the
   * dashboard — EQ-1 is checked only when a reconciliation of the account has period end {@code
   * asAt}, and unresolved lines in any statement not yet reconciled are a warning. 404 on an
   * unknown account.
   */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public TrustIntegrityReport check(UUID trustAccountId, LocalDate asAt, UUID bankStatementId) {
    moduleGuard.requireModule(MODULE_ID);
    LocalDate date = asAt != null ? asAt : LocalDate.now(clock);
    return checkOne(trustBooksService.position(trustAccountId, date), bankStatementId);
  }

  /**
   * Every trust account of the firm (open or closed) as at {@code asAt} (null = today), EQ-3 and
   * the firm totals. No paging: a firm holds a handful of trust accounts (open question #9; the
   * cost is linear in the number of accounts).
   */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public FirmIntegrityReport checkFirm(LocalDate asAt) {
    moduleGuard.requireModule(MODULE_ID);
    LocalDate date = asAt != null ? asAt : LocalDate.now(clock);
    var firm = trustBooksService.firmPosition(date);
    var accounts = firm.accounts().stream().map(position -> checkOne(position, null)).toList();

    var firmFlags = new ArrayList<IntegrityFlag>();
    if (firm.interAccount().signum() != 0) {
      var offending =
          firm.accounts().stream()
              .filter(p -> p.interAccount().signum() != 0)
              .collect(
                  Collectors.toMap(TrustPosition::trustAccountId, TrustPosition::interAccount));
      var names =
          trustAccountRepository.findAllById(offending.keySet()).stream()
              .collect(Collectors.toMap(TrustAccount::getId, TrustAccount::getAccountName));
      var subjects =
          firm.accounts().stream()
              .filter(p -> offending.containsKey(p.trustAccountId()))
              .map(
                  p ->
                      new FlagSubject(
                          SUBJECT_TRUST_ACCOUNT,
                          p.trustAccountId(),
                          names.get(p.trustAccountId()),
                          p.interAccount().abs()))
              .toList();
      firmFlags.add(
          new IntegrityFlag(
              INTER_ACCOUNT_NOT_ZERO,
              BLOCKING,
              "Section 86(3) placements do not net to zero across the trust accounts: "
                  + rand(firm.interAccount().abs())
                  + " is placed or held without its other side as at "
                  + IntegrityMessages.date(date)
                  + ".",
              firm.interAccount().abs(),
              subjects));
    }
    boolean balanced =
        firmFlags.stream().noneMatch(TrustIntegrityService::isBlocking)
            && accounts.stream().allMatch(TrustIntegrityReport::balanced);
    return new FirmIntegrityReport(
        date,
        balanced,
        firm.cashbook(),
        firm.clientLedgers(),
        firm.investmentCreditors(),
        firm.fundLedger(),
        firm.unidentifiedDeposits(),
        firm.otherNonClient(),
        firm.interAccount(),
        firm.creditorTotal(),
        firm.difference(),
        List.copyOf(firmFlags),
        accounts);
  }

  /** True for a {@code BLOCKING} flag. */
  public static boolean isBlocking(IntegrityFlag flag) {
    return BLOCKING.equals(flag.severity());
  }

  // --- the check of one account, reusing its position ------------------------------------------

  private TrustIntegrityReport checkOne(TrustPosition position, UUID bankStatementId) {
    UUID accountId = position.trustAccountId();
    LocalDate date = position.asAt();
    var flags = new ArrayList<IntegrityFlag>();

    // EQ-1 — with the statement given, else a reconciliation of the account at this date
    BankPosition bank = bankAt(accountId, bankStatementId, date);
    if (bank != null && bank.adjustedBankBalance().compareTo(position.cashbook()) != 0) {
      var diff = bank.adjustedBankBalance().subtract(position.cashbook()).abs();
      flags.add(
          flag(
              BANK_DIFFERENCE,
              BLOCKING,
              "Adjusted bank balance "
                  + rand(bank.adjustedBankBalance())
                  + " differs from the cashbook "
                  + rand(position.cashbook())
                  + " by "
                  + rand(diff)
                  + " as at "
                  + IntegrityMessages.date(date)
                  + ".",
              diff,
              List.of()));
    }

    // EQ-2
    if (position.difference().signum() != 0) {
      flags.add(
          flag(
              BOOKS_OUT_OF_BALANCE,
              BLOCKING,
              booksOutOfBalanceSentence(position),
              position.difference().abs(),
              List.of()));
    }

    // EQ-4 — per item, as at the date
    var items = ledgerQueries.unidentifiedItemBalances(accountId, date);
    itemsMismatch(accountId, date, items).ifPresent(flags::add);

    // EQ-5 — per investment held in the account
    investmentsMismatch(accountId, date).ifPresent(flags::add);

    // Unresolved statement lines
    unresolvedLines(accountId, bankStatementId).ifPresent(flags::add);

    // Warnings
    if (position.fundLedger().signum() < 0) {
      var debit = position.fundLedger().abs();
      var subjects =
          creditorAccountRepository
              .findByTrustAccountIdAndAccountType(
                  accountId, TrustCreditorAccountType.FUND_INTEREST_AND_CHARGES)
              .map(
                  ca ->
                      List.of(
                          new FlagSubject(
                              SUBJECT_CREDITOR_ACCOUNT, ca.getId(), ca.getName(), debit)))
              .orElse(List.of());
      flags.add(
          flag(
              FUND_LEDGER_IN_DEBIT,
              WARNING,
              "The Fund interest and charges ledger is " + rand(debit) + " in debit.",
              debit,
              subjects));
    }
    clientsInDebit(accountId, date).ifPresent(flags::add);
    unidentifiedOverOneYear(date, items).ifPresent(flags::add);
    chargesToRefund(accountId, date).ifPresent(flags::add);
    backdatedAfterLock(accountId, date).ifPresent(flags::add);

    // Blocking first; the stable sort keeps the table order within each severity
    flags.sort(Comparator.comparing(f -> isBlocking(f) ? 0 : 1));
    boolean balanced = flags.stream().noneMatch(TrustIntegrityService::isBlocking);

    var lastCompleted =
        reconciliationRepository
            .findFirstByTrustAccountIdAndStatusOrderByPeriodEndDescCompletedAtDesc(
                accountId, ReconciliationStatus.COMPLETED);
    return new TrustIntegrityReport(
        accountId,
        date,
        position,
        bank,
        balanced,
        List.copyOf(flags),
        lastCompleted.map(TrustReconciliation::getId).orElse(null),
        lastCompleted.map(TrustReconciliation::getPeriodEnd).orElse(null));
  }

  private BankPosition bankAt(UUID accountId, UUID bankStatementId, LocalDate date) {
    if (bankStatementId != null) {
      return trustBooksService.bankPosition(accountId, bankStatementId, date);
    }
    return reconciliationRepository
        .findFirstByTrustAccountIdAndPeriodEndOrderByCreatedAtDesc(accountId, date)
        .map(TrustReconciliation::getBankStatementId)
        .map(statementId -> trustBooksService.bankPosition(accountId, statementId, date))
        .orElse(null);
  }

  /** The G2 sentence (§16.3.4, §16.4.4): both totals, the difference, then the breakdown. */
  private static String booksOutOfBalanceSentence(TrustPosition p) {
    var text =
        new StringBuilder()
            .append("As at ")
            .append(IntegrityMessages.date(p.asAt()))
            .append(" the cashbook is ")
            .append(rand(p.cashbook()))
            .append(" and trust creditors total ")
            .append(rand(p.creditorTotal()))
            .append(" — a difference of ")
            .append(rand(p.difference().abs()))
            .append(". Client ledgers ")
            .append(rand(p.clientLedgers()))
            .append("; investment creditors ")
            .append(rand(p.investmentCreditors()))
            .append("; Fund interest and charges ")
            .append(rand(p.fundLedger()))
            .append("; unidentified deposits ")
            .append(rand(p.unidentifiedDeposits()));
    if (p.interAccount().signum() != 0) {
      text.append("; section 86(3) placements ").append(rand(p.interAccount()));
    }
    if (p.otherNonClient().signum() != 0) {
      text.append("; other non-client trust creditors ").append(rand(p.otherNonClient()));
    }
    return text.append(".").toString();
  }

  /**
   * EQ-4 as at the date: (a) no unidentified-ledger row without its item; (b) each item's balance
   * is 0 or its amount; (d) as at today, an item received on or before the date is {@code HELD}
   * exactly when it is open (D-6: an item received after the date is skipped for (d)).
   */
  private Optional<IntegrityFlag> itemsMismatch(
      UUID accountId, LocalDate date, List<UnidentifiedItemBalanceRow> items) {
    var sentences = new ArrayList<String>();
    var subjects = new ArrayList<FlagSubject>();
    long unnamed = ledgerQueries.unidentifiedRowsWithoutItem(accountId, date);
    if (unnamed > 0) {
      sentences.add(
          unnamed == 1
              ? "1 unidentified-deposit ledger row names no deposit."
              : unnamed + " unidentified-deposit ledger rows name no deposit.");
    }
    boolean today = date.equals(LocalDate.now(clock));
    for (var item : items) {
      boolean open = item.getBalance().compareTo(item.getAmount()) == 0;
      boolean zeroOrAmount = open || item.getBalance().signum() == 0;
      String described =
          "Unidentified deposit \"" + item.getBankDescription() + "\" of " + rand(item.getAmount());
      if (!zeroOrAmount) {
        sentences.add(
            described
                + ": its ledger rows total "
                + rand(item.getBalance())
                + " as at "
                + IntegrityMessages.date(date)
                + ".");
      } else if (today
          && !item.getReceivedDate().isAfter(date)
          && "HELD".equals(item.getStatus()) != open) {
        sentences.add(
            described
                + " is recorded as "
                + IntegrityMessages.statusWords(item.getStatus())
                + " but its ledger rows total "
                + rand(item.getBalance())
                + " as at "
                + IntegrityMessages.date(date)
                + ".");
      } else {
        continue;
      }
      subjects.add(
          new FlagSubject(
              SUBJECT_UNIDENTIFIED_DEPOSIT,
              item.getId(),
              item.getBankDescription(),
              item.getBalance().abs()));
    }
    if (sentences.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        flag(
            UNIDENTIFIED_ITEMS_MISMATCH,
            BLOCKING,
            String.join(" ", sentences),
            null,
            List.copyOf(subjects)));
  }

  /** EQ-5: each investment's rows equal placed + net interest entries − withdrawn, as at D. */
  private Optional<IntegrityFlag> investmentsMismatch(UUID accountId, LocalDate date) {
    var sentences = new ArrayList<String>();
    var subjects = new ArrayList<FlagSubject>();
    var total = BigDecimal.ZERO;
    for (var row : ledgerQueries.investmentTies(accountId, date)) {
      var expected = row.getPlaced().add(row.getInterest()).subtract(row.getWithdrawn());
      if (row.getBalance().compareTo(expected) == 0) {
        continue;
      }
      var name = row.getInstitution() + " " + row.getAccountNumber();
      sentences.add(
          "Investment "
              + name
              + ": its ledger rows total "
              + rand(row.getBalance())
              + " against "
              + rand(expected)
              + " placed, credited and withdrawn as at "
              + IntegrityMessages.date(date)
              + ".");
      subjects.add(
          new FlagSubject(SUBJECT_INVESTMENT, row.getInvestmentId(), name, row.getBalance().abs()));
      total = total.add(row.getBalance().subtract(expected).abs());
    }
    if (sentences.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        flag(
            INVESTMENT_BALANCE_MISMATCH,
            BLOCKING,
            String.join(" ", sentences),
            total,
            List.copyOf(subjects)));
  }

  /**
   * With the reconciliation's statement: its {@code UNMATCHED} lines block (G4). Without: lines
   * {@code UNMATCHED} in any imported statement not yet reconciled are a warning.
   */
  private Optional<IntegrityFlag> unresolvedLines(UUID accountId, UUID bankStatementId) {
    if (bankStatementId != null) {
      long count =
          bankStatementLineRepository.countByBankStatementIdAndMatchStatus(
              bankStatementId, "UNMATCHED");
      if (count == 0) {
        return Optional.empty();
      }
      var fileName =
          bankStatementRepository.findById(bankStatementId).map(BankStatement::getFileName);
      return Optional.of(
          flag(
              UNRESOLVED_STATEMENT_LINES,
              BLOCKING,
              unresolvedSentence(count),
              null,
              List.of(
                  new FlagSubject(
                      SUBJECT_BANK_STATEMENT, bankStatementId, fileName.orElse(null), null))));
    }
    var statements = bankStatementLineRepository.findUnresolvedInUnreconciledStatements(accountId);
    if (statements.isEmpty()) {
      return Optional.empty();
    }
    long count = statements.stream().mapToLong(UnresolvedStatementRow::getUnresolvedCount).sum();
    var subjects =
        statements.stream()
            .map(
                s ->
                    new FlagSubject(
                        SUBJECT_BANK_STATEMENT, s.getStatementId(), s.getFileName(), null))
            .toList();
    return Optional.of(
        flag(
            UNRESOLVED_STATEMENT_LINES,
            WARNING,
            (count == 1
                    ? "1 statement line is neither matched, recorded nor excluded"
                    : count + " statement lines are neither matched, recorded nor excluded")
                + (statements.size() == 1
                    ? " on a statement not yet reconciled."
                    : " on " + statements.size() + " statements not yet reconciled."),
            null,
            subjects));
  }

  /** The G4 sentence (§16.3.4, D-2). */
  public static String unresolvedSentence(long count) {
    return count == 1
        ? "1 statement line is neither matched, recorded nor excluded."
        : count + " statement lines are neither matched, recorded nor excluded.";
  }

  /** Every client whose {@code CLIENT} balance on the account is below zero as at the date. */
  private Optional<IntegrityFlag> clientsInDebit(UUID accountId, LocalDate date) {
    var inDebit =
        ledgerQueries.clientBalances(accountId, date).stream()
            .filter(row -> row.getBalance().signum() < 0)
            .toList();
    if (inDebit.isEmpty()) {
      return Optional.empty();
    }
    Map<UUID, String> names =
        customerRepository
            .findByIdIn(inDebit.stream().map(ClientBalanceRow::getCustomerId).toList())
            .stream()
            .collect(Collectors.toMap(Customer::getId, Customer::getName));
    var total =
        inDebit.stream().map(r -> r.getBalance().abs()).reduce(BigDecimal.ZERO, BigDecimal::add);
    var subjects =
        inDebit.stream()
            .map(
                r ->
                    new FlagSubject(
                        SUBJECT_CLIENT,
                        r.getCustomerId(),
                        names.get(r.getCustomerId()),
                        r.getBalance().abs()))
            .toList();
    return Optional.of(
        flag(
            CLIENT_LEDGER_IN_DEBIT,
            WARNING,
            inDebit.size() == 1
                ? "1 client ledger is " + rand(total) + " in debit."
                : inDebit.size() + " client ledgers are in debit, " + rand(total) + " in total.",
            total,
            subjects));
  }

  /** Items open as at the date (by their balance, EQ-4) and received more than a year before. */
  private static Optional<IntegrityFlag> unidentifiedOverOneYear(
      LocalDate date, List<UnidentifiedItemBalanceRow> items) {
    var old =
        items.stream()
            .filter(i -> i.getBalance().compareTo(i.getAmount()) == 0)
            .filter(i -> i.getReceivedDate().isBefore(date.minusYears(1)))
            .toList();
    if (old.isEmpty()) {
      return Optional.empty();
    }
    var total =
        old.stream()
            .map(UnidentifiedItemBalanceRow::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return Optional.of(
        flag(
            UNIDENTIFIED_OVER_ONE_YEAR,
            WARNING,
            old.size() == 1
                ? "1 unidentified deposit of "
                    + rand(total)
                    + " has been held for more than a year."
                : old.size()
                    + " unidentified deposits totalling "
                    + rand(total)
                    + " have been held for more than a year.",
            total,
            old.stream()
                .map(
                    i ->
                        new FlagSubject(
                            SUBJECT_UNIDENTIFIED_DEPOSIT,
                            i.getId(),
                            i.getBankDescription(),
                            i.getAmount()))
                .toList()));
  }

  /** Non-recoverable charges in excess of refunds, as at the date (D-8). */
  private Optional<IntegrityFlag> chargesToRefund(UUID accountId, LocalDate date) {
    var row = ledgerQueries.chargesToRefund(accountId, date);
    var excess = row.getNonRecoverableCharges().subtract(row.getRefunds());
    if (excess.signum() <= 0) {
      return Optional.empty();
    }
    return Optional.of(
        flag(
            CHARGES_TO_REFUND,
            WARNING,
            rand(excess)
                + " of non-recoverable bank charges has been debited to trust and not yet refunded"
                + " from the business account.",
            excess,
            List.of()));
  }

  /**
   * Entries back-dated into a locked month (§16.3.10, ADR-382): for each locked list of the account
   * with {@code period_end} on or before the date, the posted rows dated on or before its period
   * end and posted after it was locked. One statement per account (grouped by list); one subject
   * per list. A warning: such an entry is recorded and shown against the list, never refused.
   */
  private Optional<IntegrityFlag> backdatedAfterLock(UUID accountId, LocalDate date) {
    var rows = monthlyListRepository.backdatedAfterLock(accountId, date);
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    var sentences = new ArrayList<String>();
    var subjects = new ArrayList<FlagSubject>();
    for (var row : rows) {
      long count = row.getEntryCount();
      String month = IntegrityMessages.monthYear(row.getPeriodEnd());
      sentences.add(
          (count == 1 ? "1 entry dated on or before " : count + " entries dated on or before ")
              + IntegrityMessages.date(row.getPeriodEnd())
              + (count == 1 ? " was" : " were")
              + " entered after the "
              + month
              + " list was locked.");
      var net = row.getCashbookEffect().abs();
      subjects.add(
          new FlagSubject(
              SUBJECT_MONTHLY_LIST,
              row.getMonthlyListId(),
              month + " list",
              net.signum() == 0 ? null : net));
    }
    return Optional.of(
        flag(
            BACKDATED_AFTER_LOCK,
            WARNING,
            String.join(" ", sentences),
            null,
            List.copyOf(subjects)));
  }

  // --- helpers -----------------------------------------------------------------------------------

  private static IntegrityFlag flag(
      String code, String severity, String message, BigDecimal amount, List<FlagSubject> subjects) {
    return new IntegrityFlag(code, severity, message, amount, subjects);
  }
}
