package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustBooksService.BankPosition;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustBooksService.TrustPosition;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustMonthlyListRepository.BackdatedListRow;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.ReconciliationStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliation;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.ClientBalanceRow;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.ClientMatterBalanceRow;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedger;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedgerQueryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Rule 54.15 monthly trust creditor list (Phase 92 650A, architecture §16.3.10, ADR-382).
 *
 * <ul>
 *   <li>{@link #lock} — called by {@code completeReconciliation}, in the same transaction, when the
 *       reconciliation's period end is the last day of a calendar month: freezes the reconciled
 *       money side, the cashbook, each creditor total, every creditor by name and the flags. There
 *       is no endpoint to lock, and a list is never updated, re-locked or deleted.
 *   <li>{@link #list} / {@link #get} — the locked lists of an account; one list with the entries
 *       made after it was locked and dated on or before its period end ({@code
 *       adjustmentsSinceLock}).
 *   <li>{@link #liveList} — the same content computed now, for a date with no locked list (the Rule
 *       54.15 report).
 * </ul>
 *
 * <p>A posting back-dated into a locked month is never refused and never alters the list: it is
 * shown against it here, on the report and as the integrity flag {@code BACKDATED_AFTER_LOCK}.
 */
@Service
public class TrustMonthlyListService {

  private static final String MODULE_ID = "trust_accounting";

  /** The Fund line's name when the account has no Fund creditor account (never the case today). */
  static final String FUND_LINE_DEFAULT_NAME =
      "Legal Practitioners' Fidelity Fund — interest and charges";

  /** The inter-account line on the funding account (negative) — §16.1.2 wording. */
  static final String PLACED_IN_SECTION_86_3 = "Placed in section 86(3) investment accounts";

  /** The inter-account line on the investment account (positive) — §16.1.2 wording. */
  static final String HELD_FOR_TRUST_CREDITORS = "Held for the firm's trust creditors";

  /** One list of an account, without its lines. {@code adjustmentCount} as at today. */
  public record MonthlyListSummary(
      UUID id,
      UUID trustAccountId,
      LocalDate periodEnd,
      UUID reconciliationId,
      BigDecimal adjustedBankBalance,
      BigDecimal cashbookBalance,
      BigDecimal creditorTotal,
      BigDecimal difference,
      UUID lockedBy,
      String lockedByName,
      Instant lockedAt,
      long adjustmentCount) {}

  /** One locked list: the frozen figures, lines and flags, and what was entered after the lock. */
  public record MonthlyListResponse(
      UUID id,
      UUID trustAccountId,
      String trustAccountName,
      LocalDate periodEnd,
      UUID reconciliationId,
      BigDecimal bankBalance,
      BigDecimal outstandingDeposits,
      BigDecimal outstandingPayments,
      BigDecimal adjustedBankBalance,
      BigDecimal cashbookBalance,
      BigDecimal clientLedgerTotal,
      BigDecimal investmentCreditorTotal,
      BigDecimal fundLedgerBalance,
      BigDecimal unidentifiedDepositTotal,
      BigDecimal interAccountBalance,
      BigDecimal creditorTotal,
      BigDecimal difference,
      List<CreditorLine> lines,
      List<IntegrityFlag> integrityFlags,
      UUID lockedBy,
      String lockedByName,
      Instant lockedAt,
      AdjustmentsSinceLock adjustmentsSinceLock) {}

  /**
   * Rows dated on or before the list's period end and posted after it was locked, and the net
   * effect per term: {@code live position(period_end) − locked figure}. {@code
   * changeInCreditorTotal = liveCreditorTotal − locked creditor total}.
   */
  public record AdjustmentsSinceLock(
      List<AdjustmentEntry> entries,
      BigDecimal cashbook,
      BigDecimal clientLedgers,
      BigDecimal investmentCreditors,
      BigDecimal fundLedger,
      BigDecimal unidentifiedDeposits,
      BigDecimal interAccount,
      BigDecimal liveCreditorTotal,
      BigDecimal changeInCreditorTotal) {}

  /** One row entered after the lock; {@code typeLabel} is the registry label (never a code). */
  public record AdjustmentEntry(
      UUID transactionId,
      LocalDate transactionDate,
      Instant postedAt,
      String transactionType,
      String typeLabel,
      TrustLedger ledger,
      UUID clientId,
      String clientName,
      String reference,
      String description,
      BigDecimal cashbookEffect,
      BigDecimal ledgerEffect) {}

  /**
   * The list of an account computed now as at {@code asAt}: the position, the bank side when a
   * reconciliation of the account has that period end (null otherwise), the creditor lines and the
   * integrity flags.
   */
  public record CreditorList(
      UUID trustAccountId,
      LocalDate asAt,
      BankPosition bank,
      TrustPosition position,
      List<CreditorLine> lines,
      List<IntegrityFlag> integrityFlags) {}

  private final TrustMonthlyListRepository repository;
  private final TrustBooksService trustBooksService;
  private final TrustIntegrityService integrityService;
  private final TrustLedgerQueryRepository ledgerQueries;
  private final TrustCreditorAccountRepository creditorAccountRepository;
  private final TrustTransactionRepository transactionRepository;
  private final TrustAccountRepository trustAccountRepository;
  private final CustomerRepository customerRepository;
  private final ProjectRepository projectRepository;
  private final MemberNameResolver memberNameResolver;
  private final AuditService auditService;
  private final VerticalModuleGuard moduleGuard;
  private final Clock clock;

  public TrustMonthlyListService(
      TrustMonthlyListRepository repository,
      TrustBooksService trustBooksService,
      TrustIntegrityService integrityService,
      TrustLedgerQueryRepository ledgerQueries,
      TrustCreditorAccountRepository creditorAccountRepository,
      TrustTransactionRepository transactionRepository,
      TrustAccountRepository trustAccountRepository,
      CustomerRepository customerRepository,
      ProjectRepository projectRepository,
      MemberNameResolver memberNameResolver,
      AuditService auditService,
      VerticalModuleGuard moduleGuard,
      Clock clock) {
    this.repository = repository;
    this.trustBooksService = trustBooksService;
    this.integrityService = integrityService;
    this.ledgerQueries = ledgerQueries;
    this.creditorAccountRepository = creditorAccountRepository;
    this.transactionRepository = transactionRepository;
    this.trustAccountRepository = trustAccountRepository;
    this.customerRepository = customerRepository;
    this.projectRepository = projectRepository;
    this.memberNameResolver = memberNameResolver;
    this.auditService = auditService;
    this.moduleGuard = moduleGuard;
    this.clock = clock;
  }

  /** True when {@code date} is the last day of its calendar month. */
  public static boolean isMonthEnd(LocalDate date) {
    return date.equals(YearMonth.from(date).atEndOfMonth());
  }

  /**
   * Freezes the account's Rule 54.15 list for the completed reconciliation's period end — the date
   * the firm chose, never the statement's last line date. Joins the completion transaction: a
   * refusal here rolls the completion back. Refuses a month already locked for the account (a
   * second reconciliation to the same month end), and a list whose lines do not add up to the
   * reconciled creditor total (an entry posted while the completion ran).
   */
  @Transactional
  public TrustMonthlyList lock(TrustReconciliation completed) {
    moduleGuard.requireModule(MODULE_ID);
    LocalDate periodEnd = completed.getPeriodEnd();
    if (completed.getStatus() != ReconciliationStatus.COMPLETED || !isMonthEnd(periodEnd)) {
      throw new IllegalStateException("lock() needs a COMPLETED reconciliation to a month end");
    }
    UUID accountId = completed.getTrustAccountId();
    var existing = repository.findByTrustAccountIdAndPeriodEnd(accountId, periodEnd);
    if (existing.isPresent()) {
      throw new InvalidStateException(
          "Month already locked",
          "The list for "
              + IntegrityMessages.date(periodEnd)
              + " on this trust account was locked on "
              + IntegrityMessages.date(
                  LocalDate.ofInstant(existing.get().getLockedAt(), clock.getZone()))
              + ". A second reconciliation to the same month end cannot be completed.");
    }

    var position = trustBooksService.position(accountId, periodEnd);
    var lines = buildLines(accountId, periodEnd, position);
    var linesTotal =
        lines.stream().map(CreditorLine::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (linesTotal.compareTo(completed.getCreditorTotal()) != 0) {
      throw new InvalidStateException(
          "Trust books changed during completion",
          "An entry was posted while the reconciliation was being completed. Recalculate the"
              + " reconciliation and complete it again.");
    }

    var list =
        repository.saveAndFlush(
            new TrustMonthlyList(
                completed, lines, RequestScopes.requireMemberId(), clock.instant()));

    var details = new LinkedHashMap<String, Object>();
    details.put("trust_account_id", accountId.toString());
    details.put("period_end", periodEnd.toString());
    details.put("creditor_total", list.getCreditorTotal().toString());
    details.put("reconciliation_id", completed.getId().toString());
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_monthly_list.locked")
            .entityType("trust_monthly_list")
            .entityId(list.getId())
            .details(details)
            .build());
    return list;
  }

  /** The account's locked lists, newest first; 404 on an unknown account. */
  @Transactional(readOnly = true)
  public List<MonthlyListSummary> list(UUID trustAccountId) {
    moduleGuard.requireModule(MODULE_ID);
    if (!trustAccountRepository.existsById(trustAccountId)) {
      throw new ResourceNotFoundException("TrustAccount", trustAccountId);
    }
    var lists = repository.findByTrustAccountIdOrderByPeriodEndDesc(trustAccountId);
    if (lists.isEmpty()) {
      return List.of();
    }
    Map<UUID, Long> adjustments =
        repository.backdatedAfterLock(trustAccountId, LocalDate.now(clock)).stream()
            .collect(
                Collectors.toMap(
                    BackdatedListRow::getMonthlyListId, BackdatedListRow::getEntryCount));
    var names =
        memberNameResolver.resolveNames(
            lists.stream().map(TrustMonthlyList::getLockedBy).collect(Collectors.toSet()));
    return lists.stream()
        .map(
            l ->
                new MonthlyListSummary(
                    l.getId(),
                    l.getTrustAccountId(),
                    l.getPeriodEnd(),
                    l.getReconciliationId(),
                    l.getAdjustedBankBalance(),
                    l.getCashbookBalance(),
                    l.getCreditorTotal(),
                    l.getDifference(),
                    l.getLockedBy(),
                    names.get(l.getLockedBy()),
                    l.getLockedAt(),
                    adjustments.getOrDefault(l.getId(), 0L)))
        .toList();
  }

  /** One locked list with the entries made after the lock; 404 on an unknown list. */
  @Transactional(readOnly = true)
  public MonthlyListResponse get(UUID monthlyListId) {
    moduleGuard.requireModule(MODULE_ID);
    var list =
        repository
            .findById(monthlyListId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustMonthlyList", monthlyListId));
    var accountName =
        trustAccountRepository
            .findById(list.getTrustAccountId())
            .map(TrustAccount::getAccountName)
            .orElse(null);
    return new MonthlyListResponse(
        list.getId(),
        list.getTrustAccountId(),
        accountName,
        list.getPeriodEnd(),
        list.getReconciliationId(),
        list.getBankBalance(),
        list.getOutstandingDeposits(),
        list.getOutstandingPayments(),
        list.getAdjustedBankBalance(),
        list.getCashbookBalance(),
        list.getClientLedgerTotal(),
        list.getInvestmentCreditorTotal(),
        list.getFundLedgerBalance(),
        list.getUnidentifiedDepositTotal(),
        list.getInterAccountBalance(),
        list.getCreditorTotal(),
        list.getDifference(),
        list.getLines(),
        list.getIntegrityFlags(),
        list.getLockedBy(),
        memberNameResolver.resolveNameOrNull(list.getLockedBy()),
        list.getLockedAt(),
        adjustmentsSinceLock(list));
  }

  /**
   * The rows entered after {@code list} was locked and dated on or before its period end, with the
   * net per term (live − locked). Used by {@link #get} and the Rule 54.15 report's after-lock
   * schedule.
   */
  @Transactional(readOnly = true)
  public AdjustmentsSinceLock adjustmentsSinceLock(TrustMonthlyList list) {
    moduleGuard.requireModule(MODULE_ID);
    var rows =
        transactionRepository.findPostedAfterLock(
            list.getTrustAccountId(), list.getPeriodEnd(), list.getLockedAt());
    var clientIds =
        rows.stream()
            .map(TrustTransaction::getCustomerId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    var clientNames = customerNames(clientIds);
    var entries =
        rows.stream()
            .map(
                t ->
                    new AdjustmentEntry(
                        t.getId(),
                        t.getTransactionDate(),
                        t.getPostedAt(),
                        t.getTransactionType(),
                        TrustTransactionType.find(t.getTransactionType())
                            .map(TrustTransactionType::label)
                            .orElse("Trust entry"),
                        t.getLedger(),
                        t.getCustomerId(),
                        t.getCustomerId() != null ? clientNames.get(t.getCustomerId()) : null,
                        t.getReference(),
                        t.getDescription(),
                        t.getCashbookEffect(),
                        t.getLedgerEffect()))
            .toList();
    var live = trustBooksService.position(list.getTrustAccountId(), list.getPeriodEnd());
    return new AdjustmentsSinceLock(
        entries,
        live.cashbook().subtract(list.getCashbookBalance()),
        live.clientLedgers().subtract(list.getClientLedgerTotal()),
        live.investmentCreditors().subtract(list.getInvestmentCreditorTotal()),
        live.fundLedger().subtract(list.getFundLedgerBalance()),
        live.unidentifiedDeposits().subtract(list.getUnidentifiedDepositTotal()),
        live.interAccount().subtract(list.getInterAccountBalance()),
        live.creditorTotal(),
        live.creditorTotal().subtract(list.getCreditorTotal()));
  }

  /**
   * The account's list computed now as at {@code asAt} (null = today): the same lines a lock would
   * freeze, with the integrity check's position, bank side and flags. 404 on an unknown account.
   */
  @Transactional(readOnly = true)
  public CreditorList liveList(UUID trustAccountId, LocalDate asAt) {
    moduleGuard.requireModule(MODULE_ID);
    var report = integrityService.check(trustAccountId, asAt, null);
    return new CreditorList(
        trustAccountId,
        report.asAt(),
        report.bank(),
        report.position(),
        buildLines(trustAccountId, report.asAt(), report.position()),
        report.flags());
  }

  // --- the lines -------------------------------------------------------------------------------

  /**
   * Every creditor of the account as at {@code date}, from the posted rows (one source of truth):
   * clients with a non-zero general balance (with the per-matter breakdown), clients with a
   * non-zero invested balance on this account, the Fund line (always), each open unidentified
   * deposit and the inter-account line where non-zero. They sum to {@code position.creditorTotal()}
   * while EQ-4 holds and nothing is on another non-client ledger ({@code otherNonClient} is always
   * zero in this phase).
   */
  private List<CreditorLine> buildLines(UUID accountId, LocalDate date, TrustPosition position) {
    var general =
        ledgerQueries.clientBalances(accountId, date).stream()
            .filter(r -> r.getBalance().signum() != 0)
            .toList();
    var invested =
        ledgerQueries.investmentCreditorBalances(accountId, date).stream()
            .filter(r -> r.getBalance().signum() != 0)
            .toList();
    Map<UUID, List<ClientMatterBalanceRow>> mattersByClient =
        general.isEmpty()
            ? Map.of()
            : ledgerQueries.clientMatterBalancesOnAccount(accountId, date).stream()
                .filter(r -> r.getBalance().signum() != 0)
                .collect(Collectors.groupingBy(ClientMatterBalanceRow::getCustomerId));

    var clientIds = new HashSet<UUID>();
    general.forEach(r -> clientIds.add(r.getCustomerId()));
    invested.forEach(r -> clientIds.add(r.getCustomerId()));
    var clientNames = customerNames(clientIds);
    var matterIds =
        mattersByClient.values().stream()
            .flatMap(List::stream)
            .map(ClientMatterBalanceRow::getProjectId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Map<UUID, String> matterNames =
        matterIds.isEmpty()
            ? Map.of()
            : projectRepository.findByIdIn(matterIds).stream()
                .collect(Collectors.toMap(Project::getId, Project::getName));

    Comparator<CreditorLine> byName =
        Comparator.comparing(
                (CreditorLine l) -> l.name() == null ? "" : l.name(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(l -> l.clientId().toString());
    var lines = new ArrayList<CreditorLine>();

    lines.addAll(
        general.stream()
            .map(
                r -> {
                  var matters =
                      mattersByClient.getOrDefault(r.getCustomerId(), List.of()).stream()
                          .map(
                              m ->
                                  new CreditorLine.MatterBalance(
                                      m.getProjectId(),
                                      m.getProjectId() != null
                                          ? matterNames.get(m.getProjectId())
                                          : null,
                                      m.getBalance()))
                          .sorted(
                              Comparator.comparing(
                                  (CreditorLine.MatterBalance m) ->
                                      m.name() == null ? "" : m.name(),
                                  String.CASE_INSENSITIVE_ORDER))
                          .toList();
                  return new CreditorLine(
                      CreditorLine.Kind.CLIENT,
                      r.getCustomerId(),
                      clientNames.get(r.getCustomerId()),
                      matters,
                      r.getBalance(),
                      null,
                      null,
                      null);
                })
            .sorted(byName)
            .toList());

    lines.addAll(
        invested.stream()
            .map(
                (ClientBalanceRow r) ->
                    new CreditorLine(
                        CreditorLine.Kind.CLIENT_INVESTMENT,
                        r.getCustomerId(),
                        clientNames.get(r.getCustomerId()),
                        List.of(),
                        r.getBalance(),
                        null,
                        null,
                        null))
            .sorted(byName)
            .toList());

    var fundName =
        creditorAccountRepository
            .findByTrustAccountIdAndAccountType(
                accountId, TrustCreditorAccountType.FUND_INTEREST_AND_CHARGES)
            .map(TrustCreditorAccount::getName)
            .orElse(FUND_LINE_DEFAULT_NAME);
    lines.add(
        new CreditorLine(
            CreditorLine.Kind.FUND,
            null,
            fundName,
            List.of(),
            position.fundLedger(),
            null,
            null,
            null));

    // Open as at the date (EQ-4): the item's own rows sum to its amount; oldest first
    ledgerQueries.unidentifiedItemBalances(accountId, date).stream()
        .filter(i -> i.getAmount().signum() != 0 && i.getBalance().compareTo(i.getAmount()) == 0)
        .map(
            i ->
                new CreditorLine(
                    CreditorLine.Kind.UNIDENTIFIED,
                    null,
                    i.getBankDescription(),
                    List.of(),
                    i.getAmount(),
                    i.getId(),
                    i.getReceivedDate().toString(),
                    i.getBankDescription()))
        .forEach(lines::add);

    if (position.interAccount().signum() != 0) {
      lines.add(
          new CreditorLine(
              CreditorLine.Kind.INTER_ACCOUNT,
              null,
              position.interAccount().signum() < 0
                  ? PLACED_IN_SECTION_86_3
                  : HELD_FOR_TRUST_CREDITORS,
              List.of(),
              position.interAccount(),
              null,
              null,
              null));
    }
    return List.copyOf(lines);
  }

  private Map<UUID, String> customerNames(Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    return customerRepository.findByIdIn(ids).stream()
        .collect(Collectors.toMap(Customer::getId, Customer::getName, (a, b) -> a));
  }
}
