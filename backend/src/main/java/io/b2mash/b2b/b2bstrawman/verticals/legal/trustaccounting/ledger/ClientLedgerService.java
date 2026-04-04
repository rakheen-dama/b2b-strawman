package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.TrustTransactionResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
  public List<TrustTransactionResponse> getClientTransactionHistory(
      UUID customerId, UUID trustAccountId) {
    moduleGuard.requireModule(MODULE_ID);

    return transactionRepository
        .findByCustomerIdAndTrustAccountIdOrderByTransactionDateDesc(customerId, trustAccountId)
        .stream()
        .map(this::toTransactionResponse)
        .toList();
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
