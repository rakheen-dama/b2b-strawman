package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.EntityNoun;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.ClientBalanceRow;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.ClientHistoryRow;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedgerQueryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.TrustTransactionResponse;
import java.math.BigDecimal;
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
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientLedgerService {

  private static final String MODULE_ID = "trust_accounting";

  private final ClientLedgerCardRepository ledgerCardRepository;
  private final TrustTransactionRepository transactionRepository;
  private final TrustLedgerQueryRepository ledgerQueries;
  private final CustomerRepository customerRepository;
  private final VerticalModuleGuard moduleGuard;

  public ClientLedgerService(
      ClientLedgerCardRepository ledgerCardRepository,
      TrustTransactionRepository transactionRepository,
      TrustLedgerQueryRepository ledgerQueries,
      CustomerRepository customerRepository,
      VerticalModuleGuard moduleGuard) {
    this.ledgerCardRepository = ledgerCardRepository;
    this.transactionRepository = transactionRepository;
    this.ledgerQueries = ledgerQueries;
    this.customerRepository = customerRepository;
    this.moduleGuard = moduleGuard;
  }

  // --- DTO Records ---

  /**
   * One client's ledger on one account. {@code balance}, the totals and {@code lastTransactionDate}
   * are derived from the posted rows (ADR-378); {@code id}, {@code createdAt} and {@code updatedAt}
   * are the client's lock anchor's, null when it has none.
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
      // G89-F13: every total is derived from the posted ledger (LedgerTotals), so
      // deposits + transfers in - payments - fee transfers - refunds - transfers out == balance
      BigDecimal totalRefunds,
      BigDecimal totalTransfersIn,
      BigDecimal totalTransfersOut,
      LocalDate lastTransactionDate,
      Instant createdAt,
      Instant updatedAt) {}

  public record LedgerStatementResponse(
      BigDecimal openingBalance,
      BigDecimal closingBalance,
      List<LedgerStatementLine> transactions) {}

  public record TotalBalanceResponse(BigDecimal balance) {}

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

  // --- Service Methods ---

  @Transactional(readOnly = true)
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
    if (summary.isEmpty() && anchor.isEmpty()) {
      return emptyLedgerCardResponse(customer, trustAccountId);
    }
    return toResponse(
        trustAccountId,
        customerId,
        customer.getName(),
        summary,
        anchor,
        totalsFor(trustAccountId, List.of(customerId)).getOrDefault(customerId, LedgerTotals.ZERO));
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
  @Transactional(readOnly = true)
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

    return page.map(
        row ->
            toResponse(
                trustAccountId,
                row.getCustomerId(),
                customerNames.getOrDefault(row.getCustomerId(), "(deleted customer)"),
                Optional.of(row),
                Optional.ofNullable(anchors.get(row.getCustomerId())),
                totals.getOrDefault(row.getCustomerId(), LedgerTotals.ZERO)));
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

  @Transactional(readOnly = true)
  public TotalBalanceResponse getTotalTrustBalance(UUID trustAccountId) {
    moduleGuard.requireModule(MODULE_ID);
    return new TotalBalanceResponse(ledgerQueries.clientLedgerTotal(trustAccountId, null));
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

  // --- Private Helpers ---

  /** Posted totals per client on the account — one query whatever the number of clients. */
  private Map<UUID, LedgerTotals> totalsFor(UUID trustAccountId, Collection<UUID> customerIds) {
    if (customerIds.isEmpty()) {
      return Map.of();
    }
    return LedgerTotals.byCustomer(
        ledgerQueries.clientTotalsByType(trustAccountId, customerIds, null));
  }

  private ClientLedgerCardResponse toResponse(
      UUID trustAccountId,
      UUID customerId,
      String customerName,
      Optional<ClientBalanceRow> summary,
      Optional<ClientLedgerCard> anchor,
      LedgerTotals totals) {
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
        summary.map(ClientBalanceRow::getLastTransactionDate).orElse(null),
        anchor.map(ClientLedgerCard::getCreatedAt).orElse(null),
        anchor.map(ClientLedgerCard::getUpdatedAt).orElse(null));
  }

  private static TrustTransactionResponse toTransactionResponse(ClientHistoryRow row) {
    return new TrustTransactionResponse(
        row.getId(),
        row.getTrustAccountId(),
        row.getTransactionType(),
        row.getAmount(),
        row.getCustomerId(),
        row.getProjectId(),
        row.getCounterpartyCustomerId(),
        row.getInvoiceId(),
        row.getReference(),
        row.getDescription(),
        row.getTransactionDate(),
        row.getStatus(),
        row.getApprovedBy(),
        row.getApprovedAt(),
        row.getSecondApprovedBy(),
        row.getSecondApprovedAt(),
        row.getRejectedBy(),
        row.getRejectedAt(),
        row.getRejectionReason(),
        row.getReversalOf(),
        row.getReversedById(),
        row.getBankStatementLineId(),
        row.getRecordedBy(),
        row.getCreatedAt(),
        row.getRunningBalance());
  }
}
