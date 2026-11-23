package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.EntityNoun;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustBooksService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentInterestRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.CreditorBreakdown;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.ClientBalanceRow;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.ClientHistoryRow;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedger;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedgerQueryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionDirection;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.TrustTransactionResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionTypeLabels;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientLedgerService {

  private static final String MODULE_ID = "trust_accounting";

  private final ClientLedgerCardRepository ledgerCardRepository;
  private final TrustTransactionRepository transactionRepository;
  private final TrustLedgerQueryRepository ledgerQueries;
  private final CustomerRepository customerRepository;
  private final VerticalModuleGuard moduleGuard;
  private final TrustBooksService trustBooksService;
  private final TrustInvestmentInterestRepository interestRepository;
  private final Clock clock;

  public ClientLedgerService(
      ClientLedgerCardRepository ledgerCardRepository,
      TrustTransactionRepository transactionRepository,
      TrustLedgerQueryRepository ledgerQueries,
      CustomerRepository customerRepository,
      VerticalModuleGuard moduleGuard,
      TrustBooksService trustBooksService,
      TrustInvestmentInterestRepository interestRepository,
      Clock clock) {
    this.ledgerCardRepository = ledgerCardRepository;
    this.transactionRepository = transactionRepository;
    this.ledgerQueries = ledgerQueries;
    this.customerRepository = customerRepository;
    this.moduleGuard = moduleGuard;
    this.trustBooksService = trustBooksService;
    this.interestRepository = interestRepository;
    this.clock = clock;
  }

  // --- DTO Records ---

  /**
   * One client's ledger on one account. {@code balance}, the totals and {@code lastTransactionDate}
   * are derived from the posted rows (ADR-378); {@code id}, {@code createdAt} and {@code updatedAt}
   * are the client's lock anchor's, null when it has none. {@code balance} is the client's general
   * balance; {@code investedBalance} (648E, ADR-379 L7) is what is held for the client in section
   * 86(4) investments placed from this account — the client's total held in trust is {@code balance
   * + investedBalance}, unchanged by a placement or its return.
   */
  public record ClientLedgerCardResponse(
      UUID id,
      UUID trustAccountId,
      UUID clientId,
      String clientName,
      BigDecimal balance,
      BigDecimal totalDeposits,
      BigDecimal totalPayments,
      BigDecimal totalFeeTransfers,
      // G89-F13 / 648E / 657A: every total is derived from the posted ledger (LedgerTotals), so
      // broughtForward + deposits + transfers in + investment returns - payments - fee transfers
      // - refunds - transfers out - investment placements == balance (general);
      // total held in trust = balance + investedBalance
      BigDecimal totalRefunds,
      BigDecimal totalTransfersIn,
      BigDecimal totalTransfersOut,
      BigDecimal totalInvestmentPlacements,
      BigDecimal totalInvestmentReturns,
      // Phase 93 (657A): "Brought forward at take-on" — the signed net of the client's take-on rows
      // (LedgerTotals.broughtForward); 0 for a client never taken on. Still sums to balance.
      BigDecimal broughtForward,
      BigDecimal investedBalance,
      LocalDate lastTransactionDate,
      Instant createdAt,
      Instant updatedAt) {}

  public record LedgerStatementResponse(
      BigDecimal openingBalance,
      BigDecimal closingBalance,
      List<LedgerStatementLine> transactions) {}

  /**
   * The account's client ledgers ({@code balance}, meaning unchanged) with every trust creditor
   * beside them in {@code creditorBreakdown} (§16.4.1, 646D).
   */
  public record TotalBalanceResponse(BigDecimal balance, CreditorBreakdown creditorBreakdown) {}

  /** Optional /history filters (ADR-361); every null field disables its predicate. */
  public record ClientHistoryFilter(
      String type, String status, LocalDate dateFrom, LocalDate dateTo) {
    public static final ClientHistoryFilter NONE = new ClientHistoryFilter(null, null, null, null);
  }

  public record LedgerStatementLine(
      UUID transactionId,
      String transactionType,
      BigDecimal amount,
      String reference,
      String description,
      LocalDate transactionDate,
      String status,
      BigDecimal runningBalance,
      // Phase 92 (ADR-378): the row's signed effect on the client ledger — the statement and the
      // Statement of Account classify lines by its sign, never by type name
      BigDecimal ledgerEffect) {}

  /**
   * 648F (§16.6.4, ADR-379 L7): a client's section 86(4) investments placed from one trust account,
   * over a period — what the Statement of Account and the Client Trust Statement print in their
   * "Section 86(4) investment" block and the client-ledger-statement report carries. Balances are
   * the posted {@code CLIENT_INVESTMENT} ledger as at {@code startDate − 1} / {@code endDate}; the
   * period's movements are the same rows by type (a reversal netted under the type it reverses);
   * gross and the Fund's 5% come from the POSTED interest entries (A2: under NET only the entry
   * holds them). Interest is only ever {@code INV_INTEREST} (E4). {@code openingBalance +
   * capitalPlaced + interestNet − returnedToTrust = closingBalance}.
   */
  public record InvestedStatement(
      BigDecimal openingBalance,
      BigDecimal capitalPlaced,
      BigDecimal interestGross,
      BigDecimal interestFundShare,
      BigDecimal interestNet,
      // capital plus the net interest returned to the trust banking account (INV_WITHDRAW)
      BigDecimal returnedToTrust,
      BigDecimal closingBalance,
      List<InvestedInterestLine> interestLines) {

    public static final InvestedStatement NONE =
        new InvestedStatement(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            List.of());

    /** Nothing to print: nothing invested at either end and no movement in the period. */
    public boolean isEmpty() {
      return openingBalance.signum() == 0
          && closingBalance.signum() == 0
          && capitalPlaced.signum() == 0
          && interestNet.signum() == 0
          && returnedToTrust.signum() == 0
          && interestLines.isEmpty();
    }
  }

  /** One POSTED section 86(4) interest entry: the date credited, gross, the Fund's 5%, net. */
  public record InvestedInterestLine(
      LocalDate entryDate,
      BigDecimal grossAmount,
      BigDecimal fundShareAmount,
      BigDecimal netAmount) {}

  // --- Service Methods ---

  // REPEATABLE READ: the card's balance, totals and invested balance are separate statements; one
  // snapshot keeps them from straddling a commit (an s86(4) placement posts INV_PLACE and
  // INV_RECEIVE together). Read-only, so Postgres cannot raise a serialization failure here.
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ClientLedgerCardResponse getClientLedger(UUID customerId, UUID trustAccountId) {
    moduleGuard.requireModule(MODULE_ID);

    // An unknown customer ID is a genuine 404; a known client with no posted row is a valid
    // zero-balance state (GAP-S4-06 + PR #1004 review finding) — the "Trust" tab on every new
    // matter renders a clean R0.00 state instead of a 404.
    Customer customer =
        customerRepository
            .findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.CLIENT, customerId));
    var summary = ledgerQueries.clientSummary(trustAccountId, customerId);
    var anchor = ledgerCardRepository.findByTrustAccountIdAndCustomerId(trustAccountId, customerId);
    var invested =
        investedFor(trustAccountId, List.of(customerId)).getOrDefault(customerId, BigDecimal.ZERO);
    // 657A: a client taken on with only a section 86(4) investment has no CLIENT row and no anchor
    // (TAKEON_INVESTMENT posts to CLIENT_INVESTMENT), yet holds money in trust — not a zero card
    if (summary.isEmpty() && anchor.isEmpty() && invested.signum() == 0) {
      return emptyLedgerCardResponse(customer, trustAccountId);
    }
    return toResponse(
        trustAccountId,
        customerId,
        customer.getName(),
        summary,
        anchor,
        totalsFor(trustAccountId, List.of(customerId)).getOrDefault(customerId, LedgerTotals.ZERO),
        invested);
  }

  /**
   * Synthetic zero-balance response for a client with no posted trust row and no lock anchor on the
   * account. Caller must ensure the customer exists — see {@link #getClientLedger}.
   */
  private ClientLedgerCardResponse emptyLedgerCardResponse(Customer customer, UUID trustAccountId) {
    return new ClientLedgerCardResponse(
        null, // no lock anchor yet
        trustAccountId,
        customer.getId(),
        customer.getName(),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO, // totalInvestmentPlacements
        BigDecimal.ZERO, // totalInvestmentReturns
        BigDecimal.ZERO, // broughtForward
        BigDecimal.ZERO, // investedBalance
        null, // lastTransactionDate
        null, // createdAt
        null); // updatedAt
  }

  /**
   * The client ledgers of an account: every client with a posted row (ADR-378 — a client with rows
   * and no anchor is listed), balance descending, paged in SQL. The caller's sort is ignored (the
   * UI sends {@code sort=balance,desc}, which the derived query applies itself — there is no stored
   * {@code balance} property to sort on).
   */
  // One snapshot for the page, its totals and invested balances — see getClientLedger
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<ClientLedgerCardResponse> listClientLedgers(UUID trustAccountId, Pageable pageable) {
    moduleGuard.requireModule(MODULE_ID);

    Pageable unsorted =
        pageable.isPaged()
            ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
            : Pageable.unpaged();
    Page<ClientBalanceRow> page = ledgerQueries.clientBalancePage(trustAccountId, unsorted);

    // Batch-load customer names, lock anchors and derived totals to avoid N+1
    Set<UUID> customerIds =
        page.getContent().stream().map(ClientBalanceRow::getCustomerId).collect(Collectors.toSet());
    Map<UUID, String> customerNames =
        customerRepository.findByIdIn(customerIds).stream()
            .collect(Collectors.toMap(c -> c.getId(), c -> c.getName()));
    Map<UUID, ClientLedgerCard> anchors =
        ledgerCardRepository
            .findByTrustAccountIdAndCustomerIdIn(trustAccountId, customerIds)
            .stream()
            .collect(Collectors.toMap(ClientLedgerCard::getCustomerId, Function.identity()));
    // One batch query for the page's derived totals (G89-F13)
    Map<UUID, LedgerTotals> totals = totalsFor(trustAccountId, customerIds);
    // ... and one for what the page's clients hold in section 86(4) investments (648E)
    Map<UUID, BigDecimal> invested = investedFor(trustAccountId, customerIds);

    return page.map(
        row ->
            toResponse(
                trustAccountId,
                row.getCustomerId(),
                customerNames.getOrDefault(row.getCustomerId(), "(deleted customer)"),
                Optional.of(row),
                Optional.ofNullable(anchors.get(row.getCustomerId())),
                totals.getOrDefault(row.getCustomerId(), LedgerTotals.ZERO),
                invested.getOrDefault(row.getCustomerId(), BigDecimal.ZERO)));
  }

  /**
   * A client's trust history on one account, newest first, each row carrying its server-computed
   * running balance (ADR-361, arch §14.3.3). The filters narrow the page without changing any
   * balance.
   */
  @Transactional(readOnly = true)
  public Page<TrustTransactionResponse> getClientTransactionHistory(
      UUID customerId, UUID trustAccountId, ClientHistoryFilter filter, Pageable pageable) {
    moduleGuard.requireModule(MODULE_ID);
    var f = filter != null ? filter : ClientHistoryFilter.NONE;
    // Sort-free: the posting order is baked into the native query (R6.7). The UI still sends
    // sort=transactionDate,desc, which must not reach a native query.
    Pageable unsorted =
        pageable.isPaged()
            ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
            : Pageable.unpaged();

    return transactionRepository
        .findClientHistoryWithRunningBalance(
            customerId,
            trustAccountId,
            blankToNull(f.type()),
            blankToNull(f.status()),
            f.dateFrom(),
            f.dateTo(),
            unsorted)
        .map(ClientLedgerService::toTransactionResponse);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  // --- 440.11: Historical Balance, Total Balance, and Ledger Statement ---

  @Transactional(readOnly = true)
  public BigDecimal getClientBalanceAsOfDate(
      UUID customerId, UUID trustAccountId, LocalDate asOfDate) {
    moduleGuard.requireModule(MODULE_ID);
    return ledgerQueries.clientBalance(trustAccountId, customerId, asOfDate);
  }

  /**
   * What is held for the client in section 86(4) investments placed from {@code fundingAccountId},
   * as at {@code asAt} ({@code TrustLedgerQueryRepository.clientInvestedBalance}; never null — the
   * query COALESCEs to zero). With {@link #getClientBalanceAsOfDate} it makes the client's money
   * held on the account: a placement moves it from the general to the invested balance (652D D-2).
   */
  @Transactional(readOnly = true)
  public BigDecimal getClientInvestedBalanceAsOfDate(
      UUID customerId, UUID fundingAccountId, LocalDate asAt) {
    moduleGuard.requireModule(MODULE_ID);
    return ledgerQueries.clientInvestedBalance(fundingAccountId, customerId, asAt);
  }

  /**
   * Client ledgers and every trust creditor as at {@code asAt} (null = today, {@code
   * LocalDate.now(clock)}) — 646D. 404 for an unknown account ({@link TrustBooksService#position}).
   */
  @Transactional(readOnly = true)
  public TotalBalanceResponse getTotalTrustBalance(UUID trustAccountId, LocalDate asAt) {
    moduleGuard.requireModule(MODULE_ID);
    var position =
        trustBooksService.position(trustAccountId, asAt != null ? asAt : LocalDate.now(clock));
    return new TotalBalanceResponse(position.clientLedgers(), CreditorBreakdown.of(position));
  }

  /** As at today. */
  @Transactional(readOnly = true)
  public TotalBalanceResponse getTotalTrustBalance(UUID trustAccountId) {
    return getTotalTrustBalance(trustAccountId, null);
  }

  @Transactional(readOnly = true)
  public LedgerStatementResponse getClientLedgerStatement(
      UUID customerId, UUID trustAccountId, LocalDate startDate, LocalDate endDate) {
    moduleGuard.requireModule(MODULE_ID);

    if (endDate.isBefore(startDate)) {
      throw new InvalidStateException(
          "Invalid ledger statement range", "endDate must be on or after startDate");
    }

    // Get the opening balance as of the day before the start date
    BigDecimal openingBalance =
        ledgerQueries.clientBalance(trustAccountId, customerId, startDate.minusDays(1));

    // Fetch transactions in the date range
    List<TrustTransaction> transactions =
        transactionRepository.findForStatement(customerId, trustAccountId, startDate, endDate);

    // Compute running balance
    List<LedgerStatementLine> lines = new ArrayList<>();
    BigDecimal runningBalance = openingBalance;

    for (TrustTransaction txn : transactions) {
      // The row's own posted effect (ADR-378) — no type-name sign table
      BigDecimal effect = txn.getLedgerEffect();
      runningBalance = runningBalance.add(effect);

      lines.add(
          new LedgerStatementLine(
              txn.getId(),
              txn.getTransactionType(),
              txn.getAmount(),
              txn.getReference(),
              txn.getDescription(),
              txn.getTransactionDate(),
              txn.getStatus(),
              runningBalance,
              effect));
    }

    BigDecimal closingBalance = lines.isEmpty() ? openingBalance : lines.getLast().runningBalance();

    return new LedgerStatementResponse(openingBalance, closingBalance, lines);
  }

  /**
   * The client's section 86(4) investments placed from {@code fundingAccountId} over {@code
   * startDate}..{@code endDate} ({@link InvestedStatement}); {@link InvestedStatement#isEmpty()}
   * when there is nothing to print.
   *
   * <p>REPEATABLE READ (as {@link #getClientLedger}): the two balances, the per-type totals and the
   * interest entries are four statements; one snapshot keeps an interest posting from landing in
   * the totals but not the closing balance. Read-only, so Postgres cannot raise a serialization
   * failure here. The isolation applies only where this call starts the transaction — inside a
   * caller's transaction it reads on the caller's snapshot
   * (qa_cycle/evidence/phase92/648F/found.md, R-1) — so the client documents call it inside {@code
   * TrustSnapshotReads} with their other trust reads (FU-3), and the reports inside the read-only
   * REPEATABLE_READ transaction {@code ReportExportService} opens (650C D-38).
   */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public InvestedStatement getClientInvestedStatement(
      UUID customerId, UUID fundingAccountId, LocalDate startDate, LocalDate endDate) {
    moduleGuard.requireModule(MODULE_ID);
    if (endDate.isBefore(startDate)) {
      throw new InvalidStateException(
          "Invalid ledger statement range", "endDate must be on or after startDate");
    }
    BigDecimal opening =
        ledgerQueries.clientInvestedBalance(fundingAccountId, customerId, startDate.minusDays(1));
    BigDecimal closing = ledgerQueries.clientInvestedBalance(fundingAccountId, customerId, endDate);

    var movements = InvestedMovements.ZERO;
    for (var row :
        ledgerQueries.clientInvestedTotalsByType(
            fundingAccountId, customerId, startDate, endDate)) {
      // Lenient reader: a code the registry does not know never reaches a client document
      var type = TrustTransactionType.find(row.getTransactionType());
      if (type.isPresent()) {
        movements = movements.plus(type.get(), row.getTotal());
      }
    }

    var lines =
        interestRepository
            .findPostedForClientStatement(customerId, fundingAccountId, startDate, endDate)
            .stream()
            .map(
                e ->
                    new InvestedInterestLine(
                        e.getEntryDate(),
                        e.getGrossAmount(),
                        e.getFundShareAmount(),
                        e.getNetAmount()))
            .toList();
    BigDecimal gross =
        lines.stream()
            .map(InvestedInterestLine::grossAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal fundShare =
        lines.stream()
            .map(InvestedInterestLine::fundShareAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    return new InvestedStatement(
        opening,
        movements.placed(),
        gross,
        fundShare,
        movements.interestNet(),
        // INV_WITHDRAW effects are negative on the investment ledger
        movements.withdrawn().negate(),
        closing,
        lines);
  }

  /**
   * A period's signed {@code CLIENT_INVESTMENT} movements by type (648F) — the exhaustive switch
   * mirrors {@link LedgerTotals#plus}, so a new type must be placed here before it compiles.
   */
  private record InvestedMovements(
      BigDecimal placed, BigDecimal interestNet, BigDecimal withdrawn) {

    static final InvestedMovements ZERO =
        new InvestedMovements(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    InvestedMovements plus(TrustTransactionType type, BigDecimal total) {
      return switch (type) {
        // 657A: a taken-on section 86(4) investment's placement row is its TAKEON_INVESTMENT
        case INV_RECEIVE, TAKEON_INVESTMENT ->
            new InvestedMovements(placed.add(total), interestNet, withdrawn);
        case INV_INTEREST -> new InvestedMovements(placed, interestNet.add(total), withdrawn);
        case INV_WITHDRAW -> new InvestedMovements(placed, interestNet, withdrawn.add(total));
        // Never a group key here: clientInvestedTotalsByType groups a reversal under the type it
        // reverses, and these types never sit on a client's s86(4) CLIENT_INVESTMENT ledger
        case DEPOSIT,
            PAYMENT,
            FEE_TRANSFER,
            REFUND,
            TRANSFER_OUT,
            TRANSFER_IN,
            REVERSAL,
            FUND_INTEREST,
            FUND_SWEEP,
            BANK_CHARGE,
            CHARGE_REFUND,
            UNIDENTIFIED_DEPOSIT,
            UNIDENT_ALLOC_OUT,
            UNIDENT_ALLOC_IN,
            UNIDENT_FUND_PAY,
            INV_PLACE,
            FUND_INV_SHARE,
            INV_RETURN,
            FIRM_INV_PLACE,
            FIRM_INV_RECEIVE,
            FIRM_INV_WITHDRAW,
            FIRM_INV_RETURN,
            TAKEON_CLIENT,
            TAKEON_FUND,
            TAKEON_FUND_DEBIT,
            TAKEON_UNIDENTIFIED,
            TAKEON_FIRM_INV_OUT,
            TAKEON_FIRM_INV_IN,
            TAKEON_PAY_UNPRES,
            TAKEON_PAY_OFFSET,
            TAKEON_DEP_TRANSIT,
            TAKEON_DEP_OFFSET ->
            this;
      };
    }
  }

  // --- Private Helpers ---

  /** Posted totals per client on the account — one query whatever the number of clients. */
  private Map<UUID, LedgerTotals> totalsFor(UUID trustAccountId, Collection<UUID> customerIds) {
    if (customerIds.isEmpty()) {
      return Map.of();
    }
    return LedgerTotals.byCustomer(
        ledgerQueries.clientTotalsByType(trustAccountId, customerIds, null));
  }

  /**
   * Per client, what is held in section 86(4) investments placed from the account (648E, the
   * funding-account rule) — one query whatever the number of clients; absent client = zero.
   */
  private Map<UUID, BigDecimal> investedFor(UUID trustAccountId, Collection<UUID> customerIds) {
    if (customerIds.isEmpty()) {
      return Map.of();
    }
    return ledgerQueries.investedBalancesByClient(trustAccountId, customerIds).stream()
        .collect(Collectors.toMap(ClientBalanceRow::getCustomerId, ClientBalanceRow::getBalance));
  }

  private ClientLedgerCardResponse toResponse(
      UUID trustAccountId,
      UUID customerId,
      String customerName,
      Optional<ClientBalanceRow> summary,
      Optional<ClientLedgerCard> anchor,
      LedgerTotals totals,
      BigDecimal investedBalance) {
    return new ClientLedgerCardResponse(
        anchor.map(ClientLedgerCard::getId).orElse(null),
        trustAccountId,
        customerId,
        customerName,
        summary.map(ClientBalanceRow::getBalance).orElse(BigDecimal.ZERO),
        totals.deposits(),
        totals.payments(),
        totals.feeTransfers(),
        totals.refunds(),
        totals.transfersIn(),
        totals.transfersOut(),
        totals.investmentPlacements(),
        totals.investmentReturns(),
        totals.broughtForward(),
        investedBalance,
        summary.map(ClientBalanceRow::getLastTransactionDate).orElse(null),
        anchor.map(ClientLedgerCard::getCreatedAt).orElse(null),
        anchor.map(ClientLedgerCard::getUpdatedAt).orElse(null));
  }

  private static TrustTransactionResponse toTransactionResponse(ClientHistoryRow row) {
    return new TrustTransactionResponse(
        row.getId(),
        row.getTrustAccountId(),
        row.getTransactionType(),
        TrustTransactionTypeLabels.firmLabel(row.getTransactionType()),
        TrustTransactionDirection.ofRow(row.getTransactionType(), row.getReversedType()),
        row.getLedger() == null ? null : TrustLedger.valueOf(row.getLedger()),
        row.getAmount(),
        row.getCustomerId(),
        row.getProjectId(),
        row.getCreditorAccountId(),
        row.getInvestmentId(),
        row.getCounterpartyCustomerId(),
        row.getInvoiceId(),
        row.getReference(),
        row.getDescription(),
        row.getTransactionDate(),
        row.getStatus(),
        row.getPostedAt(),
        row.getApprovedBy(),
        row.getApprovedAt(),
        row.getSecondApprovedBy(),
        row.getSecondApprovedAt(),
        row.getRejectedBy(),
        row.getRejectedAt(),
        row.getRejectionReason(),
        row.getReversalOf(),
        row.getReversedById(),
        Boolean.TRUE.equals(row.getReversalPending()),
        row.getBankStatementLineId(),
        row.getRecordedBy(),
        row.getCreatedAt(),
        row.getRunningBalance(),
        // a client-ledger row is never a bank charge (647A, chk_trust_txn_charge)
        null,
        null);
  }
}
