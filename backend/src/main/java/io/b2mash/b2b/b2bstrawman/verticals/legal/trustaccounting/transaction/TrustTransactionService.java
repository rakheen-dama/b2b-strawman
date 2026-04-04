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
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  private final EntityManager entityManager;

  public TrustTransactionService(
      TrustTransactionRepository transactionRepository,
      ClientLedgerCardRepository ledgerCardRepository,
      TrustAccountRepository trustAccountRepository,
      CustomerRepository customerRepository,
      VerticalModuleGuard moduleGuard,
      AuditService auditService,
      EntityManager entityManager) {
    this.transactionRepository = transactionRepository;
    this.ledgerCardRepository = ledgerCardRepository;
    this.trustAccountRepository = trustAccountRepository;
    this.customerRepository = customerRepository;
    this.moduleGuard = moduleGuard;
    this.auditService = auditService;
    this.entityManager = entityManager;
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
    // deposits. If two concurrent inserts race, the second hits the unique constraint;
    // we catch that and re-query with the lock to update the now-existing row.
    var ledgerCard =
        ledgerCardRepository
            .findByAccountAndCustomerForUpdate(trustAccountId, request.customerId())
            .orElse(null);

    if (ledgerCard == null) {
      ledgerCard = upsertNewLedgerCard(trustAccountId, request.customerId());
    }

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

    // Upsert target ledger — handle concurrent insert race
    if (targetLedger == null) {
      targetLedger = upsertNewLedgerCard(trustAccountId, request.targetCustomerId());
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

  // --- 440.9: Transaction Reversal ---

  private static final Set<String> CREDIT_TYPES =
      Set.of("DEPOSIT", "TRANSFER_IN", "INTEREST_CREDIT");

  private static final Set<String> DEBIT_TYPES =
      Set.of("PAYMENT", "TRANSFER_OUT", "FEE_TRANSFER", "REFUND");

  @Transactional
  public TrustTransactionResponse reverseTransaction(UUID transactionId, String reason) {
    moduleGuard.requireModule(MODULE_ID);

    // Validate reason is provided
    if (reason == null || reason.isBlank()) {
      throw new InvalidStateException("Invalid reversal", "Reversal reason is required");
    }

    var original =
        transactionRepository
            .findById(transactionId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustTransaction", transactionId));

    // Validate trust account is not closed
    var account =
        trustAccountRepository
            .findById(original.getTrustAccountId())
            .orElseThrow(
                () -> new ResourceNotFoundException("TrustAccount", original.getTrustAccountId()));

    if (TrustAccountStatus.CLOSED == account.getStatus()) {
      throw new InvalidStateException("Cannot reverse transaction", "Trust account is closed");
    }

    // Validate status — only APPROVED transactions can be reversed
    if (!"APPROVED".equals(original.getStatus())) {
      throw new InvalidStateException(
          "Cannot reverse transaction", "Transaction must be in APPROVED status to be reversed");
    }

    // Validate not already reversed — check both the original's reversedById (set for debit
    // reversals) and whether a reversal transaction already exists (covers credit reversals
    // where the original is not yet marked REVERSED until approval in Epic 441).
    if (original.getReversedById() != null
        || transactionRepository.existsByReversalOf(original.getId())) {
      throw new InvalidStateException(
          "Cannot reverse transaction", "Transaction has already been reversed");
    }

    var memberId = RequestScopes.requireMemberId();
    boolean isCreditType = CREDIT_TYPES.contains(original.getTransactionType());
    boolean isDebitType = DEBIT_TYPES.contains(original.getTransactionType());

    // Determine reversal status based on original type
    // Debit reversals (add money back) are immediate; credit reversals (remove money) need approval
    String reversalStatus;
    if (isDebitType) {
      reversalStatus = "RECORDED";
    } else if (isCreditType) {
      reversalStatus = "AWAITING_APPROVAL";
    } else {
      throw new InvalidStateException(
          "Cannot reverse transaction",
          "Transaction type " + original.getTransactionType() + " cannot be reversed");
    }

    // Build reversal reference, truncating to 200-char column limit
    String reversalRef = "REV-" + original.getReference();
    if (reversalRef.length() > 200) {
      reversalRef = reversalRef.substring(0, 200);
    }

    var reversal =
        new TrustTransaction(
            original.getTrustAccountId(),
            "REVERSAL",
            original.getAmount(),
            original.getCustomerId(),
            original.getProjectId(),
            original.getCounterpartyCustomerId(),
            reversalRef,
            reason,
            LocalDate.now(),
            reversalStatus,
            memberId);

    // Set reversal_of to link to the original
    reversal.setReversalOf(original.getId());

    var savedReversal = transactionRepository.save(reversal);

    // For debit reversals (immediate): mark original as REVERSED and update ledger.
    // RECORDED status for debit reversals is the final status — no approval needed,
    // analogous to DEPOSIT's RECORDED status. The cashbook query includes RECORDED.
    if (isDebitType) {
      original.setReversedById(savedReversal.getId());
      original.setStatus("REVERSED");
      transactionRepository.save(original);

      if (original.getCustomerId() != null) {
        var ledgerCard =
            ledgerCardRepository
                .findByAccountAndCustomerForUpdate(
                    original.getTrustAccountId(), original.getCustomerId())
                .orElse(null);

        if (ledgerCard != null) {
          ledgerCard.creditBalance(original.getAmount(), LocalDate.now());
          ledgerCardRepository.save(ledgerCard);
        }
      }
    }
    // For credit reversals (AWAITING_APPROVAL): do NOT mark the original as REVERSED yet.
    // The original remains APPROVED until the reversal is itself approved (Epic 441 scope).
    // Only the reversal_of FK on the new reversal transaction links them for now.

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_transaction.reversed")
            .entityType("trust_transaction")
            .entityId(savedReversal.getId())
            .details(
                Map.of(
                    "original_transaction_id", original.getId().toString(),
                    "original_type", original.getTransactionType(),
                    "amount", original.getAmount().toString(),
                    "reversal_status", reversalStatus,
                    "reason", reason))
            .build());

    return toResponse(savedReversal);
  }

  // --- 440.10: Cashbook Balance ---

  @Transactional(readOnly = true)
  public BigDecimal getCashbookBalance(UUID trustAccountId) {
    moduleGuard.requireModule(MODULE_ID);

    trustAccountRepository
        .findById(trustAccountId)
        .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", trustAccountId));

    return transactionRepository.calculateCashbookBalance(trustAccountId);
  }

  // --- Private Helpers ---

  /**
   * Atomically ensures a ClientLedgerCard exists for the given account and customer. Uses a native
   * INSERT ... ON CONFLICT DO NOTHING to handle the race where two concurrent transactions both see
   * no existing row. After the upsert, re-queries with FOR UPDATE to lock and return the row.
   */
  private ClientLedgerCard upsertNewLedgerCard(UUID trustAccountId, UUID customerId) {
    entityManager
        .createNativeQuery(
            """
            INSERT INTO client_ledger_cards (id, trust_account_id, customer_id, balance,
                total_deposits, total_payments, total_fee_transfers, total_interest_credited,
                created_at, updated_at)
            VALUES (gen_random_uuid(), :accountId, :customerId, 0, 0, 0, 0, 0, now(), now())
            ON CONFLICT (trust_account_id, customer_id) DO NOTHING
            """)
        .setParameter("accountId", trustAccountId)
        .setParameter("customerId", customerId)
        .executeUpdate();

    return ledgerCardRepository
        .findByAccountAndCustomerForUpdate(trustAccountId, customerId)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Ledger card missing after upsert for account="
                        + trustAccountId
                        + " customer="
                        + customerId));
  }

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
