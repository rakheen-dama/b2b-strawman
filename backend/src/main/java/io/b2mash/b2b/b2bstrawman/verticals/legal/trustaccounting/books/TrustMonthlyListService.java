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
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.LedgerTotals;
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
import org.springframework.transaction.annotation.Isolation;
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
 *
 * <p>The reads ({@link #list}, {@link #get}, {@link #adjustmentsSinceLock}, {@link #liveList}) run
 * at {@code REPEATABLE_READ} when they start the transaction, so their several statements see one
 * snapshot (648E convention). Joined to a caller's transaction they take its isolation (R-1).
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
   * second reconciliation to the same month end), and a list whose cashbook or any creditor term
   * differs from the reconciled one (an entry posted while the completion ran).
   *
   * <p>First it takes the account row lock ({@code FOR UPDATE}, 650A F-1) that every posting's
   * {@code FOR SHARE} conflicts with: the books are read only after every posting in flight has
   * committed, and a posting that starts now waits for the completion to commit, so its {@code
   * posted_at} is after {@code locked_at} — every row is either in the list or shown against it.
   * Lock order: the statement (completion), then the account.
   */
  @Transactional
  public TrustMonthlyList lock(TrustReconciliation completed) {
    moduleGuard.requireModule(MODULE_ID);
    LocalDate periodEnd = completed.getPeriodEnd();
    if (completed.getStatus() != ReconciliationStatus.COMPLETED || !isMonthEnd(periodEnd)) {
      throw new IllegalStateException("lock() needs a COMPLETED reconciliation to a month end");
    }
    UUID accountId = completed.getTrustAccountId();
    trustAccountRepository
        .findByIdForUpdate(accountId)
        .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", accountId));
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
    if (!matchesReconciled(completed, position, lines)) {
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

  /**
   * True when the list about to be frozen is the reconciled one: the cashbook and creditor total
   * read now equal the reconciliation's, and the lines of each kind sum to the matching reconciled
   * term (clients' general balances, invested balances, the Fund ledger, open unidentified
   * deposits, section 86(3) placements) and all of them to the creditor total. A posting that moved
   * value between two terms — leaving the total unchanged — is caught too.
   */
  private static boolean matchesReconciled(
      TrustReconciliation reconciled, TrustPosition position, List<CreditorLine> lines) {
    return same(position.cashbook(), reconciled.getCashbookBalance())
        && same(position.creditorTotal(), reconciled.getCreditorTotal())
        && same(sum(lines, CreditorLine.Kind.CLIENT), reconciled.getClientLedgerTotal())
        && same(
            sum(lines, CreditorLine.Kind.CLIENT_INVESTMENT),
            reconciled.getInvestmentCreditorTotal())
        && same(sum(lines, CreditorLine.Kind.FUND), reconciled.getFundLedgerBalance())
        && same(
            sum(lines, CreditorLine.Kind.UNIDENTIFIED), reconciled.getUnidentifiedDepositTotal())
        && same(sum(lines, CreditorLine.Kind.INTER_ACCOUNT), reconciled.getInterAccountBalance())
        && same(sum(lines, null), reconciled.getCreditorTotal());
  }

  /** Σ balance of the lines of {@code kind} (null = every line). */
  private static BigDecimal sum(List<CreditorLine> lines, CreditorLine.Kind kind) {
    return lines.stream()
        .filter(l -> kind == null || l.kind() == kind)
        .map(CreditorLine::balance)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static boolean same(BigDecimal a, BigDecimal b) {
    return a.compareTo(b) == 0;
  }

  /** The list {@code reconciliationId} locked when it completed, or null (650A, §16.4.4). */
  @Transactional(readOnly = true)
  public UUID monthlyListIdFor(UUID reconciliationId) {
    moduleGuard.requireModule(MODULE_ID);
    return repository.findIdByReconciliationId(reconciliationId).orElse(null);
  }

  /**
   * The list each of {@code reconciliationIds} locked, keyed by reconciliation id (absent = none) —
   * one statement for a page of reconciliations.
   */
  @Transactional(readOnly = true)
  public Map<UUID, UUID> monthlyListIdsFor(Collection<UUID> reconciliationIds) {
    moduleGuard.requireModule(MODULE_ID);
    if (reconciliationIds.isEmpty()) {
      return Map.of();
    }
    return repository.findIdsByReconciliationIdIn(reconciliationIds).stream()
        .collect(
            Collectors.toMap(
                TrustMonthlyListRepository.ReconciliationListId::getReconciliationId,
                TrustMonthlyListRepository.ReconciliationListId::getId));
  }

  /** The account's locked lists, newest first; 404 on an unknown account. */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
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
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
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
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
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
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
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
   * zero in this phase). Each client's posted totals and last posted date ride on its first line.
   */
  private List<CreditorLine> buildLines(UUID accountId, LocalDate date, TrustPosition position) {
    var clientRows = ledgerQueries.clientBalances(accountId, date);
    var general = clientRows.stream().filter(r -> r.getBalance().signum() != 0).toList();
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
    Map<UUID, LedgerTotals> totals =
        clientIds.isEmpty()
            ? Map.of()
            : LedgerTotals.byCustomer(ledgerQueries.clientTotalsByType(accountId, clientIds, date));
    Map<UUID, LocalDate> lastDates = new LinkedHashMap<>();
    clientRows.stream()
        .filter(r -> r.getLastTransactionDate() != null)
        .forEach(r -> lastDates.put(r.getCustomerId(), r.getLastTransactionDate()));
    var generalIds =
        general.stream().map(ClientBalanceRow::getCustomerId).collect(Collectors.toSet());
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
                  return clientLine(
                      CreditorLine.Kind.CLIENT,
                      r,
                      clientNames,
                      matters,
                      totals.getOrDefault(r.getCustomerId(), LedgerTotals.ZERO),
                      lastDates.get(r.getCustomerId()));
                })
            .sorted(byName)
            .toList());

    lines.addAll(
        invested.stream()
            .map(
                (ClientBalanceRow r) ->
                    generalIds.contains(r.getCustomerId())
                        ? clientLine(
                            CreditorLine.Kind.CLIENT_INVESTMENT,
                            r,
                            clientNames,
                            List.of(),
                            null,
                            null)
                        : clientLine(
                            CreditorLine.Kind.CLIENT_INVESTMENT,
                            r,
                            clientNames,
                            List.of(),
                            totals.getOrDefault(r.getCustomerId(), LedgerTotals.ZERO),
                            lastDates.get(r.getCustomerId())))
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
            null,
            null,
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
                    i.getBankDescription(),
                    null,
                    null,
                    null,
                    null))
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
              null,
              null,
              null,
              null,
              null));
    }
    return List.copyOf(lines);
  }

  /**
   * A client's line; {@code totals} and {@code lastDate} only on the client's first line (null
   * totals = not the first line).
   */
  private static CreditorLine clientLine(
      CreditorLine.Kind kind,
      ClientBalanceRow row,
      Map<UUID, String> clientNames,
      List<CreditorLine.MatterBalance> matters,
      LedgerTotals totals,
      LocalDate lastDate) {
    return new CreditorLine(
        kind,
        row.getCustomerId(),
        clientNames.get(row.getCustomerId()),
        matters,
        row.getBalance(),
        null,
        null,
        null,
        totals != null ? totals.deposits() : null,
        totals != null ? totals.payments() : null,
        totals != null ? totals.feeTransfers() : null,
        totals != null && lastDate != null ? lastDate.toString() : null);
  }

  private Map<UUID, String> customerNames(Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    return customerRepository.findByIdIn(ids).stream()
        .collect(Collectors.toMap(Customer::getId, Customer::getName, (a, b) -> a));
  }
}
