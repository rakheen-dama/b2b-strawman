package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.EntityNoun;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.ClientHistoryRow;
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
import java.util.Set;
import java.util.UUID;
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
  private final CustomerRepository customerRepository;
  private final VerticalModuleGuard moduleGuard;

  public ClientLedgerService(
      ClientLedgerCardRepository ledgerCardRepository,
      TrustTransactionRepository transactionRepository,
      CustomerRepository customerRepository,
      VerticalModuleGuard moduleGuard) {
    this.ledgerCardRepository = ledgerCardRepository;
    this.transactionRepository = transactionRepository;
    this.customerRepository = customerRepository;
    this.moduleGuard = moduleGuard;
  }

  private static final Set<String> CREDIT_TYPES =
      Set.of("DEPOSIT", "TRANSFER_IN", "INTEREST_CREDIT");

  private static final Set<String> DEBIT_TYPES =
      Set.of("PAYMENT", "TRANSFER_OUT", "FEE_TRANSFER", "REFUND", "INTEREST_LPFF");

  // --- DTO Records ---

  public record ClientLedgerCardResponse(
      UUID id,
      UUID trustAccountId,
      UUID clientId,
      String clientName,
      BigDecimal balance,
      BigDecimal totalDeposits,
      BigDecimal totalPayments,
      BigDecimal totalFeeTransfers,
      BigDecimal totalInterestCredited,
      // G89-F13: every total is derived from the posted ledger (LedgerTotals), so
      // deposits + interest + transfers in - payments - fee transfers - refunds - transfers out
      // == balance
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
      BigDecimal runningBalance) {}

  // --- Service Methods ---

  @Transactional(readOnly = true)
  public ClientLedgerCardResponse getClientLedger(UUID customerId, UUID trustAccountId) {
    moduleGuard.requireModule(MODULE_ID);

    return ledgerCardRepository
        .findByTrustAccountIdAndCustomerId(trustAccountId, customerId)
        .map(
            card ->
                toResponse(
                    card,
                    customerNameOf(card),
                    totalsFor(trustAccountId, List.of(customerId))
                        .getOrDefault(customerId, LedgerTotals.ZERO)))
        .orElseGet(
            () -> {
              // No ledger card yet is a valid state — card is lazily created on the first
              // deposit/payment via TrustTransactionService.applyTransaction. But an unknown
              // customer ID is a genuine 404. Resolve the customer first so we can distinguish
              // "no card yet" (valid customer, synthetic empty response) from "customer not found"
              // (throw). See GAP-S4-06 + PR #1004 review finding.
              Customer customer =
                  customerRepository
                      .findById(customerId)
                      .orElseThrow(
                          () -> new ResourceNotFoundException(EntityNoun.CLIENT, customerId));
              return emptyLedgerCardResponse(customer, trustAccountId);
            });
  }

  /**
   * Synthetic zero-balance response for customers who have no ledger card yet (i.e. no trust
   * transactions have been recorded). The ledger card is lazily created on the first
   * deposit/payment via {@code TrustTransactionService.applyTransaction}. Returning a synthetic
   * empty response here lets the "Trust" tab on every new matter render a clean R0.00 state instead
   * of a 404 error. Caller must ensure the customer exists — see {@link #getClientLedger}.
   */
  private ClientLedgerCardResponse emptyLedgerCardResponse(Customer customer, UUID trustAccountId) {
    return new ClientLedgerCardResponse(
        null, // no persistent card id yet
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
        BigDecimal.ZERO,
        null, // lastTransactionDate
        null, // createdAt
        null); // updatedAt
  }

  @Transactional(readOnly = true)
  public Page<ClientLedgerCardResponse> listClientLedgers(UUID trustAccountId, Pageable pageable) {
    moduleGuard.requireModule(MODULE_ID);

    Page<ClientLedgerCard> page =
        ledgerCardRepository.findByTrustAccountId(trustAccountId, pageable);

    // Batch-load customer names to avoid N+1
    Set<UUID> customerIds =
        page.getContent().stream().map(ClientLedgerCard::getCustomerId).collect(Collectors.toSet());
    Map<UUID, String> customerNames =
        customerRepository.findByIdIn(customerIds).stream()
            .collect(Collectors.toMap(c -> c.getId(), c -> c.getName()));
    // One batch query for the page's derived totals (G89-F13)
    Map<UUID, LedgerTotals> totals = totalsFor(trustAccountId, customerIds);

    return page.map(
        entity ->
            toResponse(
                entity,
                customerNames.getOrDefault(entity.getCustomerId(), "(deleted customer)"),
                totals.getOrDefault(entity.getCustomerId(), LedgerTotals.ZERO)));
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
    return transactionRepository.calculateClientBalanceAsOfDate(
        customerId, trustAccountId, asOfDate);
  }

  @Transactional(readOnly = true)
  public TotalBalanceResponse getTotalTrustBalance(UUID trustAccountId) {
    moduleGuard.requireModule(MODULE_ID);
    return new TotalBalanceResponse(
        ledgerCardRepository.calculateTotalTrustBalance(trustAccountId));
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
        transactionRepository.calculateClientBalanceAsOfDate(
            customerId, trustAccountId, startDate.minusDays(1));

    // Fetch transactions in the date range
    List<TrustTransaction> transactions =
        transactionRepository.findForStatement(customerId, trustAccountId, startDate, endDate);

    // Compute running balance
    List<LedgerStatementLine> lines = new ArrayList<>();
    BigDecimal runningBalance = openingBalance;

    for (TrustTransaction txn : transactions) {
      BigDecimal effect = computeLedgerEffect(txn);
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
              runningBalance));
    }

    BigDecimal closingBalance = lines.isEmpty() ? openingBalance : lines.getLast().runningBalance();

    return new LedgerStatementResponse(openingBalance, closingBalance, lines);
  }

  /**
   * Computes the ledger effect of a transaction on the client's balance. Credit types add to
   * balance; debit types subtract from balance. REVERSAL transactions are excluded from statement
   * queries (they should not appear here), but if they do, this will throw. Reversals affect
   * balances through direct ledger card updates (debit reversals) or the approval flow (credit
   * reversals, Epic 441).
   */
  private BigDecimal computeLedgerEffect(TrustTransaction txn) {
    if (CREDIT_TYPES.contains(txn.getTransactionType())) {
      return txn.getAmount();
    } else if (DEBIT_TYPES.contains(txn.getTransactionType())) {
      return txn.getAmount().negate();
    }
    throw new InvalidStateException(
        "Unsupported transaction type for ledger statement", txn.getTransactionType());
  }

  // --- Private Helpers ---

  /** Posted totals per client on the account — one query whatever the number of clients. */
  private Map<UUID, LedgerTotals> totalsFor(UUID trustAccountId, Collection<UUID> customerIds) {
    if (customerIds.isEmpty()) {
      return Map.of();
    }
    return LedgerTotals.byCustomer(
        transactionRepository.sumPostedByCustomerAndType(trustAccountId, customerIds));
  }

  private String customerNameOf(ClientLedgerCard entity) {
    return customerRepository
        .findById(entity.getCustomerId())
        .map(c -> c.getName())
        .orElse("(deleted customer)");
  }

  private ClientLedgerCardResponse toResponse(
      ClientLedgerCard entity, String customerName, LedgerTotals totals) {
    return new ClientLedgerCardResponse(
        entity.getId(),
        entity.getTrustAccountId(),
        entity.getCustomerId(),
        customerName,
        entity.getBalance(),
        totals.deposits(),
        totals.payments(),
        totals.feeTransfers(),
        totals.interestCredited(),
        totals.refunds(),
        totals.transfersIn(),
        totals.transfersOut(),
        entity.getLastTransactionDate(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
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
