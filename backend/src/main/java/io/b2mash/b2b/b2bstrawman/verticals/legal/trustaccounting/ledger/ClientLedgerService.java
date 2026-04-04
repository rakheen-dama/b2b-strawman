package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.TrustTransactionResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientLedgerService {

  private static final String MODULE_ID = "trust_accounting";

  private final ClientLedgerCardRepository ledgerCardRepository;
  private final TrustTransactionRepository transactionRepository;
  private final VerticalModuleGuard moduleGuard;

  public ClientLedgerService(
      ClientLedgerCardRepository ledgerCardRepository,
      TrustTransactionRepository transactionRepository,
      VerticalModuleGuard moduleGuard) {
    this.ledgerCardRepository = ledgerCardRepository;
    this.transactionRepository = transactionRepository;
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
      UUID customerId,
      BigDecimal balance,
      BigDecimal totalDeposits,
      BigDecimal totalPayments,
      BigDecimal totalFeeTransfers,
      BigDecimal totalInterestCredited,
      LocalDate lastTransactionDate,
      Instant createdAt,
      Instant updatedAt) {}

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

    var ledgerCard =
        ledgerCardRepository
            .findByTrustAccountIdAndCustomerId(trustAccountId, customerId)
            .orElseThrow(() -> new ResourceNotFoundException("ClientLedgerCard", customerId));

    return toResponse(ledgerCard);
  }

  @Transactional(readOnly = true)
  public Page<ClientLedgerCardResponse> listClientLedgers(UUID trustAccountId, Pageable pageable) {
    moduleGuard.requireModule(MODULE_ID);

    return ledgerCardRepository
        .findByTrustAccountId(trustAccountId, pageable)
        .map(this::toResponse);
  }

  @Transactional(readOnly = true)
  public Page<TrustTransactionResponse> getClientTransactionHistory(
      UUID customerId, UUID trustAccountId, Pageable pageable) {
    moduleGuard.requireModule(MODULE_ID);

    return transactionRepository
        .findByCustomerIdAndTrustAccountIdOrderByTransactionDateDesc(
            customerId, trustAccountId, pageable)
        .map(this::toTransactionResponse);
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
  public BigDecimal getTotalTrustBalance(UUID trustAccountId) {
    moduleGuard.requireModule(MODULE_ID);
    return ledgerCardRepository.calculateTotalTrustBalance(trustAccountId);
  }

  @Transactional(readOnly = true)
  public List<LedgerStatementLine> getClientLedgerStatement(
      UUID customerId, UUID trustAccountId, LocalDate startDate, LocalDate endDate) {
    moduleGuard.requireModule(MODULE_ID);

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

    return lines;
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
    throw new IllegalStateException("Unknown transaction type: " + txn.getTransactionType());
  }

  // --- Private Helpers ---

  private ClientLedgerCardResponse toResponse(ClientLedgerCard entity) {
    return new ClientLedgerCardResponse(
        entity.getId(),
        entity.getTrustAccountId(),
        entity.getCustomerId(),
        entity.getBalance(),
        entity.getTotalDeposits(),
        entity.getTotalPayments(),
        entity.getTotalFeeTransfers(),
        entity.getTotalInterestCredited(),
        entity.getLastTransactionDate(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  private TrustTransactionResponse toTransactionResponse(
      io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction
          entity) {
    return new TrustTransactionResponse(
        entity.getId(),
        entity.getTrustAccountId(),
        entity.getTransactionType(),
        entity.getAmount(),
        entity.getCustomerId(),
        entity.getProjectId(),
        entity.getCounterpartyCustomerId(),
        entity.getInvoiceId(),
        entity.getReference(),
        entity.getDescription(),
        entity.getTransactionDate(),
        entity.getStatus(),
        entity.getApprovedBy(),
        entity.getApprovedAt(),
        entity.getSecondApprovedBy(),
        entity.getSecondApprovedAt(),
        entity.getRejectedBy(),
        entity.getRejectedAt(),
        entity.getRejectionReason(),
        entity.getReversalOf(),
        entity.getReversedById(),
        entity.getBankStatementLineId(),
        entity.getRecordedBy(),
        entity.getCreatedAt());
  }
}
