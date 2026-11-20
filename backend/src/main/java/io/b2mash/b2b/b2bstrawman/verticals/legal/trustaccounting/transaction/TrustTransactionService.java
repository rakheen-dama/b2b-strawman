package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import io.b2mash.b2b.b2bstrawman.audit.AuditDetails;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
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
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.BankStatementLineRepository;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

  /**
   * The rows that resolve an unidentified deposit (647D, §16.3.6): their approval, rejection and
   * reversal also move the item, through {@link UnidentifiedResolutionHooks}.
   */
  private static final Set<TrustTransactionType> UNIDENTIFIED_RESOLUTIONS =
      EnumSet.of(TrustTransactionType.UNIDENT_ALLOC_OUT, TrustTransactionType.UNIDENT_FUND_PAY);

  /**
   * An investment's placement rows (648A, §16.3.7): their approval posts the receive leg on the
   * investment account and their rejection cancels the investment, through {@link
   * InvestmentPlacementHooks}.
   */
  private static final Set<TrustTransactionType> PLACEMENTS =
      EnumSet.of(TrustTransactionType.INV_PLACE, TrustTransactionType.FIRM_INV_PLACE);

  /**
   * FU-9 (Phase 92 F-6; founder-signed ruling {@code
   * qa_cycle/evidence/phase92/legal/F-6-opinion.md}): the firm-initiated outflows from a trust
   * account whose final approval the account's cashbook must cover ({@link
   * #requireCashbookCovers}), each with the noun its refusal names. A section 86(3) placement
   * ({@code FIRM_INV_PLACE}) is the same rule, checked by its owner under the same account lock
   * with its own refusal ({@link InvestmentPlacementHooks#onPlacementApproving}, 648A F-3). Every
   * other row is never checked: bank-confirmed entries (statement-driven postings, bank charges,
   * sweeps, Fund interest), reversal contras (a dishonoured deposit), inflows and paired legs
   * record what happened; the integrity check's {@code CASHBOOK_IN_DEBIT} surfaces any debit they
   * leave.
   */
  private static final Map<TrustTransactionType, String> CASHBOOK_COVERED_OUTFLOWS =
      Map.of(
          TrustTransactionType.PAYMENT, "payment",
          TrustTransactionType.REFUND, "refund",
          TrustTransactionType.FEE_TRANSFER, "fee transfer",
          TrustTransactionType.INV_PLACE, "section 86(4) placement",
          TrustTransactionType.UNIDENT_FUND_PAY, "payment to the Fund");

  private final TrustTransactionRepository transactionRepository;
  private final TrustPostingService postingService;
  private final TrustLedgerQueryRepository ledgerQueries;
  private final TrustAccountRepository trustAccountRepository;
  private final CustomerRepository customerRepository;
  private final TrustClientMatterRule clientMatterRule;
  private final UnidentifiedResolutionHooks unidentifiedResolutionHooks;
  private final InvestmentPlacementHooks investmentPlacementHooks;
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
      TrustAccountRepository trustAccountRepository,
      CustomerRepository customerRepository,
      TrustClientMatterRule clientMatterRule,
      UnidentifiedResolutionHooks unidentifiedResolutionHooks,
      InvestmentPlacementHooks investmentPlacementHooks,
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
    this.trustAccountRepository = trustAccountRepository;
    this.customerRepository = customerRepository;
    this.clientMatterRule = clientMatterRule;
    this.unidentifiedResolutionHooks = unidentifiedResolutionHooks;
    this.investmentPlacementHooks = investmentPlacementHooks;
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
      // ADR-361: server-computed on the client-ledger /history endpoint (null on non-posted /
      // REVERSAL rows) and the creditor-ledger /transactions endpoint (647C: null on unposted rows
      // only); null on every other producer.
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
    return postDeposit(trustAccountId, request);
  }

  private TrustTransactionResponse postDeposit(UUID trustAccountId, RecordDepositRequest request) {
    moduleGuard.requireModule(MODULE_ID);
    TrustAmounts.requireValid(request.amount());
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

    clientMatterRule.requireClientMatter(customer, request.matterId());

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
    TrustAmounts.requireValid(request.amount());
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
    clientMatterRule.requireClientMatter(sourceCustomer, request.sourceMatterId());
    clientMatterRule.requireClientMatter(targetCustomer, request.targetMatterId());

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
    TrustAmounts.requireValid(request.amount());
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

    clientMatterRule.requireClientMatter(customer, request.matterId());

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
    TrustAmounts.requireValid(request.amount());
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
      throw TrustClientMatterRule.matterRequired(
          "Fee note "
              + (invoice.getInvoiceNumber() != null
                  ? invoice.getInvoiceNumber()
                  : invoice.getId().toString())
              + " does not belong to a single matter, so the transfer has no matter to post to.");
    }
    clientMatterRule.requireClientMatter(customer, inferredProjectId);

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
    TrustAmounts.requireValid(request.amount());
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

    clientMatterRule.requireClientMatter(customer, request.matterId());

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
      // 648A review F-3: a placement's owner re-checks the cover the client check below cannot
      // (a section 86(3) placement's is the funding account's cashbook). Lock order: this row, then
      // the funding account (section 86(3), which names no client, so takes no anchor). A section
      // 86(4) placement takes nothing here: its client's anchor below, then the account (FU-9).
      if (PLACEMENTS.contains(type)) {
        investmentPlacementHooks.onPlacementApproving(transaction);
      }
      // FU-9 R-1: an unidentified deposit's resolution takes its item and (an allocation) the
      // destination client's anchor here, before the cashbook check's account lock and the posting
      // lock below — the global order, so it cannot deadlock with a payment approval for that
      // client.
      if (UNIDENTIFIED_RESOLUTIONS.contains(type)) {
        unidentifiedResolutionHooks.onResolutionApproving(transaction);
      }
      // A CLIENT-ledger debit (PAYMENT, FEE_TRANSFER, REFUND, INV_PLACE) takes money off the
      // client; an unidentified-deposit resolution (UNIDENT_ALLOC_OUT, UNIDENT_FUND_PAY:
      // NON_CLIENT, no client on the row) takes it off its own item, whole by construction
      // (§16.2.4).
      if (type.ledger() == TrustLedger.CLIENT) {
        requireClientCovers(transaction);
      }

      // 441.9 / Phase 88 U15 (ADR-347, N-5): a fee transfer posts its real amount through the one
      // payment entry point — PARTIALLY_PAID when it leaves a balance, PAID when it clears it, and
      // (ADR-347 A4) capped at PAID with an invoice.payment_excess audit when it exceeds the
      // balance due. postPayment writes the payment_events row (provider trust_fee_transfer, method
      // TRUST_TRANSFER, destination TRUST, reference = the trust reference), which the reversal
      // cascade in reverseTransaction matches on. No PSP is involved: a trust fee transfer is an
      // internal accounting operation. A PAID fee note is refused up front so trust is never
      // debited for a settled fee note.
      boolean settlesFeeNote =
          "FEE_TRANSFER".equals(transaction.getTransactionType())
              && transaction.getInvoiceId() != null;
      if (settlesFeeNote) {
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
      }

      // FU-9: a firm-initiated outflow must also be covered by the account's cashbook. The account
      // lock is the last step of the global lock order (TrustPostingService): after this row, the
      // fee note and the client's lock anchor, never before the anchor (650A F-11).
      var outflow = CASHBOOK_COVERED_OUTFLOWS.get(type);
      if (outflow != null) {
        requireCashbookCovers(transaction, outflow);
      }

      if (settlesFeeNote) {
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
    // 647D: an approved allocation posts its UNIDENT_ALLOC_IN and the item leaves HELD in the same
    // transaction (lock order: this row, the item, then the client's lock anchor).
    if (UNIDENTIFIED_RESOLUTIONS.contains(type)) {
      unidentifiedResolutionHooks.onResolutionApproved(transaction);
    }
    // 648A: an approved placement posts its receive leg on the investment account in the same
    // transaction (lock order: this row, then the investment row).
    if (PLACEMENTS.contains(type)) {
      investmentPlacementHooks.onPlacementApproved(transaction);
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

  /**
   * FU-9 (Phase 92 F-6, ruling 1): the trust account's derived cashbook must cover {@code
   * transaction}'s amount, which is about to leave the account, as at {@code max(today, transaction
   * date)} <em>and every later date a posted row is dated</em> (R-2): the lowest the cashbook
   * stands from then on, so an already-approved later-dated outflow is not overdrawn by this one.
   * Read under the account's lock ({@code FOR UPDATE}), so every covered outflow's approval on the
   * account — and a section 86(3) placement's approval or request, which take the same lock — is
   * serialised with this read; the posting lock taken next re-takes the row {@code FOR SHARE}
   * (already held). The row is still AWAITING_APPROVAL, so the cashbook excludes it. Refused with
   * the client-cover refusal's title (400), naming the remedy: money placed in a section 86(3)
   * investment is withdrawn to the banking account first (Rule 54.14.7.3).
   */
  private void requireCashbookCovers(TrustTransaction transaction, String outflow) {
    var accountId = transaction.getTrustAccountId();
    trustAccountRepository
        .findByIdForUpdate(accountId)
        .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", accountId));
    var today = LocalDate.now(clock);
    var asAt =
        transaction.getTransactionDate().isAfter(today) ? transaction.getTransactionDate() : today;
    var cashbook = ledgerQueries.lowestCashbookFrom(accountId, asAt);
    if (cashbook.compareTo(transaction.getAmount()) < 0) {
      throw new InvalidStateException(
          "Insufficient trust balance",
          "The trust account's cashbook does not cover this "
              + outflow
              + ". Available: "
              + rand(cashbook)
              + ", Requested: "
              + rand(transaction.getAmount())
              + ". If trust money has been placed in a section 86(3) investment, withdraw it from"
              + " the investment first.");
    }
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

    // 647D: a rejected allocation or payment to the Fund leaves its item HELD, resolution cleared.
    if (UNIDENTIFIED_RESOLUTIONS.contains(
        TrustTransactionType.of(transaction.getTransactionType()))) {
      unidentifiedResolutionHooks.onResolutionRejected(transaction);
    }

    // G89-F87 (D41), 648A: an investment is saved ACTIVE while its placement awaits approval; a
    // rejected placement never moved the money, so the investment is CANCELLED in the same
    // transaction (lock order: this row, then the investment row).
    if (PLACEMENTS.contains(TrustTransactionType.of(transaction.getTransactionType()))) {
      investmentPlacementHooks.onPlacementRejected(transaction, reason);
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

    // A VIA_PAIR row cannot be reversed directly — reverse its pair, which reverses both legs: a
    // TRANSFER_IN through its TRANSFER_OUT, an UNIDENT_ALLOC_IN through its UNIDENT_ALLOC_OUT
    // (647D).
    var type = TrustTransactionType.of(original.getTransactionType());
    var reversalRule = type.reversal();
    if (reversalRule == TrustTransactionType.Reversal.VIA_PAIR) {
      if (type == TrustTransactionType.UNIDENT_ALLOC_IN) {
        throw new InvalidStateException(
            "Cannot reverse transaction",
            "Reverse the unidentified deposit's allocation instead to undo both entries");
      }
      throw new InvalidStateException(
          "Cannot reverse TRANSFER_IN directly",
          "Reverse the paired TRANSFER_OUT instead to undo both legs of the transfer");
    }

    requireReversible(original);

    // 648A (replaces G89-F86): an investment's rows are never reversed — the investment is
    // withdrawn instead. Refused on the type, under the row lock already held on the original.
    if (isFinalInvestmentRow(type)) {
      throw new InvalidStateException("Cannot reverse transaction", investmentRowRefusal(type));
    }

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

    // PAIR by type (647D, F-646E-3): each pair has its own second leg and its own lookup.
    if (reversalRule == TrustTransactionType.Reversal.PAIR) {
      return switch (type) {
        case TRANSFER_OUT -> reverseTransferOut(original, reason, reversalDate, memberId);
        case UNIDENT_ALLOC_OUT ->
            reverseUnidentifiedAllocation(original, reason, reversalDate, memberId);
        default -> throw new IllegalStateException("No pair reversal for " + type);
      };
    }
    // A payment to the Fund that bounced: its item is HELD again, in the same transaction.
    if (type == TrustTransactionType.UNIDENT_FUND_PAY) {
      unidentifiedResolutionHooks.onResolutionReversed(original, reversalDate);
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
      var originalType = TrustTransactionType.of(original.getTransactionType());
      if (isFinalInvestmentRow(originalType)) {
        throw new InvalidStateException(
            "Cannot unmatch statement line", investmentRowRefusal(originalType));
      }
      var rule = originalType.reversal();
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
   * 648A: an investment's row that is never reversed — registry-derived ({@link
   * TrustTransactionType#isInvestmentRow()} with {@code reversal = NONE}): the placement, receipt,
   * withdrawal and return legs of both bases. An investment row the registry lets its owner reverse
   * ({@code INV_INTEREST}, and {@code FUND_INV_SHARE}, both {@code OWNER}) keeps the owner refusal:
   * 648B reverses them through their interest entry ({@code
   * TrustInvestmentService.reverseInterest}) or by unmatching the statement line they were recorded
   * from.
   */
  private static boolean isFinalInvestmentRow(TrustTransactionType type) {
    return type.isInvestmentRow() && type.reversal() == TrustTransactionType.Reversal.NONE;
  }

  /**
   * 648A (replaces G89-F86): why an investment row cannot be reversed. A placement is undone by
   * withdrawing the investment (its whole balance returns to the funding account); every other
   * investment row is likewise final (registry {@code reversal = NONE}), so a withdrawn
   * investment's placement can never be reversed into a second credit.
   */
  private static String investmentRowRefusal(TrustTransactionType type) {
    return PLACEMENTS.contains(type)
        ? "This entry placed a trust investment. Withdraw the investment instead of reversing its"
            + " placement."
        : "Investment entries cannot be reversed. Withdraw the investment instead.";
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

    var reversal = TrustPostingService.contraDraft(original, reason, reversalDate, memberId);

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
        postingService.postReversal(
            TrustPostingService.contraDraft(original, reason, reversalDate, memberId), original);

    // Contra for the TRANSFER_IN (debits the target); the TRANSFER_IN is marked REVERSED and keeps
    // its own effect. Same reference as the OUT contra (the pair shares one).
    var savedInReversal =
        postingService.postReversal(
            TrustPostingService.contraDraft(pairedTransferIn, reason, reversalDate, memberId),
            pairedTransferIn);

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

  /**
   * Undoes an approved allocation of an unidentified deposit (§16.3.6 "Undo", 647D): the item
   * returns to HELD, and both legs are reversed as a pair, dated {@code reversalDate} — the {@code
   * UNIDENT_ALLOC_OUT} (unidentified ledger +) and its {@code UNIDENT_ALLOC_IN} (client −);
   * cashbook-neutral. Like a transfer reversal, the client must still hold the money, on that
   * matter. The pair is found through the item ({@code allocation_credit_transaction_id}), never by
   * reference. Lock order: the ALLOC_OUT (locked by the caller) → the item (hook) → the ALLOC_IN →
   * the client's lock anchor. A refusal rolls everything back, the item's return to HELD included.
   */
  private TrustTransactionResponse reverseUnidentifiedAllocation(
      TrustTransaction original, String reason, LocalDate reversalDate, UUID memberId) {
    UUID creditId =
        unidentifiedResolutionHooks
            .onResolutionReversed(original, reversalDate)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Allocation " + original.getId() + " has no credit leg to reverse"));
    var credit =
        transactionRepository
            .findByIdForUpdate(creditId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustTransaction", creditId));
    if (credit.getReversedById() != null
        || transactionRepository.existsByReversalOfAndStatusNot(credit.getId(), "REJECTED")) {
      throw new InvalidStateException(
          "Cannot reverse transaction", "The allocated deposit has already been reversed");
    }

    postingService.lockClient(credit.getTrustAccountId(), credit.getCustomerId());
    var client =
        customerRepository
            .findById(credit.getCustomerId())
            .orElseThrow(
                () -> new ResourceNotFoundException(EntityNoun.CLIENT, credit.getCustomerId()));
    var available =
        ledgerQueries.clientBalance(credit.getTrustAccountId(), credit.getCustomerId(), null);
    if (available.compareTo(credit.getAmount()) < 0) {
      throw new InvalidStateException(
          "Insufficient trust balance",
          "Insufficient trust balance for "
              + client.getName()
              + ". Available: "
              + rand(available)
              + ", Requested: "
              + rand(credit.getAmount()));
    }
    requireMatterCellCovers(
        credit.getTrustAccountId(),
        credit.getCustomerId(),
        client.getName(),
        credit.getProjectId(),
        credit.getAmount());

    // Both contras post at once (PAIR / VIA_PAIR) and carry the item's id (postReversal copies the
    // ledger target), so EQ-4 reads the item as allocated until the reversal date.
    var outContra =
        postingService.postReversal(
            TrustPostingService.contraDraft(original, reason, reversalDate, memberId), original);
    var inContra =
        postingService.postReversal(
            TrustPostingService.contraDraft(credit, reason, reversalDate, memberId), credit);
    // NON_CLIENT: the portal sync skips it (no client). CLIENT: the client's matter re-projects.
    publishContraRecorded(outContra, memberId);
    publishContraRecorded(inContra, memberId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_transaction.reversed")
            .entityType("trust_transaction")
            .entityId(outContra.getId())
            .details(
                AuditDetails.matterScoped(
                    credit.getProjectId(),
                    credit.getCustomerId(),
                    Map.of(
                        "original_transaction_id", original.getId().toString(),
                        "paired_allocation_credit_id", credit.getId().toString(),
                        "original_type", original.getTransactionType(),
                        "amount", original.getAmount().toString(),
                        "reversal_status", "RECORDED",
                        "reason", reason,
                        "reversal_date", reversalDate.toString())))
            .build());

    return toResponse(outContra);
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
