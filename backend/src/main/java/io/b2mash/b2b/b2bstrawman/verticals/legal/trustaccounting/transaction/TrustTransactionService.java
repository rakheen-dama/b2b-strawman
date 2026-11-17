package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import io.b2mash.b2b.b2bstrawman.audit.AuditDetails;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.EntityNoun;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceService;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEventRepository;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEventStatus;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentPosting;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.template.VariableFormatter;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustTransactionApprovalEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustTransactionRecordedEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestment;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.BankStatementLineRepository;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrustTransactionService {

  private static final Logger log = LoggerFactory.getLogger(TrustTransactionService.class);

  private static final String MODULE_ID = "trust_accounting";

  /** Trust money is rand: refusal messages use the house en-ZA format (G89-F40). */
  private static final Locale TRUST_LOCALE = VariableFormatter.resolveLocale("ZAR");

  /** ProblemDetail {@code code} of a debit refused by the matter sub-ledger guard (G89-F12). */
  public static final String INSUFFICIENT_MATTER_TRUST_BALANCE =
      "INSUFFICIENT_MATTER_TRUST_BALANCE";

  /** ProblemDetail {@code code} of a client posting refused for want of a matter (G89-F28). */
  public static final String MATTER_REQUIRED = "MATTER_REQUIRED";

  /** ProblemDetail {@code code} of a client posting on a matter not linked to it (G89-F28). */
  public static final String MATTER_NOT_LINKED_TO_CLIENT = "MATTER_NOT_LINKED_TO_CLIENT";

  /** The refusal detail for a client trust posting without a matter (G89-F28, D36). */
  static final String MATTER_REQUIRED_DETAIL = "A matter is required for client trust transactions";

  private final TrustTransactionRepository transactionRepository;
  private final TrustPostingService postingService;
  private final TrustLedgerQueryRepository ledgerQueries;
  private final TrustInvestmentRepository trustInvestmentRepository;
  private final TrustAccountRepository trustAccountRepository;
  private final CustomerRepository customerRepository;
  private final CustomerProjectRepository customerProjectRepository;
  private final ProjectRepository projectRepository;
  private final InvoiceRepository invoiceRepository;
  private final InvoiceLineRepository invoiceLineRepository;
  private final InvoiceService invoiceService;
  private final PaymentEventRepository paymentEventRepository;
  private final VerticalModuleGuard moduleGuard;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final BankStatementLineRepository bankStatementLineRepository;
  private final Clock clock;

  public TrustTransactionService(
      TrustTransactionRepository transactionRepository,
      TrustPostingService postingService,
      TrustLedgerQueryRepository ledgerQueries,
      TrustInvestmentRepository trustInvestmentRepository,
      TrustAccountRepository trustAccountRepository,
      CustomerRepository customerRepository,
      CustomerProjectRepository customerProjectRepository,
      ProjectRepository projectRepository,
      InvoiceRepository invoiceRepository,
      InvoiceLineRepository invoiceLineRepository,
      InvoiceService invoiceService,
      PaymentEventRepository paymentEventRepository,
      VerticalModuleGuard moduleGuard,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      BankStatementLineRepository bankStatementLineRepository,
      Clock clock) {
    this.transactionRepository = transactionRepository;
    this.postingService = postingService;
    this.ledgerQueries = ledgerQueries;
    this.trustInvestmentRepository = trustInvestmentRepository;
    this.trustAccountRepository = trustAccountRepository;
    this.customerRepository = customerRepository;
    this.customerProjectRepository = customerProjectRepository;
    this.projectRepository = projectRepository;
    this.invoiceRepository = invoiceRepository;
    this.invoiceLineRepository = invoiceLineRepository;
    this.invoiceService = invoiceService;
    this.paymentEventRepository = paymentEventRepository;
    this.moduleGuard = moduleGuard;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.bankStatementLineRepository = bankStatementLineRepository;
    this.clock = clock;
  }

  // --- DTO Records ---

  public record RecordDepositRequest(
      UUID clientId,
      UUID matterId,
      BigDecimal amount,
      String reference,
      String description,
      LocalDate transactionDate) {}

  /**
   * An inter-client transfer: TRANSFER_OUT debits the source client on {@code sourceMatterId} (a
   * matter of the source client), TRANSFER_IN credits the target client on {@code targetMatterId}
   * (a matter of the target client; G89-F28, D39).
   */
  public record RecordTransferRequest(
      UUID sourceClientId,
      UUID sourceMatterId,
      UUID targetClientId,
      UUID targetMatterId,
      BigDecimal amount,
      String reference,
      String description,
      LocalDate transactionDate) {}

  public record RecordPaymentRequest(
      UUID clientId,
      UUID matterId,
      BigDecimal amount,
      String reference,
      String description,
      LocalDate transactionDate) {}

  /**
   * A fee transfer from a client's trust money to the firm against a fee note. {@code
   * transactionDate} is optional (ruling #6, §16.4.1): the date the bank shows the transfer; null =
   * today ({@code LocalDate.now(clock)}); never in the future.
   */
  public record RecordFeeTransferRequest(
      UUID clientId,
      UUID invoiceId,
      BigDecimal amount,
      String reference,
      LocalDate transactionDate) {

    /** Undated: the transfer is dated today. */
    public RecordFeeTransferRequest(
        UUID clientId, UUID invoiceId, BigDecimal amount, String reference) {
      this(clientId, invoiceId, amount, reference, null);
    }
  }

  public record RecordRefundRequest(
      UUID clientId,
      UUID matterId,
      BigDecimal amount,
      String reference,
      String description,
      LocalDate transactionDate) {}

  /**
   * One trust transaction (§16.4.1). {@code typeLabel} is the registry's firm label; {@code
   * direction} the way the row moves its own ledger ({@link TrustTransactionDirection#ofRow});
   * {@code reversalPending} is true while a contra of this row awaits approval — the one blocking
   * state the other fields do not show (a posted contra sets {@code reversedById}).
   */
  public record TrustTransactionResponse(
      UUID id,
      UUID trustAccountId,
      String transactionType,
      String typeLabel,
      TrustTransactionDirection direction,
      TrustLedger ledger,
      BigDecimal amount,
      UUID clientId,
      UUID matterId,
      UUID creditorAccountId,
      UUID investmentId,
      UUID counterpartyClientId,
      UUID invoiceId,
      String reference,
      String description,
      LocalDate transactionDate,
      String status,
      Instant postedAt,
      UUID approvedBy,
      Instant approvedAt,
      UUID secondApprovedBy,
      Instant secondApprovedAt,
      UUID rejectedBy,
      Instant rejectedAt,
      String rejectionReason,
      UUID reversalOf,
      UUID reversedById,
      boolean reversalPending,
      UUID bankStatementLineId,
      UUID recordedBy,
      Instant createdAt,
      // ADR-361: server-computed on the client-ledger /history endpoint only; null on every other
      // producer and on non-posted / REVERSAL rows.
      BigDecimal runningBalance,
      // 647A (ADR-381): BANK_CHARGE only — whether the charge is on the Fidelity Fund's
      // recoverable list (the firm's assertion; Kazi does not check it), and the VAT in the amount;
      // null on every other row (chk_trust_txn_charge).
      Boolean chargeRecoverable,
      BigDecimal chargeVatAmount) {}

  /**
   * One registry entry as data (§16.4.1) — feeds every type filter. {@code label} is the firm
   * label; {@code ledger} and {@code origin} are null for REVERSAL (its original's). {@code
   * requiresApproval} is true for maker-checker types ({@code Approval.REQUIRED}); a {@code
   * WITH_PAIR} row follows its pair and is never recorded alone.
   */
  public record TrustTransactionTypeResponse(
      String code,
      String label,
      TrustTransactionDirection direction,
      TrustLedger ledger,
      TrustTransactionType.Origin origin,
      boolean requiresApproval,
      TrustTransactionType.Reversal reversal,
      boolean clientVisible) {}

  public record RejectRequest(@NotBlank String reason) {}

  /**
   * A reversal (§16.3.11). {@code reversalDate} is optional: the date the bank shows the reversal,
   * from the original's date to today; null means today ({@code LocalDate.now(clock)}).
   */
  public record ReverseRequest(@NotBlank String reason, LocalDate reversalDate) {}

  public record CashbookBalanceResponse(BigDecimal balance) {}

  // --- Service Methods ---

  /**
   * The transaction type registry as data, in declaration order (the filter order) — no screen
   * hard-codes a type list (§16.4.1).
   */
  @Transactional(readOnly = true)
  public List<TrustTransactionTypeResponse> listTransactionTypes() {
    moduleGuard.requireModule(MODULE_ID);
    return Arrays.stream(TrustTransactionType.values())
        .map(
            t ->
                new TrustTransactionTypeResponse(
                    t.name(),
                    t.label(),
                    t.direction(),
                    t.ledger(),
                    t.origin(),
                    t.approval() == TrustTransactionType.Approval.REQUIRED,
                    t.reversal(),
                    t.clientVisible()))
        .toList();
  }

  /**
   * Lists a trust account's transactions, newest first. All filters are optional; with none set the
   * result equals the historical unfiltered list. {@code matterId} narrows to the matter's
   * transactions ({@code trust_transactions.project_id}); {@code ledger} to one creditor ledger;
   * {@code excludeLinked} hides transactions already linked to a legal disbursement (Phase 88 Epic
   * 621A — trust-link picker).
   */
  @Transactional(readOnly = true)
  public Page<TrustTransactionResponse> listTransactions(
      UUID trustAccountId,
      UUID matterId,
      String transactionType,
      String status,
      TrustLedger ledger,
      boolean excludeLinked,
      Pageable pageable) {
    moduleGuard.requireModule(MODULE_ID);

    trustAccountRepository
        .findById(trustAccountId)
        .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", trustAccountId));

    var page =
        transactionRepository.findFiltered(
            trustAccountId, matterId, transactionType, status, ledger, excludeLinked, pageable);
    return new PageImpl<>(
        toResponses(page.getContent()), page.getPageable(), page.getTotalElements());
  }

  @Transactional(readOnly = true)
  public TrustTransactionResponse getTransactionById(UUID accountId, UUID transactionId) {
    moduleGuard.requireModule(MODULE_ID);

    var transaction =
        transactionRepository
            .findById(transactionId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustTransaction", transactionId));

    if (!transaction.getTrustAccountId().equals(accountId)) {
      throw new ResourceNotFoundException("TrustTransaction", transactionId);
    }

    return toResponse(transaction);
  }

  @Transactional(readOnly = true)
  public List<TrustTransactionResponse> getPendingApprovals(UUID trustAccountId) {
    moduleGuard.requireModule(MODULE_ID);

    trustAccountRepository
        .findById(trustAccountId)
        .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", trustAccountId));

    return toResponses(
        transactionRepository.findByStatusAndTrustAccountId("AWAITING_APPROVAL", trustAccountId));
  }

  @Transactional
  public TrustTransactionResponse recordDeposit(UUID trustAccountId, RecordDepositRequest request) {
    return postDeposit(trustAccountId, request, true);
  }

  /**
   * Credits a withdrawn trust investment back to the client: the deposit carries the matter the
   * placement payment ({@code placementTransactionId}) debited, so the money returns to the matter
   * it left (G89-F28). The matter is derived, not chosen, so it is not re-validated; a placement
   * recorded before G89-F28 has no matter and its withdrawal mirrors it client-level (D6: no
   * backfill of legacy rows).
   *
   * <p>PR #1757 review: the withdrawal returns money the placement took, so the placement payment
   * must be completed (APPROVED). While it awaits approval, once rejected, or once reversed, the
   * client was never (or is no longer) debited and a withdrawal would create trust money.
   */
  @Transactional
  public TrustTransactionResponse recordInvestmentWithdrawal(
      UUID trustAccountId, UUID placementTransactionId, RecordDepositRequest request) {
    // Checked under the placement row lock (the same lock reverseTransaction takes first), so a
    // concurrent reversal of the placement either commits before this check or waits for it.
    var placement =
        placementTransactionId == null
            ? null
            : transactionRepository.findByIdForUpdate(placementTransactionId).orElse(null);
    if (placement == null || !"APPROVED".equals(placement.getStatus())) {
      throw new InvalidStateException(
          "Cannot withdraw investment",
          "The investment's placement payment must be approved before the investment can be"
              + " withdrawn (placement payment status: "
              + (placement == null ? "missing" : placement.getStatus())
              + ")");
    }
    UUID placementMatterId = placement.getProjectId();
    return postDeposit(
        trustAccountId,
        new RecordDepositRequest(
            request.clientId(),
            placementMatterId,
            request.amount(),
            request.reference(),
            request.description(),
            request.transactionDate()),
        false);
  }

  /**
   * Locks an investment's placement PAYMENT row — the first lock of both the withdrawal and a
   * reversal of the placement (PR #1757 security review); the investment row is locked second.
   */
  @Transactional
  public void lockInvestmentPlacement(UUID placementTransactionId) {
    if (placementTransactionId != null) {
      transactionRepository.findByIdForUpdate(placementTransactionId);
    }
  }

  private TrustTransactionResponse postDeposit(
      UUID trustAccountId, RecordDepositRequest request, boolean requireMatter) {
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
            .findById(request.clientId())
            .orElseThrow(
                () -> new ResourceNotFoundException(EntityNoun.CLIENT, request.clientId()));

    if (requireMatter) {
      requireClientMatter(customer, request.matterId());
    }

    var memberId = RequestScopes.requireMemberId();

    var transaction =
        new TrustTransaction(
            trustAccountId,
            "DEPOSIT",
            request.amount(),
            request.clientId(),
            request.matterId(),
            null,
            request.reference(),
            request.description(),
            request.transactionDate(),
            "RECORDED",
            memberId);

    // Serialise with every other posting for this client (ADR-231 lock kept, ADR-378): ensure the
    // lock anchor exists, lock it, then post. The balance is derived from the posted row.
    postingService.lockClient(trustAccountId, request.clientId());
    var saved = postingService.record(transaction);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_deposit.recorded")
            .entityType("trust_transaction")
            .entityId(saved.getId())
            .details(
                AuditDetails.matterScoped(
                    saved.getProjectId(),
                    request.clientId(),
                    Map.of(
                        "amount", request.amount().toString(),
                        "customer_name", customer.getName(),
                        "reference", request.reference())))
            .build());

    // Publish a RECORDED-path event so downstream projections (notably the portal trust-ledger
    // read-model) sync the deposit. TrustTransactionApprovalEvent is NOT emitted here because the
    // deposit bypasses the awaiting-approval flow — the portal sync listener only fires on
    // "trust_transaction.approved", so without this event the portal /trust view stays empty
    // even though firm-side tenant_*.trust_transactions has the row (GAP-L-52).
    eventPublisher.publishEvent(
        TrustTransactionRecordedEvent.recorded(
            saved.getId(),
            trustAccountId,
            saved.getProjectId(),
            "DEPOSIT",
            request.amount(),
            saved.getTransactionDate(),
            request.clientId(),
            memberId,
            RequestScopes.getTenantIdOrNull(),
            RequestScopes.getOrgIdOrNull()));

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

    if (request.sourceClientId().equals(request.targetClientId())) {
      throw new InvalidStateException(
          "Invalid transfer", "Source and target client cannot be the same");
    }

    var sourceCustomer =
        customerRepository
            .findById(request.sourceClientId())
            .orElseThrow(
                () -> new ResourceNotFoundException(EntityNoun.CLIENT, request.sourceClientId()));

    var targetCustomer =
        customerRepository
            .findById(request.targetClientId())
            .orElseThrow(
                () -> new ResourceNotFoundException(EntityNoun.CLIENT, request.targetClientId()));

    // G89-F28 / D39: each leg sits on its own client's matter, both checked before either is
    // written — TRANSFER_OUT on the source's matter, TRANSFER_IN on the target's.
    requireClientMatter(sourceCustomer, request.sourceMatterId());
    requireClientMatter(targetCustomer, request.targetMatterId());

    var memberId = RequestScopes.requireMemberId();

    // Determine lock order: ascending UUID to prevent deadlocks
    UUID firstId =
        request.sourceClientId().compareTo(request.targetClientId()) < 0
            ? request.sourceClientId()
            : request.targetClientId();
    UUID secondId =
        request.sourceClientId().compareTo(request.targetClientId()) < 0
            ? request.targetClientId()
            : request.sourceClientId();

    // Acquire the two clients' lock anchors in deterministic order (ADR-231, ADR-378)
    postingService.lockClient(trustAccountId, firstId);
    postingService.lockClient(trustAccountId, secondId);

    // Source must have a sufficient derived balance (a client with no posted row has R0,00)
    var sourceAvailable =
        ledgerQueries.clientBalance(trustAccountId, request.sourceClientId(), null);
    if (sourceAvailable.compareTo(request.amount()) < 0) {
      throw new InvalidStateException(
          "Insufficient trust balance",
          "Insufficient trust balance for "
              + sourceCustomer.getName()
              + ". Available: "
              + rand(sourceAvailable)
              + ", Requested: "
              + rand(request.amount()));
    }

    // A transfer moves the source client's money on its matter (G89-F12)
    requireMatterCellCovers(
        trustAccountId,
        request.sourceClientId(),
        sourceCustomer.getName(),
        request.sourceMatterId(),
        request.amount());

    // Create paired transactions
    var transferOut =
        new TrustTransaction(
            trustAccountId,
            "TRANSFER_OUT",
            request.amount(),
            request.sourceClientId(),
            request.sourceMatterId(),
            request.targetClientId(),
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
            request.targetClientId(),
            request.targetMatterId(),
            request.sourceClientId(),
            request.reference(),
            request.description(),
            request.transactionDate(),
            "RECORDED",
            memberId);

    var savedOut = postingService.record(transferOut);
    var savedIn = postingService.record(transferIn);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_transfer.recorded")
            .entityType("trust_transaction")
            .entityId(savedOut.getId())
            .details(
                // R-17: customer_id = the SOURCE client (the TRANSFER_OUT leg's owner) so the
                // portal Firm trail never shows the row to every client on a shared matter;
                // source/target keys below still carry both sides.
                AuditDetails.matterScoped(
                    savedOut.getProjectId(),
                    savedOut.getCustomerId(),
                    Map.of(
                        "amount", request.amount().toString(),
                        "source_customer_id", request.sourceClientId().toString(),
                        "target_customer_id", request.targetClientId().toString(),
                        "reference", request.reference())))
            .build());

    // Publish one RECORDED-path event per leg so the portal read-model recomputes BOTH the
    // source (TRANSFER_OUT / source customer) and target (TRANSFER_IN / target customer) matter
    // views. Without both events only one side's portal balance would refresh (GAP-L-52).
    String tenantId = RequestScopes.getTenantIdOrNull();
    String orgId = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        TrustTransactionRecordedEvent.recorded(
            savedOut.getId(),
            trustAccountId,
            savedOut.getProjectId(),
            "TRANSFER_OUT",
            request.amount(),
            savedOut.getTransactionDate(),
            request.sourceClientId(),
            memberId,
            tenantId,
            orgId));
    eventPublisher.publishEvent(
        TrustTransactionRecordedEvent.recorded(
            savedIn.getId(),
            trustAccountId,
            savedIn.getProjectId(),
            "TRANSFER_IN",
            request.amount(),
            savedIn.getTransactionDate(),
            request.targetClientId(),
            memberId,
            tenantId,
            orgId));

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
            .findById(request.clientId())
            .orElseThrow(
                () -> new ResourceNotFoundException(EntityNoun.CLIENT, request.clientId()));

    requireClientMatter(customer, request.matterId());

    var memberId = RequestScopes.requireMemberId();

    var transaction =
        new TrustTransaction(
            trustAccountId,
            "PAYMENT",
            request.amount(),
            request.clientId(),
            request.matterId(),
            null,
            request.reference(),
            request.description(),
            request.transactionDate(),
            "AWAITING_APPROVAL",
            memberId);

    var saved = postingService.recordAwaitingApproval(transaction);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_payment.recorded")
            .entityType("trust_transaction")
            .entityId(saved.getId())
            .details(
                AuditDetails.matterScoped(
                    saved.getProjectId(),
                    request.clientId(),
                    Map.of(
                        "amount", request.amount().toString(),
                        "customer_name", customer.getName(),
                        "reference", request.reference())))
            .build());

    eventPublisher.publishEvent(
        TrustTransactionApprovalEvent.awaitingApproval(
            saved.getId(),
            trustAccountId,
            saved.getProjectId(),
            "PAYMENT",
            request.amount(),
            saved.getTransactionDate(),
            request.clientId(),
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
    var transactionDate = resolveFeeTransferDate(request.transactionDate());

    var account =
        trustAccountRepository
            .findById(trustAccountId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", trustAccountId));

    if (TrustAccountStatus.CLOSED == account.getStatus()) {
      throw new InvalidStateException("Cannot record fee transfer", "Trust account is closed");
    }

    var customer =
        customerRepository
            .findById(request.clientId())
            .orElseThrow(
                () -> new ResourceNotFoundException(EntityNoun.CLIENT, request.clientId()));

    var invoice =
        invoiceRepository
            .findById(request.invoiceId())
            .orElseThrow(
                () -> new ResourceNotFoundException(EntityNoun.FEE_NOTE, request.invoiceId()));

    if (!invoice.getCustomerId().equals(request.clientId())) {
      throw new InvalidStateException(
          "Invalid fee transfer", "Fee note does not belong to the specified client");
    }

    // A remainder transfer can be recorded against a part-paid fee note (Phase 88 U15, ADR-347).
    if (invoice.getStatus() != InvoiceStatus.APPROVED
        && invoice.getStatus() != InvoiceStatus.SENT
        && invoice.getStatus() != InvoiceStatus.PARTIALLY_PAID) {
      throw new InvalidStateException(
          "Invalid fee transfer",
          "Fee note must be in APPROVED, SENT or PARTIALLY_PAID status, current status: "
              + invoice.getStatus());
    }

    var memberId = RequestScopes.requireMemberId();

    // GAP-L-69: infer projectId from the invoice's lines so the resulting trust transaction
    // carries the matter binding required by TrustBalanceZeroGate's per-matter calculation.
    // Falls through to null when the invoice spans multiple matters (rare for fee notes) —
    // a future story can decide on multi-matter splits.
    var distinctProjectIds =
        invoiceLineRepository.findDistinctProjectIdsByInvoiceId(invoice.getId());
    UUID inferredProjectId = distinctProjectIds.size() == 1 ? distinctProjectIds.get(0) : null;
    // G89-F28: a fee transfer is a client posting like any other and must land on a matter of the
    // client. The matter comes from the fee note: refused when the fee note has no single matter,
    // and (PR #1757 review) when that matter is no longer linked to the client.
    if (inferredProjectId == null) {
      throw matterRequired(
          "Fee note "
              + (invoice.getInvoiceNumber() != null
                  ? invoice.getInvoiceNumber()
                  : invoice.getId().toString())
              + " does not belong to a single matter, so the transfer has no matter to post to.");
    }
    requireClientMatter(customer, inferredProjectId);

    var transaction =
        new TrustTransaction(
            trustAccountId,
            "FEE_TRANSFER",
            request.amount(),
            request.clientId(),
            inferredProjectId,
            null,
            request.reference(),
            null,
            transactionDate,
            "AWAITING_APPROVAL",
            memberId);

    transaction.setInvoiceId(request.invoiceId());

    var saved = postingService.recordAwaitingApproval(transaction);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_fee_transfer.recorded")
            .entityType("trust_transaction")
            .entityId(saved.getId())
            .details(
                AuditDetails.matterScoped(
                    inferredProjectId,
                    request.clientId(),
                    Map.of(
                        "amount", request.amount().toString(),
                        "customer_name", customer.getName(),
                        "invoice_id", request.invoiceId().toString(),
                        "reference", request.reference())))
            .build());

    eventPublisher.publishEvent(
        TrustTransactionApprovalEvent.awaitingApproval(
            saved.getId(),
            trustAccountId,
            saved.getProjectId(),
            "FEE_TRANSFER",
            request.amount(),
            saved.getTransactionDate(),
            request.clientId(),
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
            .findById(request.clientId())
            .orElseThrow(
                () -> new ResourceNotFoundException(EntityNoun.CLIENT, request.clientId()));

    requireClientMatter(customer, request.matterId());

    var memberId = RequestScopes.requireMemberId();

    var transaction =
        new TrustTransaction(
            trustAccountId,
            "REFUND",
            request.amount(),
            request.clientId(),
            request.matterId(),
            null,
            request.reference(),
            request.description(),
            request.transactionDate(),
            "AWAITING_APPROVAL",
            memberId);

    var saved = postingService.recordAwaitingApproval(transaction);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_refund.recorded")
            .entityType("trust_transaction")
            .entityId(saved.getId())
            .details(
                AuditDetails.matterScoped(
                    saved.getProjectId(),
                    request.clientId(),
                    Map.of(
                        "amount", request.amount().toString(),
                        "customer_name", customer.getName(),
                        "reference", request.reference())))
            .build());

    eventPublisher.publishEvent(
        TrustTransactionApprovalEvent.awaitingApproval(
            saved.getId(),
            trustAccountId,
            saved.getProjectId(),
            "REFUND",
            request.amount(),
            saved.getTransactionDate(),
            request.clientId(),
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
      // Status stays AWAITING_APPROVAL — second approval still needed
      postingService.recordFirstApproval(transaction, approverId);

      auditService.log(
          AuditEventBuilder.builder()
              .eventType(
                  "trust_" + transaction.getTransactionType().toLowerCase() + ".first_approved")
              .entityType("trust_transaction")
              .entityId(transaction.getId())
              .details(
                  AuditDetails.matterScoped(
                      transaction.getProjectId(),
                      transaction.getCustomerId(),
                      Map.of(
                          "transaction_type", transaction.getTransactionType(),
                          "amount", transaction.getAmount().toString(),
                          "first_approved_by", approverId.toString())))
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
    // A credit contra awaiting approval (a deposit reversal, NEEDS_APPROVAL — §16.3.11, the H-a
    // fix) posts the negation of its original now; the client — and the matter cell — must still
    // cover it. Lock order: the pending contra row (locked by the caller), the original row, then
    // the client's lock anchor.
    var type = TrustTransactionType.of(transaction.getTransactionType());
    TrustTransaction original = null;
    if (type == TrustTransactionType.REVERSAL) {
      original =
          transactionRepository
              .findByIdForUpdate(transaction.getReversalOf())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "TrustTransaction", transaction.getReversalOf()));
      if (original.getReversedById() != null
          || !("RECORDED".equals(original.getStatus())
              || "APPROVED".equals(original.getStatus()))) {
        throw new InvalidStateException(
            "Cannot approve transaction",
            "The transaction this reversal undoes is no longer posted or has already been"
                + " reversed");
      }
      if (TrustPostingService.contraOf(original).ledgerEffect().signum() < 0
          && transaction.getCustomerId() != null) {
        requireClientCovers(transaction);
      }
    } else if (type.approval() == TrustTransactionType.Approval.REQUIRED) {
      // The types that post on approval (PAYMENT, FEE_TRANSFER, REFUND) take money off the client.
      requireClientCovers(transaction);

      // 441.9 / Phase 88 U15 (ADR-347, N-5): a fee transfer posts its real amount through the one
      // payment entry point — PARTIALLY_PAID when it leaves a balance, PAID when it clears it, and
      // (ADR-347 A4) capped at PAID with an invoice.payment_excess audit when it exceeds the
      // balance due. postPayment writes the payment_events row (provider trust_fee_transfer, method
      // TRUST_TRANSFER, destination TRUST, reference = the trust reference), which the reversal
      // cascade in reverseTransaction matches on. No PSP is involved: a trust fee transfer is an
      // internal accounting operation. A PAID fee note is refused up front so trust is never
      // debited for a settled fee note.
      if ("FEE_TRANSFER".equals(transaction.getTransactionType())
          && transaction.getInvoiceId() != null) {
        var invoice =
            invoiceRepository
                .findByIdForUpdate(transaction.getInvoiceId())
                .orElseThrow(
                    () ->
                        new ResourceNotFoundException(
                            EntityNoun.FEE_NOTE, transaction.getInvoiceId()));
        if (invoice.getStatus() == InvoiceStatus.PAID) {
          throw new InvalidStateException(
              "Fee note already paid",
              "Cannot complete fee transfer — fee note is already in PAID status");
        }
        invoiceService.postPayment(
            transaction.getInvoiceId(),
            PaymentPosting.trustTransfer(transaction.getAmount(), transaction.getReference()));
      }
    }

    if (!isDualMode) {
      transaction.setApprovedBy(approverId);
      transaction.setApprovedAt(Instant.now());
    }
    // APPROVED and posted now (TrustPostingService, ADR-378): a REQUIRED row takes its registry
    // effects; a credit contra takes the negation of its original's, and the original is marked
    // REVERSED (it keeps its own effects — as-at history is unchanged).
    if (original != null) {
      postingService.postReversalOnApproval(transaction, original, approverId);
    } else {
      postingService.postOnApproval(transaction, approverId);
    }

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_" + transaction.getTransactionType().toLowerCase() + ".approved")
            .entityType("trust_transaction")
            .entityId(transaction.getId())
            .details(
                AuditDetails.matterScoped(
                    transaction.getProjectId(),
                    transaction.getCustomerId(),
                    Map.of(
                        "transaction_type", transaction.getTransactionType(),
                        "amount", transaction.getAmount().toString(),
                        "approved_by", approverId.toString())))
            .build());

    // Publish approval event for notifications
    eventPublisher.publishEvent(
        TrustTransactionApprovalEvent.approved(
            transaction.getId(),
            transaction.getTrustAccountId(),
            transaction.getProjectId(),
            transaction.getTransactionType(),
            transaction.getAmount(),
            transaction.getTransactionDate(),
            transaction.getCustomerId(),
            transaction.getRecordedBy(),
            approverId,
            RequestScopes.getTenantIdOrNull(),
            RequestScopes.getOrgIdOrNull()));

    // A contra posts now: re-project the portal at once (§16.3.11; idempotent with the approval
    // event's re-projection — the recorded-event email filter ignores REVERSAL).
    if (original != null) {
      publishContraRecorded(transaction, approverId);
    }
  }

  /**
   * The client — and, for a mattered row, the matter cell — must cover {@code transaction}'s
   * amount, which is about to leave the client's ledger. Takes the client's lock anchor first, so
   * the derived balances read here cannot race with another posting for the client; the row is
   * still AWAITING_APPROVAL, so neither sum includes it.
   */
  private void requireClientCovers(TrustTransaction transaction) {
    var customer =
        customerRepository
            .findById(transaction.getCustomerId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(EntityNoun.CLIENT, transaction.getCustomerId()));

    // Serialise with the client's other postings, then read the derived balance (ADR-378)
    postingService.lockClient(transaction.getTrustAccountId(), transaction.getCustomerId());
    var availableBalance =
        ledgerQueries.clientBalance(
            transaction.getTrustAccountId(), transaction.getCustomerId(), null);

    if (availableBalance.compareTo(transaction.getAmount()) < 0) {
      throw new InvalidStateException(
          "Insufficient trust balance",
          "Insufficient trust balance for "
              + customer.getName()
              + ". Available: "
              + rand(availableBalance)
              + ", Requested: "
              + rand(transaction.getAmount()));
    }

    // G89-F12: a debit on a matter (the fee transfer's matter is inferred from its fee note at
    // record time) must also be covered by this client's money on that matter. The anchor lock
    // above serialises the client's postings; this transaction is still AWAITING_APPROVAL, so the
    // cell sum excludes it.
    requireMatterCellCovers(
        transaction.getTrustAccountId(),
        transaction.getCustomerId(),
        customer.getName(),
        transaction.getProjectId(),
        transaction.getAmount());
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

    postingService.reject(transaction, rejecterId, reason);

    // G89-F87 (D41): an investment is created ACTIVE while its placement PAYMENT awaits approval.
    // A rejected placement never moved the money, so the investment is CANCELLED (terminal) in the
    // same transaction — otherwise it stays ACTIVE on the s86 register, in getMaturing and in the
    // Section 35 data pack with nothing behind it. Lock order as withdrawInvestment and
    // reverseTransaction: the placement row (locked above), then the investment row.
    if ("PAYMENT".equals(transaction.getTransactionType())) {
      trustInvestmentRepository
          .findByPlacementTransactionIdForUpdate(transaction.getId())
          .filter(
              investment ->
                  "ACTIVE".equals(investment.getStatus())
                      || "MATURED".equals(investment.getStatus()))
          .ifPresent(
              investment -> {
                investment.setStatus("CANCELLED");
                trustInvestmentRepository.save(investment);
                auditService.log(
                    AuditEventBuilder.builder()
                        .eventType("trust_investment.cancelled")
                        .entityType("trust_investment")
                        .entityId(investment.getId())
                        .details(
                            AuditDetails.matterScoped(
                                transaction.getProjectId(),
                                transaction.getCustomerId(),
                                Map.of(
                                    "placement_transaction_id",
                                    transaction.getId().toString(),
                                    "reason",
                                    reason)))
                        .build());
              });
    }

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_" + transaction.getTransactionType().toLowerCase() + ".rejected")
            .entityType("trust_transaction")
            .entityId(transaction.getId())
            .details(
                AuditDetails.matterScoped(
                    transaction.getProjectId(),
                    transaction.getCustomerId(),
                    Map.of(
                        "transaction_type", transaction.getTransactionType(),
                        "amount", transaction.getAmount().toString(),
                        "rejected_by", rejecterId.toString(),
                        "reason", reason)))
            .build());

    eventPublisher.publishEvent(
        TrustTransactionApprovalEvent.rejected(
            transaction.getId(),
            transaction.getTrustAccountId(),
            transaction.getProjectId(),
            transaction.getTransactionType(),
            transaction.getAmount(),
            transaction.getTransactionDate(),
            transaction.getCustomerId(),
            transaction.getRecordedBy(),
            rejecterId,
            RequestScopes.getTenantIdOrNull(),
            RequestScopes.getOrgIdOrNull()));

    return toResponse(transaction);
  }

  // --- 440.9 / 649B: Transaction Reversal (contra entry, §16.3.11, ADR-378) ---

  /**
   * Reverses a posted row ({@code RECORDED} or {@code APPROVED} — founder ruling #1, H-d) by its
   * type's registry rule: IMMEDIATE posts the contra at once; NEEDS_APPROVAL saves it awaiting
   * approval (it posts on approval, H-a); PAIR reverses both legs of a transfer; VIA_PAIR, OWNER
   * and NONE are refused. The contra is dated {@code request.reversalDate()} (default today; from
   * the original's date to today); the original keeps its effects and date, so every earlier as-at
   * balance is unchanged.
   */
  @Transactional
  public TrustTransactionResponse reverseTransaction(UUID transactionId, ReverseRequest request) {
    moduleGuard.requireModule(MODULE_ID);

    // Validate reason is provided (service callers bypass the controller's @Valid)
    String reason = request == null ? null : request.reason();
    if (reason == null || reason.isBlank()) {
      throw new InvalidStateException("Invalid reversal", "Reversal reason is required");
    }

    // Acquire pessimistic write lock to prevent concurrent double-reversal
    var original =
        transactionRepository
            .findByIdForUpdate(transactionId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustTransaction", transactionId));

    // A TRANSFER_IN (rule VIA_PAIR) cannot be reversed directly — reverse the paired TRANSFER_OUT
    // instead, which reverses both legs.
    var reversalRule = TrustTransactionType.of(original.getTransactionType()).reversal();
    if (reversalRule == TrustTransactionType.Reversal.VIA_PAIR) {
      throw new InvalidStateException(
          "Cannot reverse TRANSFER_IN directly",
          "Reverse the paired TRANSFER_OUT instead to undo both legs of the transfer");
    }

    requireReversible(original);

    requireNotAPlacementPayment(original);

    var memberId = RequestScopes.requireMemberId();

    // The original type's reversal rule (registry, ADR-378): PAIR (TRANSFER_OUT) reverses both
    // legs; IMMEDIATE (debits — the money comes back) posts at once; NEEDS_APPROVAL (a deposit —
    // the money leaves) awaits approval. OWNER is reversed only through its owner's own action;
    // NONE (a REVERSAL) cannot be reversed.
    if (reversalRule == TrustTransactionType.Reversal.OWNER) {
      throw new InvalidStateException(
          "Cannot reverse transaction",
          "This entry is reversed from its own screen (its statement line or investment), not"
              + " here");
    }
    if (reversalRule != TrustTransactionType.Reversal.PAIR
        && reversalRule != TrustTransactionType.Reversal.IMMEDIATE
        && reversalRule != TrustTransactionType.Reversal.NEEDS_APPROVAL) {
      throw new InvalidStateException(
          "Cannot reverse transaction",
          "Transaction type " + original.getTransactionType() + " cannot be reversed");
    }

    var reversalDate = resolveReversalDate(request.reversalDate(), original);

    if (reversalRule == TrustTransactionType.Reversal.PAIR) {
      return reverseTransferOut(original, reason, reversalDate, memberId);
    }
    return toResponse(postContra(original, reason, reversalDate, memberId));
  }

  /**
   * Reverses every posted original linked to a bank statement line — the unmatch of a {@code
   * RECORDED} line (§16.3.5; first caller in 647A). Each contra is dated the <b>line's</b> date
   * (the bank never did what the row said, so the pair leaves no trace in any as-at balance) and
   * goes through the normal reversal path; only rules that post at once are accepted ({@code
   * IMMEDIATE}, and {@code OWNER} — the unmatch is the owner's own action). Neither the line nor
   * the originals' {@code bank_statement_line_id} is touched here (647A's unmatch does that).
   *
   * <p><b>Enforced precondition (647A, 649B found.md R3):</b> the line must be {@code RECORDED} —
   * its rows were <i>recorded from the statement</i>. An ordinary match ({@code AUTO_MATCHED} /
   * {@code MANUALLY_MATCHED}) also sets {@code bank_statement_line_id} on a real firm payment, and
   * unmatching it must only clear the link — never book a contra; any other line state is refused
   * ({@code InvalidStateException}).
   *
   * <p>All originals are locked (finder order) and checked before the first contra is posted, so a
   * refusal leaves nothing half-reversed; each contra then takes its client's lock anchor (lock
   * order: originals, then client anchor — the same as {@link #reverseTransaction}).
   */
  @Transactional
  public List<TrustTransaction> reverseForUnmatch(UUID bankStatementLineId) {
    moduleGuard.requireModule(MODULE_ID);

    var line =
        bankStatementLineRepository
            .findById(bankStatementLineId)
            .orElseThrow(
                () -> new ResourceNotFoundException("BankStatementLine", bankStatementLineId));
    // The line's match status (not a transaction status): only a recorded line created its rows.
    if (!"RECORDED".equals(line.getMatchStatus())) {
      throw new InvalidStateException(
          "Cannot unmatch statement line",
          "Only a line recorded from the statement has entries to reverse");
    }
    var memberId = RequestScopes.requireMemberId();

    record Unmatched(TrustTransaction original, LocalDate reversalDate) {}
    var originals = new ArrayList<Unmatched>();
    for (var candidate :
        transactionRepository.findPostedOriginalsByBankStatementLineId(
            bankStatementLineId, List.of("RECORDED", "APPROVED"))) {
      var original =
          transactionRepository
              .findByIdForUpdate(candidate.getId())
              .orElseThrow(
                  () -> new ResourceNotFoundException("TrustTransaction", candidate.getId()));
      var rule = TrustTransactionType.of(original.getTransactionType()).reversal();
      if (rule != TrustTransactionType.Reversal.IMMEDIATE
          && rule != TrustTransactionType.Reversal.OWNER) {
        throw new InvalidStateException(
            "Cannot unmatch statement line",
            "A statement-line unmatch cannot reverse a "
                + original.getTransactionType()
                + " transaction (reversal rule "
                + rule
                + ")");
      }
      requireReversible(original);
      requireNotAPlacementPayment(original);
      originals.add(
          new Unmatched(original, resolveReversalDate(line.getTransactionDate(), original)));
    }

    var contras = new ArrayList<TrustTransaction>();
    for (var unmatched : originals) {
      contras.add(
          postContra(
              unmatched.original(),
              "Statement line unmatched",
              unmatched.reversalDate(),
              memberId));
    }
    return contras;
  }

  /**
   * G89-F86 (D40): a PAYMENT that placed a trust investment is never reversed, whatever the
   * investment's status — the investment is withdrawn instead. While the investment is ACTIVE or
   * MATURED a reversal would credit the client while the s86 register (and getMaturing, the Section
   * 35 data pack) still shows the money invested, and the investment could never be withdrawn; once
   * it is WITHDRAWN the withdrawal already returned the money, so a reversal would credit the
   * client twice (PR #1757 review). Applied by {@link #reverseTransaction} and {@link
   * #reverseForUnmatch} (CodeRabbit, PR #1789). Lock order as in
   * TrustInvestmentService.withdrawInvestment — the placement row (locked by the caller), then the
   * investment row.
   */
  private void requireNotAPlacementPayment(TrustTransaction original) {
    var placedInvestment =
        "PAYMENT".equals(original.getTransactionType())
            ? trustInvestmentRepository.findByPlacementTransactionIdForUpdate(original.getId())
            : Optional.<TrustInvestment>empty();
    if (placedInvestment.isPresent()) {
      throw new InvalidStateException(
          "Cannot reverse transaction",
          "This payment placed a trust investment ("
              + placedInvestment.get().getStatus()
              + "). Withdraw the investment instead of reversing its placement payment.");
    }
  }

  /**
   * The checks every reversal path shares: the account is open, the original is posted ({@code
   * RECORDED} or {@code APPROVED} — founder ruling #1, H-d) and not already reversed. A {@code
   * REJECTED} contra never posted, so it does not count (H-c); a pending one still blocks.
   */
  private void requireReversible(TrustTransaction original) {
    var account =
        trustAccountRepository
            .findById(original.getTrustAccountId())
            .orElseThrow(
                () -> new ResourceNotFoundException("TrustAccount", original.getTrustAccountId()));

    if (TrustAccountStatus.CLOSED == account.getStatus()) {
      throw new InvalidStateException("Cannot reverse transaction", "Trust account is closed");
    }

    if ("REVERSED".equals(original.getStatus())) {
      throw new InvalidStateException(
          "Cannot reverse transaction", "Transaction has already been reversed");
    }
    if (!"RECORDED".equals(original.getStatus()) && !"APPROVED".equals(original.getStatus())) {
      throw new InvalidStateException(
          "Cannot reverse transaction", "Only a recorded or approved transaction can be reversed");
    }

    if (original.getReversedById() != null
        || transactionRepository.existsByReversalOfAndStatusNot(original.getId(), "REJECTED")) {
      throw new InvalidStateException(
          "Cannot reverse transaction", "Transaction has already been reversed");
    }
  }

  /**
   * The fee transfer's date (ruling #6): {@code requested}, or today ({@code LocalDate.now(clock)},
   * the SA business date) when null; never in the future. No lower bound — the ruling sets none.
   */
  private LocalDate resolveFeeTransferDate(LocalDate requested) {
    var today = LocalDate.now(clock);
    if (requested == null) {
      return today;
    }
    if (requested.isAfter(today)) {
      throw new InvalidStateException(
          "Invalid fee transfer", "The fee transfer date cannot be in the future");
    }
    return requested;
  }

  /**
   * The contra's date: {@code requested}, or today ({@code LocalDate.now(clock)}, the SA business
   * date) when null; never in the future and never before the original's own date (§16.3.11).
   */
  private LocalDate resolveReversalDate(LocalDate requested, TrustTransaction original) {
    var today = LocalDate.now(clock);
    var reversalDate = requested != null ? requested : today;
    if (reversalDate.isAfter(today)) {
      throw new InvalidStateException(
          "Cannot reverse transaction", "The reversal date cannot be in the future");
    }
    if (reversalDate.isBefore(original.getTransactionDate())) {
      throw new InvalidStateException(
          "Cannot reverse transaction",
          "The reversal date cannot be before the transaction date ("
              + original.getTransactionDate()
              + ")");
    }
    return reversalDate;
  }

  /**
   * One contra entry for {@code original} (IMMEDIATE, OWNER or NEEDS_APPROVAL rule), dated {@code
   * reversalDate}. A contra that posts now runs under the client's lock anchor, cascades a fee
   * transfer's reversal into its fee note, and re-projects the portal; a credit contra is saved
   * awaiting approval and moves nothing yet. Audited as {@code trust_transaction.reversed}.
   */
  private TrustTransaction postContra(
      TrustTransaction original, String reason, LocalDate reversalDate, UUID memberId) {
    boolean postsNow =
        TrustTransactionType.of(original.getTransactionType()).reversal()
            != TrustTransactionType.Reversal.NEEDS_APPROVAL;

    var reversal = newContra(original, reason, reversalDate, memberId);

    // A contra that posts now moves the client's balance: serialise with its other postings first
    if (postsNow && original.getCustomerId() != null) {
      postingService.lockClient(original.getTrustAccountId(), original.getCustomerId());
    }

    // Links reversal_of. A contra that posts now is RECORDED (its final status) carrying the
    // negation of the original's effect, and the original is marked REVERSED with its own effects
    // kept (ADR-378). A credit contra is saved AWAITING_APPROVAL and leaves the original untouched
    // until it is approved (performApprovalCompletion).
    var savedReversal = postingService.postReversal(reversal, original);

    if (postsNow) {
      // GAP-L-70: cascade FEE_TRANSFER reversal back into the invoice domain.
      // When the trust ledger rolls back, the linked invoice must roll back symmetrically
      // (payment_event deleted; PAID/PARTIALLY_PAID -> SENT or PARTIALLY_PAID, recomputed from the
      // remaining COMPLETED events — ADR-347) so the books always tie out. Both writes share
      // this @Transactional boundary — atomic commit or atomic rollback.
      if ("FEE_TRANSFER".equals(original.getTransactionType()) && original.getInvoiceId() != null) {
        var paymentEvents =
            paymentEventRepository.findByInvoiceIdAndStatus(
                original.getInvoiceId(), PaymentEventStatus.COMPLETED);
        // Locate the payment event postPayment wrote at approval for this FEE_TRANSFER: provider
        // trust_fee_transfer, payment_reference = the trust reference, amount = the transfer
        // amount. We filter by provider, reference AND amount because trust references are
        // user-supplied at recordFeeTransfer time and not constrained to be unique — the provider
        // keeps a manual/card/Xero event that happens to share reference + amount from being
        // deleted, and the amount breaks ties on multi-payment invoices.
        var matches =
            paymentEvents.stream()
                .filter(
                    pe ->
                        PaymentPosting.TRUST_PROVIDER.equals(pe.getProviderSlug())
                            && pe.getPaymentReference() != null
                            && pe.getPaymentReference().equals(original.getReference())
                            && pe.getAmount() != null
                            && pe.getAmount().compareTo(original.getAmount()) == 0)
                .toList();
        if (matches.size() > 1) {
          log.warn(
              "Trust reversal cascade: {} candidate payment_events match reference={} + amount={} "
                  + "for invoice {}; picking the first deterministically. Consider auditing for "
                  + "duplicate FEE_TRANSFER references.",
              matches.size(),
              original.getReference(),
              original.getAmount(),
              original.getInvoiceId());
        }
        if (!matches.isEmpty()) {
          invoiceService.reversePayment(original.getInvoiceId(), matches.get(0).getId());
        } else {
          // The trust transaction had an invoice link but no matching payment_event was found.
          // This can happen if the invoice was voided, paid through another channel, or the
          // FEE_TRANSFER pre-dates GAP-L-70 (no PaymentEvent was created at approval time).
          // Log at WARN so any real data-integrity issue is observable; the trust ledger
          // reversal itself still completes correctly.
          log.warn(
              "Trust reversal cascade: FEE_TRANSFER {} (invoice={}) had no matching "
                  + "COMPLETED payment_event with reference={} + amount={}. Invoice domain is "
                  + "no-op; trust ledger reversal proceeds.",
              original.getId(),
              original.getInvoiceId(),
              original.getReference(),
              original.getAmount());
        }
      }
      publishContraRecorded(savedReversal, memberId);
    }

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_transaction.reversed")
            .entityType("trust_transaction")
            .entityId(savedReversal.getId())
            .details(
                AuditDetails.matterScoped(
                    savedReversal.getProjectId(),
                    original.getCustomerId(),
                    Map.of(
                        "original_transaction_id", original.getId().toString(),
                        "original_type", original.getTransactionType(),
                        "amount", original.getAmount().toString(),
                        "reversal_status", savedReversal.getStatus(),
                        "reason", reason,
                        "reversal_date", reversalDate.toString())))
            .build());

    return savedReversal;
  }

  /**
   * Reverses a TRANSFER_OUT by reversing both legs: the TRANSFER_OUT (credit back to source) and
   * the paired TRANSFER_IN (debit from target). Transfer reversals are internal moves, so both
   * contras post at once (RECORDED, no approval needed), cashbook-neutral, dated {@code
   * reversalDate}.
   */
  private TrustTransactionResponse reverseTransferOut(
      TrustTransaction original, String reason, LocalDate reversalDate, UUID memberId) {
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

    // Validate paired TRANSFER_IN is not already reversed (a REJECTED contra never posted)
    if (pairedTransferIn.getReversedById() != null
        || transactionRepository.existsByReversalOfAndStatusNot(
            pairedTransferIn.getId(), "REJECTED")) {
      throw new InvalidStateException(
          "Cannot reverse transaction", "Paired TRANSFER_IN has already been reversed");
    }

    // Lock both ledger cards first, in deterministic UUID order to prevent deadlocks, so the
    // target's balances are checked under the lock and before anything is written.
    UUID sourceCustomerId = original.getCustomerId();
    UUID targetCustomerId = pairedTransferIn.getCustomerId();

    UUID firstId =
        sourceCustomerId.compareTo(targetCustomerId) < 0 ? sourceCustomerId : targetCustomerId;
    UUID secondId =
        sourceCustomerId.compareTo(targetCustomerId) < 0 ? targetCustomerId : sourceCustomerId;

    postingService.lockClient(original.getTrustAccountId(), firstId);
    postingService.lockClient(original.getTrustAccountId(), secondId);

    // G89-F12: undoing the TRANSFER_IN debits the target, so the target must still hold the money —
    // on its derived balance and on the transfer's matter (the TRANSFER_IN is still posted, so
    // both sums include it). Without this the target could already have spent it.
    var targetCustomer =
        customerRepository
            .findById(targetCustomerId)
            .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.CLIENT, targetCustomerId));
    var targetAvailable =
        ledgerQueries.clientBalance(original.getTrustAccountId(), targetCustomerId, null);
    if (targetAvailable.compareTo(pairedTransferIn.getAmount()) < 0) {
      throw new InvalidStateException(
          "Insufficient trust balance",
          "Insufficient trust balance for "
              + targetCustomer.getName()
              + ". Available: "
              + rand(targetAvailable)
              + ", Requested: "
              + rand(pairedTransferIn.getAmount()));
    }
    requireMatterCellCovers(
        pairedTransferIn.getTrustAccountId(),
        targetCustomerId,
        targetCustomer.getName(),
        pairedTransferIn.getProjectId(),
        pairedTransferIn.getAmount());

    // Contra for the TRANSFER_OUT (credits the source back); the TRANSFER_OUT is marked REVERSED
    // and keeps its own effect
    var savedOutReversal =
        postingService.postReversal(newContra(original, reason, reversalDate, memberId), original);

    // Contra for the TRANSFER_IN (debits the target); the TRANSFER_IN is marked REVERSED and keeps
    // its own effect. Same reference as the OUT contra (the pair shares one).
    var savedInReversal =
        postingService.postReversal(
            newContra(pairedTransferIn, reason, reversalDate, memberId), pairedTransferIn);

    // One recorded event per contra leg (as recordTransfer — GAP-L-52): both matters re-project.
    publishContraRecorded(savedOutReversal, memberId);
    publishContraRecorded(savedInReversal, memberId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_transaction.reversed")
            .entityType("trust_transaction")
            .entityId(savedOutReversal.getId())
            .details(
                // R-17: scoped to the SOURCE client (the reversed TRANSFER_OUT's owner).
                AuditDetails.matterScoped(
                    savedOutReversal.getProjectId(),
                    original.getCustomerId(),
                    Map.of(
                        "original_transaction_id", original.getId().toString(),
                        "paired_transfer_in_id", pairedTransferIn.getId().toString(),
                        "original_type", original.getTransactionType(),
                        "amount", original.getAmount().toString(),
                        "reversal_status", "RECORDED",
                        "reason", reason,
                        "reversal_date", reversalDate.toString())))
            .build());

    return toResponse(savedOutReversal);
  }

  /** A {@code REVERSAL} draft for {@code original}: its ledger target, its amount, its own date. */
  private static TrustTransaction newContra(
      TrustTransaction original, String reason, LocalDate reversalDate, UUID memberId) {
    return new TrustTransaction(
        original.getTrustAccountId(),
        "REVERSAL",
        original.getAmount(),
        original.getCustomerId(),
        original.getProjectId(),
        original.getCounterpartyCustomerId(),
        // Build reversal reference, truncating to 200-char column limit
        buildReversalReference(original.getReference()),
        reason,
        reversalDate,
        null, // status: decided by postReversal from the original's reversal rule
        memberId);
  }

  /**
   * A contra posted: publish the recorded event so the portal read-model re-projects the matter at
   * once (§16.3.11). The portal email channel only mails deposits, so no client email is sent.
   */
  private void publishContraRecorded(TrustTransaction contra, UUID memberId) {
    eventPublisher.publishEvent(
        TrustTransactionRecordedEvent.recorded(
            contra.getId(),
            contra.getTrustAccountId(),
            contra.getProjectId(),
            "REVERSAL",
            contra.getAmount(),
            contra.getTransactionDate(),
            contra.getCustomerId(),
            memberId,
            RequestScopes.getTenantIdOrNull(),
            RequestScopes.getOrgIdOrNull()));
  }

  // --- 440.10: Cashbook Balance ---

  /** The cashbook as at {@code asAt} (null = today, {@code LocalDate.now(clock)}) — 646D. */
  @Transactional(readOnly = true)
  public CashbookBalanceResponse getCashbookBalance(UUID trustAccountId, LocalDate asAt) {
    moduleGuard.requireModule(MODULE_ID);

    trustAccountRepository
        .findById(trustAccountId)
        .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", trustAccountId));

    return new CashbookBalanceResponse(
        ledgerQueries.cashbook(trustAccountId, asAt != null ? asAt : LocalDate.now(clock)));
  }

  /** The cashbook as at today. */
  @Transactional(readOnly = true)
  public CashbookBalanceResponse getCashbookBalance(UUID trustAccountId) {
    return getCashbookBalance(trustAccountId, null);
  }

  // --- Private Helpers ---

  /**
   * G89-F28 (D36): every new client trust posting names a matter linked to the client (directly,
   * {@code projects.customer_id}, or through {@code customer_projects} — the client's matter list).
   * A row without a matter never reaches the portal, whose trust view is per client and matter, so
   * the client's portal balance would fall short of the firm ledger. Rows derived from an existing
   * row (reversals) do not pass through here.
   */
  private void requireClientMatter(Customer client, UUID matterId) {
    if (matterId == null) {
      throw matterRequired(null);
    }
    var matter =
        projectRepository
            .findById(matterId)
            .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.MATTER, matterId));
    if (client.getId().equals(matter.getCustomerId())
        || customerProjectRepository.existsByCustomerIdAndProjectId(client.getId(), matterId)) {
      return;
    }
    var properties = new LinkedHashMap<String, Object>();
    properties.put("code", MATTER_NOT_LINKED_TO_CLIENT);
    properties.put("matterId", matterId);
    throw InvalidStateException.withProperties(
        "Matter not linked to client",
        "Matter " + matter.getName() + " is not linked to client " + client.getName(),
        properties);
  }

  private static InvalidStateException matterRequired(String reason) {
    var properties = new LinkedHashMap<String, Object>();
    properties.put("code", MATTER_REQUIRED);
    if (reason != null) {
      properties.put("reason", reason);
    }
    return InvalidStateException.withProperties(
        "Matter required", MATTER_REQUIRED_DETAIL, properties);
  }

  /**
   * G89-F12: a debit on a matter must be covered by this client's money on that matter in this
   * trust account (the matter sub-ledger cell), not only by the client balance — otherwise a refund
   * of "matter + client-level credit" leaves the matter negative while the closure gate (which
   * reads the matter) and the client balance disagree, and a co-client can spend another client's
   * money on a shared matter. No-op for an unmattered debit (the client balance check covers it).
   * Callers hold the client's lock anchor, so the cell sum cannot race with another posting for the
   * client.
   */
  private void requireMatterCellCovers(
      UUID trustAccountId, UUID customerId, String clientName, UUID projectId, BigDecimal amount) {
    if (projectId == null) {
      return;
    }
    var available = ledgerQueries.matterCellBalance(trustAccountId, customerId, projectId, null);
    if (available == null) {
      available = BigDecimal.ZERO;
    }
    if (available.compareTo(amount) >= 0) {
      return;
    }
    var matterName =
        projectRepository.findById(projectId).map(Project::getName).orElse(projectId.toString());
    var properties = new LinkedHashMap<String, Object>();
    properties.put("code", INSUFFICIENT_MATTER_TRUST_BALANCE);
    properties.put("matterId", projectId);
    properties.put("available", available);
    properties.put("requested", amount);
    throw InvalidStateException.withProperties(
        "Insufficient trust balance",
        "Insufficient trust balance on matter "
            + matterName
            + " for "
            + clientName
            + ". Available on matter: "
            + rand(available)
            + ", Requested: "
            + rand(amount),
        properties);
  }

  /** A rand amount for a refusal message, in the house en-ZA format ("R10 055,21"; G89-F40). */
  private static String rand(BigDecimal amount) {
    return VariableFormatter.formatCurrencyText(amount, TRUST_LOCALE);
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

  /**
   * Every trust write path (deposit, transfer, payment, fee transfer, refund) runs this before any
   * balance logic. Amounts are stored at cents ({@code numeric(15,2)}), so a sub-cent amount is
   * refused rather than rounded on save or compared unrounded against a balance (PR #1752 review).
   * Trailing zeros ({@code 250.500}) are a whole number of cents and pass.
   */
  private void validateAmount(BigDecimal amount) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new InvalidStateException(
          "Invalid amount", "Amount must be a positive value, got: " + amount);
    }
    if (amount.stripTrailingZeros().scale() > 2) {
      throw new InvalidStateException(
          "Invalid amount", "Amount must not have more than 2 decimal places");
    }
  }

  private void validateReference(String reference) {
    if (reference == null || reference.isBlank()) {
      throw new InvalidStateException("Invalid reference", "Reference must not be blank");
    }
  }

  private TrustTransactionResponse toResponse(TrustTransaction entity) {
    return toResponses(List.of(entity)).getFirst();
  }

  /**
   * Maps a page of rows with two batched lookups, never one per row: the types of the originals the
   * page's REVERSAL rows reverse (for their {@code direction}), and which of the page's posted rows
   * have a contra awaiting approval ({@code reversalPending}). Public for the services that post
   * rows outside this one ({@code StatementLinePostingService}, 647A).
   */
  public List<TrustTransactionResponse> toResponses(List<TrustTransaction> rows) {
    Set<UUID> originalIds = new HashSet<>();
    List<UUID> postedIds = new ArrayList<>();
    for (var row : rows) {
      if ("REVERSAL".equals(row.getTransactionType()) && row.getReversalOf() != null) {
        originalIds.add(row.getReversalOf());
      }
      if ("RECORDED".equals(row.getStatus()) || "APPROVED".equals(row.getStatus())) {
        postedIds.add(row.getId());
      }
    }
    Map<UUID, String> reversedTypes = new HashMap<>();
    if (!originalIds.isEmpty()) {
      for (var original : transactionRepository.findAllById(originalIds)) {
        reversedTypes.put(original.getId(), original.getTransactionType());
      }
    }
    Set<UUID> pending =
        postedIds.isEmpty()
            ? Set.of()
            : new HashSet<>(transactionRepository.findPendingReversalOriginalIds(postedIds));
    return rows.stream()
        .map(
            row ->
                toResponse(
                    row,
                    row.getReversalOf() == null ? null : reversedTypes.get(row.getReversalOf()),
                    row.getId() != null && pending.contains(row.getId())))
        .toList();
  }

  private static TrustTransactionResponse toResponse(
      TrustTransaction entity, String reversedType, boolean reversalPending) {
    return new TrustTransactionResponse(
        entity.getId(),
        entity.getTrustAccountId(),
        entity.getTransactionType(),
        TrustTransactionTypeLabels.firmLabel(entity.getTransactionType()),
        TrustTransactionDirection.ofRow(entity.getTransactionType(), reversedType),
        entity.getLedger(),
        entity.getAmount(),
        entity.getCustomerId(),
        entity.getProjectId(),
        entity.getCreditorAccountId(),
        entity.getInvestmentId(),
        entity.getCounterpartyCustomerId(),
        entity.getInvoiceId(),
        entity.getReference(),
        entity.getDescription(),
        entity.getTransactionDate(),
        entity.getStatus(),
        entity.getPostedAt(),
        entity.getApprovedBy(),
        entity.getApprovedAt(),
        entity.getSecondApprovedBy(),
        entity.getSecondApprovedAt(),
        entity.getRejectedBy(),
        entity.getRejectedAt(),
        entity.getRejectionReason(),
        entity.getReversalOf(),
        entity.getReversedById(),
        reversalPending,
        entity.getBankStatementLineId(),
        entity.getRecordedBy(),
        entity.getCreatedAt(),
        null,
        entity.getChargeRecoverable(),
        entity.getChargeVatAmount());
  }
}
