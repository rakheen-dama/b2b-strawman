package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceService;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustTransactionApprovalEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrustTransactionService {

  private static final String MODULE_ID = "trust_accounting";

  private final TrustTransactionRepository transactionRepository;
  private final ClientLedgerCardRepository ledgerCardRepository;
  private final TrustAccountRepository trustAccountRepository;
  private final CustomerRepository customerRepository;
  private final InvoiceRepository invoiceRepository;
  private final InvoiceService invoiceService;
  private final VerticalModuleGuard moduleGuard;
  private final AuditService auditService;
  private final EntityManager entityManager;
  private final ApplicationEventPublisher eventPublisher;
  private final MemberNameResolver memberNameResolver;

  public TrustTransactionService(
      TrustTransactionRepository transactionRepository,
      ClientLedgerCardRepository ledgerCardRepository,
      TrustAccountRepository trustAccountRepository,
      CustomerRepository customerRepository,
      InvoiceRepository invoiceRepository,
      InvoiceService invoiceService,
      VerticalModuleGuard moduleGuard,
      AuditService auditService,
      EntityManager entityManager,
      ApplicationEventPublisher eventPublisher,
      MemberNameResolver memberNameResolver) {
    this.transactionRepository = transactionRepository;
    this.ledgerCardRepository = ledgerCardRepository;
    this.trustAccountRepository = trustAccountRepository;
    this.customerRepository = customerRepository;
    this.invoiceRepository = invoiceRepository;
    this.invoiceService = invoiceService;
    this.moduleGuard = moduleGuard;
    this.auditService = auditService;
    this.entityManager = entityManager;
    this.eventPublisher = eventPublisher;
    this.memberNameResolver = memberNameResolver;
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

  public record RecordPaymentRequest(
      UUID customerId,
      UUID projectId,
      BigDecimal amount,
      String reference,
      String description,
      LocalDate transactionDate) {}

  public record RecordFeeTransferRequest(
      UUID customerId, UUID invoiceId, BigDecimal amount, String reference) {}

  public record RecordRefundRequest(
      UUID customerId,
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

  public record EnrichedTrustTransactionResponse(
      UUID id,
      UUID trustAccountId,
      String transactionType,
      BigDecimal amount,
      UUID customerId,
      String customerName,
      UUID projectId,
      UUID counterpartyCustomerId,
      UUID invoiceId,
      String reference,
      String description,
      LocalDate transactionDate,
      String status,
      UUID approvedBy,
      String approvedByName,
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
      String recordedByName,
      Instant createdAt,
      BigDecimal clientBalance) {}

  public record TransactionFilters(
      LocalDate dateFrom,
      LocalDate dateTo,
      String type,
      String status,
      UUID customerId,
      UUID projectId) {}

  public record RejectRequest(@NotBlank String reason) {}

  public record ReverseRequest(@NotBlank String reason) {}

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

  // --- 441.1: Record Payment ---

  @Transactional
  public TrustTransactionResponse recordPayment(UUID trustAccountId, RecordPaymentRequest request) {
    moduleGuard.requireModule(MODULE_ID);
    validateAmount(request.amount());
    validateReference(request.reference());

    var account =
        trustAccountRepository
            .findById(trustAccountId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", trustAccountId));

    if (TrustAccountStatus.CLOSED == account.getStatus()) {
      throw new InvalidStateException("Cannot record payment", "Trust account is closed");
    }

    var customer =
        customerRepository
            .findById(request.customerId())
            .orElseThrow(() -> new ResourceNotFoundException("Customer", request.customerId()));

    var memberId = RequestScopes.requireMemberId();

    var transaction =
        new TrustTransaction(
            trustAccountId,
            "PAYMENT",
            request.amount(),
            request.customerId(),
            request.projectId(),
            null,
            request.reference(),
            request.description(),
            request.transactionDate(),
            "AWAITING_APPROVAL",
            memberId);

    var saved = transactionRepository.save(transaction);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_payment.recorded")
            .entityType("trust_transaction")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "amount", request.amount().toString(),
                    "customer_id", request.customerId().toString(),
                    "customer_name", customer.getName(),
                    "reference", request.reference()))
            .build());

    eventPublisher.publishEvent(
        TrustTransactionApprovalEvent.awaitingApproval(
            saved.getId(),
            trustAccountId,
            "PAYMENT",
            request.amount(),
            request.customerId(),
            memberId,
            RequestScopes.getTenantIdOrNull(),
            RequestScopes.getOrgIdOrNull()));

    return toResponse(saved);
  }

  // --- 441.2: Record Fee Transfer ---

  @Transactional
  public TrustTransactionResponse recordFeeTransfer(
      UUID trustAccountId, RecordFeeTransferRequest request) {
    moduleGuard.requireModule(MODULE_ID);
    validateAmount(request.amount());
    validateReference(request.reference());

    var account =
        trustAccountRepository
            .findById(trustAccountId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", trustAccountId));

    if (TrustAccountStatus.CLOSED == account.getStatus()) {
      throw new InvalidStateException("Cannot record fee transfer", "Trust account is closed");
    }

    var customer =
        customerRepository
            .findById(request.customerId())
            .orElseThrow(() -> new ResourceNotFoundException("Customer", request.customerId()));

    var invoice =
        invoiceRepository
            .findById(request.invoiceId())
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", request.invoiceId()));

    if (!invoice.getCustomerId().equals(request.customerId())) {
      throw new InvalidStateException(
          "Invalid fee transfer", "Invoice does not belong to the specified customer");
    }

    if (invoice.getStatus() != InvoiceStatus.APPROVED
        && invoice.getStatus() != InvoiceStatus.SENT) {
      throw new InvalidStateException(
          "Invalid fee transfer",
          "Invoice must be in APPROVED or SENT status, current status: " + invoice.getStatus());
    }

    var memberId = RequestScopes.requireMemberId();

    var transaction =
        new TrustTransaction(
            trustAccountId,
            "FEE_TRANSFER",
            request.amount(),
            request.customerId(),
            null,
            null,
            request.reference(),
            null,
            LocalDate.now(),
            "AWAITING_APPROVAL",
            memberId);

    transaction.setInvoiceId(request.invoiceId());

    var saved = transactionRepository.save(transaction);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_fee_transfer.recorded")
            .entityType("trust_transaction")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "amount", request.amount().toString(),
                    "customer_id", request.customerId().toString(),
                    "customer_name", customer.getName(),
                    "invoice_id", request.invoiceId().toString(),
                    "reference", request.reference()))
            .build());

    eventPublisher.publishEvent(
        TrustTransactionApprovalEvent.awaitingApproval(
            saved.getId(),
            trustAccountId,
            "FEE_TRANSFER",
            request.amount(),
            request.customerId(),
            memberId,
            RequestScopes.getTenantIdOrNull(),
            RequestScopes.getOrgIdOrNull()));

    return toResponse(saved);
  }

  // --- 441.3: Record Refund ---

  @Transactional
  public TrustTransactionResponse recordRefund(UUID trustAccountId, RecordRefundRequest request) {
    moduleGuard.requireModule(MODULE_ID);
    validateAmount(request.amount());
    validateReference(request.reference());

    var account =
        trustAccountRepository
            .findById(trustAccountId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", trustAccountId));

    if (TrustAccountStatus.CLOSED == account.getStatus()) {
      throw new InvalidStateException("Cannot record refund", "Trust account is closed");
    }

    var customer =
        customerRepository
            .findById(request.customerId())
            .orElseThrow(() -> new ResourceNotFoundException("Customer", request.customerId()));

    var memberId = RequestScopes.requireMemberId();

    var transaction =
        new TrustTransaction(
            trustAccountId,
            "REFUND",
            request.amount(),
            request.customerId(),
            null,
            null,
            request.reference(),
            request.description(),
            request.transactionDate(),
            "AWAITING_APPROVAL",
            memberId);

    var saved = transactionRepository.save(transaction);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_refund.recorded")
            .entityType("trust_transaction")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "amount", request.amount().toString(),
                    "customer_id", request.customerId().toString(),
                    "customer_name", customer.getName(),
                    "reference", request.reference()))
            .build());

    eventPublisher.publishEvent(
        TrustTransactionApprovalEvent.awaitingApproval(
            saved.getId(),
            trustAccountId,
            "REFUND",
            request.amount(),
            request.customerId(),
            memberId,
            RequestScopes.getTenantIdOrNull(),
            RequestScopes.getOrgIdOrNull()));

    return toResponse(saved);
  }

  // --- 441.4+441.7+441.8: Approve Transaction (Single + Dual Mode) ---

  @Transactional
  public TrustTransactionResponse approveTransaction(UUID transactionId, UUID approverId) {
    moduleGuard.requireModule(MODULE_ID);

    if (!RequestScopes.hasCapability("APPROVE_TRUST_PAYMENT")) {
      throw new ForbiddenException(
          "Missing capability", "Missing required capability: APPROVE_TRUST_PAYMENT");
    }

    // Validate approverId matches the authenticated member
    var authenticatedMemberId = RequestScopes.requireMemberId();
    if (!approverId.equals(authenticatedMemberId)) {
      throw new ForbiddenException(
          "Invalid approver", "Approver ID does not match the authenticated member");
    }

    var transaction =
        transactionRepository
            .findByIdForUpdate(transactionId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustTransaction", transactionId));

    if (!"AWAITING_APPROVAL".equals(transaction.getStatus())) {
      throw new InvalidStateException(
          "Cannot approve transaction",
          "Transaction must be in AWAITING_APPROVAL status to be approved");
    }

    // Load trust account to determine approval mode
    var account =
        trustAccountRepository
            .findById(transaction.getTrustAccountId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException("TrustAccount", transaction.getTrustAccountId()));

    // Determine if dual approval is required (441.7 + 441.8)
    boolean dualRequired =
        account.getRequireDualApproval()
            && (account.getPaymentApprovalThreshold() == null
                || transaction.getAmount().compareTo(account.getPaymentApprovalThreshold()) >= 0);

    if (dualRequired) {
      return approveDualMode(transaction, approverId);
    } else {
      return approveSingleMode(transaction, approverId);
    }
  }

  private TrustTransactionResponse approveSingleMode(
      TrustTransaction transaction, UUID approverId) {
    // Single mode: recorder cannot be the sole approver
    if (approverId.equals(transaction.getRecordedBy())) {
      throw new InvalidStateException(
          "Self-approval not allowed",
          "The transaction recorder cannot be the sole approver. A different member with"
              + " APPROVE_TRUST_PAYMENT capability must approve this transaction.");
    }

    performApprovalCompletion(transaction, approverId, false);
    return toResponse(transaction);
  }

  private TrustTransactionResponse approveDualMode(TrustTransaction transaction, UUID approverId) {
    if (transaction.getApprovedBy() == null) {
      // First approval in dual mode
      // Recorder CAN be the first approver in dual mode (relaxed self-approval)
      transaction.setApprovedBy(approverId);
      transaction.setApprovedAt(Instant.now());
      // Status stays AWAITING_APPROVAL — second approval still needed
      transactionRepository.save(transaction);

      auditService.log(
          AuditEventBuilder.builder()
              .eventType(
                  "trust_" + transaction.getTransactionType().toLowerCase() + ".first_approved")
              .entityType("trust_transaction")
              .entityId(transaction.getId())
              .details(
                  Map.of(
                      "transaction_type", transaction.getTransactionType(),
                      "amount", transaction.getAmount().toString(),
                      "first_approved_by", approverId.toString()))
              .build());

      return toResponse(transaction);
    } else {
      // Second approval in dual mode
      // Must differ from first approver.
      // This also implicitly prevents the recorder from being both approvers:
      // if the recorder was the first approver, this check blocks them from also being
      // the second approver (since approverId would equal approvedBy).
      if (approverId.equals(transaction.getApprovedBy())) {
        throw new InvalidStateException(
            "Duplicate approver", "Second approver must be different from the first approver");
      }

      transaction.setSecondApprovedBy(approverId);
      transaction.setSecondApprovedAt(Instant.now());

      performApprovalCompletion(transaction, approverId, true);
      return toResponse(transaction);
    }
  }

  /**
   * Performs the common approval completion logic: balance check, ledger update, fee transfer
   * invoice integration, status transition, audit, and event publishing.
   */
  private void performApprovalCompletion(
      TrustTransaction transaction, UUID approverId, boolean isDualMode) {
    // Check balance for debit types
    if (DEBIT_TYPES.contains(transaction.getTransactionType())) {
      var customer =
          customerRepository
              .findById(transaction.getCustomerId())
              .orElseThrow(
                  () -> new ResourceNotFoundException("Customer", transaction.getCustomerId()));

      var ledgerCard =
          ledgerCardRepository
              .findByAccountAndCustomerForUpdate(
                  transaction.getTrustAccountId(), transaction.getCustomerId())
              .orElse(null);

      if (ledgerCard == null || ledgerCard.getBalance().compareTo(transaction.getAmount()) < 0) {
        var availableBalance = ledgerCard != null ? ledgerCard.getBalance() : BigDecimal.ZERO;
        throw new InvalidStateException(
            "Insufficient trust balance",
            "Insufficient trust balance for "
                + customer.getName()
                + ". Available: R"
                + availableBalance
                + ", Requested: R"
                + transaction.getAmount());
      }

      // Update ledger based on transaction type
      switch (transaction.getTransactionType()) {
        case "PAYMENT" ->
            ledgerCard.addPayment(transaction.getAmount(), transaction.getTransactionDate());
        case "FEE_TRANSFER" ->
            ledgerCard.addFeeTransfer(transaction.getAmount(), transaction.getTransactionDate());
        case "REFUND" ->
            ledgerCard.debitBalance(transaction.getAmount(), transaction.getTransactionDate());
        default ->
            throw new InvalidStateException(
                "Unknown debit type",
                "Cannot approve transaction of type: " + transaction.getTransactionType());
      }

      ledgerCardRepository.save(ledgerCard);

      // 441.9: Fee transfer invoice integration — mark invoice as PAID on approval.
      // Use fromWebhook=true to skip PSP gateway call: a trust fee transfer is an internal
      // accounting operation, not an external payment through a payment service provider.
      // Pre-check: the webhook path silently accepts already-PAID invoices (idempotent),
      // but trust fee transfers must reject this to prevent double debit from trust balance.
      if ("FEE_TRANSFER".equals(transaction.getTransactionType())
          && transaction.getInvoiceId() != null) {
        var invoice =
            invoiceRepository
                .findById(transaction.getInvoiceId())
                .orElseThrow(
                    () -> new ResourceNotFoundException("Invoice", transaction.getInvoiceId()));
        if (invoice.getStatus() == InvoiceStatus.PAID) {
          throw new InvalidStateException(
              "Invoice already paid",
              "Cannot complete fee transfer — invoice is already in PAID status");
        }
        invoiceService.recordPayment(transaction.getInvoiceId(), transaction.getReference(), true);
      }
    }

    if (!isDualMode) {
      transaction.setApprovedBy(approverId);
      transaction.setApprovedAt(Instant.now());
    }
    transaction.setStatus("APPROVED");
    transactionRepository.save(transaction);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_" + transaction.getTransactionType().toLowerCase() + ".approved")
            .entityType("trust_transaction")
            .entityId(transaction.getId())
            .details(
                Map.of(
                    "transaction_type", transaction.getTransactionType(),
                    "amount", transaction.getAmount().toString(),
                    "approved_by", approverId.toString()))
            .build());

    // Publish approval event for notifications
    eventPublisher.publishEvent(
        TrustTransactionApprovalEvent.approved(
            transaction.getId(),
            transaction.getTrustAccountId(),
            transaction.getTransactionType(),
            transaction.getAmount(),
            transaction.getCustomerId(),
            transaction.getRecordedBy(),
            approverId,
            RequestScopes.getTenantIdOrNull(),
            RequestScopes.getOrgIdOrNull()));
  }

  // --- 441.5: Reject Transaction ---

  @Transactional
  public TrustTransactionResponse rejectTransaction(
      UUID transactionId, UUID rejecterId, String reason) {
    moduleGuard.requireModule(MODULE_ID);

    if (!RequestScopes.hasCapability("APPROVE_TRUST_PAYMENT")) {
      throw new ForbiddenException(
          "Missing capability", "Missing required capability: APPROVE_TRUST_PAYMENT");
    }

    if (reason == null || reason.isBlank()) {
      throw new InvalidStateException("Invalid rejection", "Rejection reason must not be blank");
    }

    var transaction =
        transactionRepository
            .findByIdForUpdate(transactionId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustTransaction", transactionId));

    if (!"AWAITING_APPROVAL".equals(transaction.getStatus())) {
      throw new InvalidStateException(
          "Cannot reject transaction",
          "Transaction must be in AWAITING_APPROVAL status to be rejected");
    }

    transaction.setRejectedBy(rejecterId);
    transaction.setRejectedAt(Instant.now());
    transaction.setRejectionReason(reason);
    transaction.setStatus("REJECTED");
    transactionRepository.save(transaction);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_" + transaction.getTransactionType().toLowerCase() + ".rejected")
            .entityType("trust_transaction")
            .entityId(transaction.getId())
            .details(
                Map.of(
                    "transaction_type", transaction.getTransactionType(),
                    "amount", transaction.getAmount().toString(),
                    "rejected_by", rejecterId.toString(),
                    "reason", reason))
            .build());

    eventPublisher.publishEvent(
        TrustTransactionApprovalEvent.rejected(
            transaction.getId(),
            transaction.getTrustAccountId(),
            transaction.getTransactionType(),
            transaction.getAmount(),
            transaction.getCustomerId(),
            transaction.getRecordedBy(),
            rejecterId,
            RequestScopes.getTenantIdOrNull(),
            RequestScopes.getOrgIdOrNull()));

    return toResponse(transaction);
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

    // Acquire pessimistic write lock to prevent concurrent double-reversal
    var original =
        transactionRepository
            .findByIdForUpdate(transactionId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustTransaction", transactionId));

    // TRANSFER_IN cannot be reversed directly — reverse the paired TRANSFER_OUT instead,
    // which will automatically reverse both legs.
    if ("TRANSFER_IN".equals(original.getTransactionType())) {
      throw new InvalidStateException(
          "Cannot reverse TRANSFER_IN directly",
          "Reverse the paired TRANSFER_OUT instead to undo both legs of the transfer");
    }

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

    // TRANSFER_OUT reversal: reverse both legs (TRANSFER_OUT + paired TRANSFER_IN)
    if ("TRANSFER_OUT".equals(original.getTransactionType())) {
      return reverseTransferOut(original, reason, memberId);
    }

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
    String reversalRef = buildReversalReference(original.getReference());

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

  /**
   * Reverses a TRANSFER_OUT by reversing both legs: the TRANSFER_OUT (credit back to source) and
   * the paired TRANSFER_IN (debit from target). Transfer reversals are internal moves, so both legs
   * are immediate (RECORDED, no approval needed) and cashbook-neutral.
   */
  private TrustTransactionResponse reverseTransferOut(
      TrustTransaction original, String reason, UUID memberId) {
    // Find the paired TRANSFER_IN: same reference, same trust account, TRANSFER_IN type,
    // targeted at the counterparty customer
    var pairedTransferIn =
        transactionRepository
            .findPairedTransfer(
                original.getTrustAccountId(),
                original.getReference(),
                "TRANSFER_IN",
                original.getCounterpartyCustomerId())
            .orElseThrow(
                () ->
                    new InvalidStateException(
                        "Cannot reverse transfer",
                        "Paired TRANSFER_IN not found for reference: " + original.getReference()));

    // Validate paired TRANSFER_IN is not already reversed
    if (pairedTransferIn.getReversedById() != null
        || transactionRepository.existsByReversalOf(pairedTransferIn.getId())) {
      throw new InvalidStateException(
          "Cannot reverse transaction", "Paired TRANSFER_IN has already been reversed");
    }

    String reversalRef = buildReversalReference(original.getReference());

    // Create reversal for the TRANSFER_OUT (credits source back)
    var outReversal =
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
            "RECORDED",
            memberId);
    outReversal.setReversalOf(original.getId());
    var savedOutReversal = transactionRepository.save(outReversal);

    // Create reversal for the TRANSFER_IN (debits target)
    var inReversal =
        new TrustTransaction(
            pairedTransferIn.getTrustAccountId(),
            "REVERSAL",
            pairedTransferIn.getAmount(),
            pairedTransferIn.getCustomerId(),
            pairedTransferIn.getProjectId(),
            pairedTransferIn.getCounterpartyCustomerId(),
            reversalRef,
            reason,
            LocalDate.now(),
            "RECORDED",
            memberId);
    inReversal.setReversalOf(pairedTransferIn.getId());
    var savedInReversal = transactionRepository.save(inReversal);

    // Mark both originals as REVERSED
    original.setReversedById(savedOutReversal.getId());
    original.setStatus("REVERSED");
    transactionRepository.save(original);

    pairedTransferIn.setReversedById(savedInReversal.getId());
    pairedTransferIn.setStatus("REVERSED");
    transactionRepository.save(pairedTransferIn);

    // Update both ledger cards: credit source (add money back), debit target (remove money)
    // Acquire locks in deterministic UUID order to prevent deadlocks
    UUID sourceCustomerId = original.getCustomerId();
    UUID targetCustomerId = pairedTransferIn.getCustomerId();

    UUID firstId =
        sourceCustomerId.compareTo(targetCustomerId) < 0 ? sourceCustomerId : targetCustomerId;
    UUID secondId =
        sourceCustomerId.compareTo(targetCustomerId) < 0 ? targetCustomerId : sourceCustomerId;

    var firstLedger =
        ledgerCardRepository
            .findByAccountAndCustomerForUpdate(original.getTrustAccountId(), firstId)
            .orElse(null);
    var secondLedger =
        ledgerCardRepository
            .findByAccountAndCustomerForUpdate(original.getTrustAccountId(), secondId)
            .orElse(null);

    // Resolve source and target ledger cards
    ClientLedgerCard sourceLedger;
    ClientLedgerCard targetLedger;
    if (firstId.equals(sourceCustomerId)) {
      sourceLedger = firstLedger;
      targetLedger = secondLedger;
    } else {
      sourceLedger = secondLedger;
      targetLedger = firstLedger;
    }

    // Credit source (add back transferred amount)
    if (sourceLedger != null) {
      sourceLedger.creditBalance(original.getAmount(), LocalDate.now());
      ledgerCardRepository.save(sourceLedger);
    }

    // Debit target (remove transferred amount)
    if (targetLedger != null) {
      targetLedger.debitBalance(original.getAmount(), LocalDate.now());
      ledgerCardRepository.save(targetLedger);
    }

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_transaction.reversed")
            .entityType("trust_transaction")
            .entityId(savedOutReversal.getId())
            .details(
                Map.of(
                    "original_transaction_id", original.getId().toString(),
                    "paired_transfer_in_id", pairedTransferIn.getId().toString(),
                    "original_type", original.getTransactionType(),
                    "amount", original.getAmount().toString(),
                    "reversal_status", "RECORDED",
                    "reason", reason))
            .build());

    return toResponse(savedOutReversal);
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

  // --- 442A: Controller-facing query and enrichment methods ---

  @Transactional(readOnly = true)
  public Page<EnrichedTrustTransactionResponse> listTransactions(
      UUID trustAccountId, TransactionFilters filters, Pageable pageable) {
    moduleGuard.requireModule(MODULE_ID);
    trustAccountRepository
        .findById(trustAccountId)
        .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", trustAccountId));

    var jpql =
        new StringBuilder("SELECT t FROM TrustTransaction t WHERE t.trustAccountId = :accountId");
    var countJpql =
        new StringBuilder(
            "SELECT COUNT(t) FROM TrustTransaction t WHERE t.trustAccountId = :accountId");

    var params = new ArrayList<Object[]>();
    params.add(new Object[] {"accountId", trustAccountId});

    if (filters.dateFrom() != null) {
      jpql.append(" AND t.transactionDate >= :dateFrom");
      countJpql.append(" AND t.transactionDate >= :dateFrom");
      params.add(new Object[] {"dateFrom", filters.dateFrom()});
    }
    if (filters.dateTo() != null) {
      jpql.append(" AND t.transactionDate <= :dateTo");
      countJpql.append(" AND t.transactionDate <= :dateTo");
      params.add(new Object[] {"dateTo", filters.dateTo()});
    }
    if (filters.type() != null) {
      jpql.append(" AND t.transactionType = :type");
      countJpql.append(" AND t.transactionType = :type");
      params.add(new Object[] {"type", filters.type()});
    }
    if (filters.status() != null) {
      jpql.append(" AND t.status = :status");
      countJpql.append(" AND t.status = :status");
      params.add(new Object[] {"status", filters.status()});
    }
    if (filters.customerId() != null) {
      jpql.append(" AND t.customerId = :customerId");
      countJpql.append(" AND t.customerId = :customerId");
      params.add(new Object[] {"customerId", filters.customerId()});
    }
    if (filters.projectId() != null) {
      jpql.append(" AND t.projectId = :projectId");
      countJpql.append(" AND t.projectId = :projectId");
      params.add(new Object[] {"projectId", filters.projectId()});
    }

    jpql.append(" ORDER BY t.transactionDate DESC, t.createdAt DESC");

    var query = entityManager.createQuery(jpql.toString(), TrustTransaction.class);
    var countQuery = entityManager.createQuery(countJpql.toString(), Long.class);

    for (var param : params) {
      query.setParameter((String) param[0], param[1]);
      countQuery.setParameter((String) param[0], param[1]);
    }

    query.setFirstResult((int) pageable.getOffset());
    query.setMaxResults(pageable.getPageSize());

    var results = query.getResultList();
    var total = countQuery.getSingleResult();

    var enriched = batchEnrich(results);
    return new PageImpl<>(enriched, pageable, total);
  }

  @Transactional(readOnly = true)
  public EnrichedTrustTransactionResponse getTransaction(UUID accountId, UUID transactionId) {
    moduleGuard.requireModule(MODULE_ID);
    var transaction =
        transactionRepository
            .findById(transactionId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustTransaction", transactionId));
    if (!transaction.getTrustAccountId().equals(accountId)) {
      throw new ResourceNotFoundException("TrustTransaction", transactionId);
    }
    return toEnrichedResponse(transaction);
  }

  @Transactional(readOnly = true)
  public List<EnrichedTrustTransactionResponse> getPendingApprovals(UUID trustAccountId) {
    moduleGuard.requireModule(MODULE_ID);
    trustAccountRepository
        .findById(trustAccountId)
        .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", trustAccountId));

    var transactions =
        transactionRepository.findByStatusAndTrustAccountId("AWAITING_APPROVAL", trustAccountId);
    return batchEnrich(transactions);
  }

  /** Controller-facing deposit — returns enriched response with name resolution. */
  @Transactional
  public EnrichedTrustTransactionResponse recordDepositEnriched(
      UUID trustAccountId, RecordDepositRequest request) {
    var flat = recordDeposit(trustAccountId, request);
    return toEnrichedResponseFromFlat(flat, null);
  }

  /** Controller-facing payment — returns enriched response with name resolution. */
  @Transactional
  public EnrichedTrustTransactionResponse recordPaymentEnriched(
      UUID trustAccountId, RecordPaymentRequest request) {
    var flat = recordPayment(trustAccountId, request);
    return toEnrichedResponseFromFlat(flat, null);
  }

  /** Controller-facing transfer — returns enriched responses with name resolution. */
  @Transactional
  public List<EnrichedTrustTransactionResponse> recordTransferEnriched(
      UUID trustAccountId, RecordTransferRequest request) {
    var flatList = recordTransfer(trustAccountId, request);
    return flatList.stream().map(flat -> toEnrichedResponseFromFlat(flat, null)).toList();
  }

  /** Controller-facing fee transfer — returns enriched response with name resolution. */
  @Transactional
  public EnrichedTrustTransactionResponse recordFeeTransferEnriched(
      UUID trustAccountId, RecordFeeTransferRequest request) {
    var flat = recordFeeTransfer(trustAccountId, request);
    return toEnrichedResponseFromFlat(flat, null);
  }

  /** Controller-facing refund — returns enriched response with name resolution. */
  @Transactional
  public EnrichedTrustTransactionResponse recordRefundEnriched(
      UUID trustAccountId, RecordRefundRequest request) {
    var flat = recordRefund(trustAccountId, request);
    return toEnrichedResponseFromFlat(flat, null);
  }

  /** Controller-facing approve — returns enriched response with client balance. */
  @Transactional
  public EnrichedTrustTransactionResponse approveTransactionEnriched(UUID transactionId) {
    var memberId = RequestScopes.requireMemberId();
    var flat = approveTransaction(transactionId, memberId);
    BigDecimal clientBalance = null;
    if (flat.customerId() != null) {
      clientBalance =
          ledgerCardRepository
              .findByTrustAccountIdAndCustomerId(flat.trustAccountId(), flat.customerId())
              .map(l -> l.getBalance())
              .orElse(null);
    }
    return toEnrichedResponseFromFlat(flat, clientBalance);
  }

  /** Controller-facing reject — returns enriched response. */
  @Transactional
  public EnrichedTrustTransactionResponse rejectTransactionEnriched(
      UUID transactionId, String reason) {
    var memberId = RequestScopes.requireMemberId();
    var flat = rejectTransaction(transactionId, memberId, reason);
    return toEnrichedResponseFromFlat(flat, null);
  }

  /** Controller-facing reverse — returns enriched response. */
  @Transactional
  public EnrichedTrustTransactionResponse reverseTransactionEnriched(
      UUID transactionId, String reason) {
    var flat = reverseTransaction(transactionId, reason);
    return toEnrichedResponseFromFlat(flat, null);
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

  /**
   * Builds a reversal reference from the original reference, truncating to 200-char column limit.
   */
  private static String buildReversalReference(String originalReference) {
    String ref = "REV-" + originalReference;
    if (ref.length() > 200) {
      ref = ref.substring(0, 200);
    }
    return ref;
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

  /**
   * Batch-enriches a list of transactions, resolving all customer names and member names in two
   * queries instead of N*3 individual queries.
   */
  private List<EnrichedTrustTransactionResponse> batchEnrich(List<TrustTransaction> transactions) {
    if (transactions.isEmpty()) {
      return List.of();
    }

    // Collect all distinct customer IDs and member IDs
    Set<UUID> customerIds = new HashSet<>();
    Set<UUID> memberIds = new HashSet<>();
    for (var tx : transactions) {
      if (tx.getCustomerId() != null) customerIds.add(tx.getCustomerId());
      if (tx.getRecordedBy() != null) memberIds.add(tx.getRecordedBy());
      if (tx.getApprovedBy() != null) memberIds.add(tx.getApprovedBy());
    }

    // Batch-fetch customer names
    Map<UUID, String> customerNames =
        customerIds.isEmpty()
            ? Map.of()
            : customerRepository.findByIdIn(customerIds).stream()
                .collect(Collectors.toMap(c -> c.getId(), c -> c.getName(), (a, b) -> a));

    // Batch-fetch member names
    Map<UUID, String> memberNames = memberNameResolver.resolveNames(memberIds);

    return transactions.stream()
        .map(
            entity ->
                new EnrichedTrustTransactionResponse(
                    entity.getId(),
                    entity.getTrustAccountId(),
                    entity.getTransactionType(),
                    entity.getAmount(),
                    entity.getCustomerId(),
                    entity.getCustomerId() != null
                        ? customerNames.get(entity.getCustomerId())
                        : null,
                    entity.getProjectId(),
                    entity.getCounterpartyCustomerId(),
                    entity.getInvoiceId(),
                    entity.getReference(),
                    entity.getDescription(),
                    entity.getTransactionDate(),
                    entity.getStatus(),
                    entity.getApprovedBy(),
                    entity.getApprovedBy() != null ? memberNames.get(entity.getApprovedBy()) : null,
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
                    memberNames.get(entity.getRecordedBy()),
                    entity.getCreatedAt(),
                    null))
        .toList();
  }

  private EnrichedTrustTransactionResponse toEnrichedResponse(TrustTransaction entity) {
    String customerName =
        entity.getCustomerId() != null
            ? customerRepository.findById(entity.getCustomerId()).map(c -> c.getName()).orElse(null)
            : null;
    String recordedByName = memberNameResolver.resolveNameOrNull(entity.getRecordedBy());
    String approvedByName =
        entity.getApprovedBy() != null
            ? memberNameResolver.resolveNameOrNull(entity.getApprovedBy())
            : null;
    return new EnrichedTrustTransactionResponse(
        entity.getId(),
        entity.getTrustAccountId(),
        entity.getTransactionType(),
        entity.getAmount(),
        entity.getCustomerId(),
        customerName,
        entity.getProjectId(),
        entity.getCounterpartyCustomerId(),
        entity.getInvoiceId(),
        entity.getReference(),
        entity.getDescription(),
        entity.getTransactionDate(),
        entity.getStatus(),
        entity.getApprovedBy(),
        approvedByName,
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
        recordedByName,
        entity.getCreatedAt(),
        null);
  }

  private EnrichedTrustTransactionResponse toEnrichedResponseFromFlat(
      TrustTransactionResponse flat, BigDecimal clientBalance) {
    String customerName =
        flat.customerId() != null
            ? customerRepository.findById(flat.customerId()).map(c -> c.getName()).orElse(null)
            : null;
    String recordedByName = memberNameResolver.resolveNameOrNull(flat.recordedBy());
    String approvedByName =
        flat.approvedBy() != null ? memberNameResolver.resolveNameOrNull(flat.approvedBy()) : null;
    return new EnrichedTrustTransactionResponse(
        flat.id(),
        flat.trustAccountId(),
        flat.transactionType(),
        flat.amount(),
        flat.customerId(),
        customerName,
        flat.projectId(),
        flat.counterpartyCustomerId(),
        flat.invoiceId(),
        flat.reference(),
        flat.description(),
        flat.transactionDate(),
        flat.status(),
        flat.approvedBy(),
        approvedByName,
        flat.approvedAt(),
        flat.secondApprovedBy(),
        flat.secondApprovedAt(),
        flat.rejectedBy(),
        flat.rejectedAt(),
        flat.rejectionReason(),
        flat.reversalOf(),
        flat.reversedById(),
        flat.bankStatementLineId(),
        flat.recordedBy(),
        recordedByName,
        flat.createdAt(),
        clientBalance);
  }
}
