package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrustTransactionService {

  private static final String MODULE_ID = "trust_accounting";

  private final TrustTransactionRepository transactionRepository;
  private final ClientLedgerCardRepository ledgerCardRepository;
  private final TrustAccountRepository trustAccountRepository;
  private final CustomerRepository customerRepository;
  private final VerticalModuleGuard moduleGuard;
  private final AuditService auditService;

  public TrustTransactionService(
      TrustTransactionRepository transactionRepository,
      ClientLedgerCardRepository ledgerCardRepository,
      TrustAccountRepository trustAccountRepository,
      CustomerRepository customerRepository,
      VerticalModuleGuard moduleGuard,
      AuditService auditService) {
    this.transactionRepository = transactionRepository;
    this.ledgerCardRepository = ledgerCardRepository;
    this.trustAccountRepository = trustAccountRepository;
    this.customerRepository = customerRepository;
    this.moduleGuard = moduleGuard;
    this.auditService = auditService;
  }

  // --- DTO Records ---

  public record RecordDepositRequest(
      UUID customerId,
      UUID projectId,
      BigDecimal amount,
      String reference,
      String description,
      LocalDate transactionDate) {}

  public record RecordTransferRequest(
      UUID sourceCustomerId,
      UUID targetCustomerId,
      UUID projectId,
      BigDecimal amount,
      String reference,
      String description,
      LocalDate transactionDate) {}

  public record TrustTransactionResponse(
      UUID id,
      UUID trustAccountId,
      String transactionType,
      BigDecimal amount,
      UUID customerId,
      UUID projectId,
      UUID counterpartyCustomerId,
      UUID invoiceId,
      String reference,
      String description,
      LocalDate transactionDate,
      String status,
      UUID approvedBy,
      Instant approvedAt,
      UUID secondApprovedBy,
      Instant secondApprovedAt,
      UUID rejectedBy,
      Instant rejectedAt,
      String rejectionReason,
      UUID reversalOf,
      UUID reversedById,
      UUID bankStatementLineId,
      UUID recordedBy,
      Instant createdAt) {}

  // --- Service Methods ---

  @Transactional
  public TrustTransactionResponse recordDeposit(UUID trustAccountId, RecordDepositRequest request) {
    moduleGuard.requireModule(MODULE_ID);
    validateAmount(request.amount());
    validateReference(request.reference());

    var account =
        trustAccountRepository
            .findById(trustAccountId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", trustAccountId));

    if (TrustAccountStatus.CLOSED == account.getStatus()) {
      throw new InvalidStateException("Cannot record deposit", "Trust account is closed");
    }

    var customer =
        customerRepository
            .findById(request.customerId())
            .orElseThrow(() -> new ResourceNotFoundException("Customer", request.customerId()));

    var memberId = RequestScopes.requireMemberId();

    var transaction =
        new TrustTransaction(
            trustAccountId,
            "DEPOSIT",
            request.amount(),
            request.customerId(),
            request.projectId(),
            null,
            request.reference(),
            request.description(),
            request.transactionDate(),
            "RECORDED",
            memberId);

    var saved = transactionRepository.save(transaction);

    // Upsert client ledger card — use FOR UPDATE lock to prevent lost updates on concurrent
    // deposits
    var ledgerCard =
        ledgerCardRepository
            .findByAccountAndCustomerForUpdate(trustAccountId, request.customerId())
            .orElseGet(() -> new ClientLedgerCard(trustAccountId, request.customerId()));

    ledgerCard.addDeposit(request.amount(), request.transactionDate());
    ledgerCardRepository.save(ledgerCard);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_deposit.recorded")
            .entityType("trust_transaction")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "amount", request.amount().toString(),
                    "customer_id", request.customerId().toString(),
                    "customer_name", customer.getName(),
                    "reference", request.reference()))
            .build());

    return toResponse(saved);
  }

  @Transactional
  public List<TrustTransactionResponse> recordTransfer(
      UUID trustAccountId, RecordTransferRequest request) {
    moduleGuard.requireModule(MODULE_ID);
    validateAmount(request.amount());
    validateReference(request.reference());

    var account =
        trustAccountRepository
            .findById(trustAccountId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", trustAccountId));

    if (TrustAccountStatus.CLOSED == account.getStatus()) {
      throw new InvalidStateException("Cannot record transfer", "Trust account is closed");
    }

    if (request.sourceCustomerId().equals(request.targetCustomerId())) {
      throw new InvalidStateException(
          "Invalid transfer", "Source and target customer cannot be the same");
    }

    var sourceCustomer =
        customerRepository
            .findById(request.sourceCustomerId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Customer", request.sourceCustomerId()));

    customerRepository
        .findById(request.targetCustomerId())
        .orElseThrow(() -> new ResourceNotFoundException("Customer", request.targetCustomerId()));

    var memberId = RequestScopes.requireMemberId();

    // Determine lock order: ascending UUID to prevent deadlocks
    UUID firstId =
        request.sourceCustomerId().compareTo(request.targetCustomerId()) < 0
            ? request.sourceCustomerId()
            : request.targetCustomerId();
    UUID secondId =
        request.sourceCustomerId().compareTo(request.targetCustomerId()) < 0
            ? request.targetCustomerId()
            : request.sourceCustomerId();

    // Acquire locks in deterministic order
    var firstLedger =
        ledgerCardRepository
            .findByAccountAndCustomerForUpdate(trustAccountId, firstId)
            .orElse(null);
    var secondLedger =
        ledgerCardRepository
            .findByAccountAndCustomerForUpdate(trustAccountId, secondId)
            .orElse(null);

    // Resolve source and target ledger cards
    ClientLedgerCard sourceLedger;
    ClientLedgerCard targetLedger;
    if (firstId.equals(request.sourceCustomerId())) {
      sourceLedger = firstLedger;
      targetLedger = secondLedger;
    } else {
      sourceLedger = secondLedger;
      targetLedger = firstLedger;
    }

    // Source ledger must exist and have sufficient balance
    if (sourceLedger == null) {
      throw new InvalidStateException(
          "Insufficient trust balance",
          "Insufficient trust balance for "
              + sourceCustomer.getName()
              + ". Available: R0.00, Requested: R"
              + request.amount());
    }

    if (sourceLedger.getBalance().compareTo(request.amount()) < 0) {
      throw new InvalidStateException(
          "Insufficient trust balance",
          "Insufficient trust balance for "
              + sourceCustomer.getName()
              + ". Available: R"
              + sourceLedger.getBalance()
              + ", Requested: R"
              + request.amount());
    }

    // Create paired transactions
    var transferOut =
        new TrustTransaction(
            trustAccountId,
            "TRANSFER_OUT",
            request.amount(),
            request.sourceCustomerId(),
            request.projectId(),
            request.targetCustomerId(),
            request.reference(),
            request.description(),
            request.transactionDate(),
            "RECORDED",
            memberId);

    var transferIn =
        new TrustTransaction(
            trustAccountId,
            "TRANSFER_IN",
            request.amount(),
            request.targetCustomerId(),
            request.projectId(),
            request.sourceCustomerId(),
            request.reference(),
            request.description(),
            request.transactionDate(),
            "RECORDED",
            memberId);

    var savedOut = transactionRepository.save(transferOut);
    var savedIn = transactionRepository.save(transferIn);

    // Update source ledger
    sourceLedger.debitBalance(request.amount(), request.transactionDate());
    ledgerCardRepository.save(sourceLedger);

    // Upsert target ledger
    if (targetLedger == null) {
      targetLedger = new ClientLedgerCard(trustAccountId, request.targetCustomerId());
    }
    targetLedger.creditBalance(request.amount(), request.transactionDate());
    ledgerCardRepository.save(targetLedger);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_transfer.recorded")
            .entityType("trust_transaction")
            .entityId(savedOut.getId())
            .details(
                Map.of(
                    "amount", request.amount().toString(),
                    "source_customer_id", request.sourceCustomerId().toString(),
                    "target_customer_id", request.targetCustomerId().toString(),
                    "reference", request.reference()))
            .build());

    return List.of(toResponse(savedOut), toResponse(savedIn));
  }

  // --- Private Helpers ---

  private void validateAmount(BigDecimal amount) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new InvalidStateException(
          "Invalid amount", "Amount must be a positive value, got: " + amount);
    }
  }

  private void validateReference(String reference) {
    if (reference == null || reference.isBlank()) {
      throw new InvalidStateException("Invalid reference", "Reference must not be blank");
    }
  }

  private TrustTransactionResponse toResponse(TrustTransaction entity) {
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
