package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor;

import io.b2mash.b2b.b2bstrawman.audit.AuditDetails;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.EntityNoun;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustTransactionApprovalEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustTransactionRecordedEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustClientMatterRule;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustPostingService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.UnidentifiedResolutionHooks;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unidentified deposits (Phase 92, ADR-376, §16.3.6). The "hold" half (647B): an item is opened
 * when a statement line is recorded as {@code UNIDENTIFIED_DEPOSIT}, listed oldest first with its
 * age, and reversed when its line is unmatched. The "resolve" half (647D): a request to allocate
 * the item to a client and matter ({@code UNIDENT_ALLOC_OUT}) or to pay it to the Fund ({@code
 * UNIDENT_FUND_PAY}) saves one pending row under the account's maker-checker rule; this service's
 * {@link UnidentifiedResolutionHooks} move the item when that row is approved (an allocation posts
 * its {@code UNIDENT_ALLOC_IN} credit leg then), rejected, or reversed.
 *
 * <p>Dependency direction: {@code StatementLinePostingService} calls this service, and {@code
 * TrustTransactionService} calls its hooks through the {@link UnidentifiedResolutionHooks}
 * interface; this service never calls back into either and imports nothing from the {@code
 * reconciliation} package — the caller hands the line's values over as a {@link StatementCredit}.
 * Every posting goes through {@link TrustPostingService}; the transaction repository is read only.
 *
 * <p>Lock order for every operation on an item: the item row first (after the resolution row the
 * caller already holds, for the hooks), then — for an allocation — the client's lock anchor.
 */
@Service
public class UnidentifiedDepositService implements UnidentifiedResolutionHooks {

  private static final String MODULE_ID = "trust_accounting";
  private static final String UNMATCH_TITLE = "Cannot unmatch statement line";
  private static final String ALLOCATE_TITLE = "Cannot allocate deposit";
  private static final String PAY_TITLE = "Cannot pay deposit to the Fund";
  private static final String PENDING_DETAIL =
      "This deposit already has a pending allocation or payment";
  private static final String NOT_HELD_DETAIL =
      "Only a held deposit can be allocated or paid to the Fund";
  private static final String ALLOC_OUT = "UNIDENT_ALLOC_OUT";
  private static final String ALLOC_IN = "UNIDENT_ALLOC_IN";
  private static final String FUND_PAY = "UNIDENT_FUND_PAY";

  /** The received date as a client reads it on the allocated ledger line ("3 November 2026"). */
  private static final DateTimeFormatter RECEIVED =
      DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

  /** An item held longer than this many days is flagged (§16.8.4 band "over one year"). */
  private static final int ONE_YEAR_DAYS = 365;

  private final UnidentifiedDepositRepository depositRepository;
  private final TrustAccountRepository trustAccountRepository;
  private final TrustPostingService postingService;
  private final TrustTransactionRepository transactionRepository;
  private final CustomerRepository customerRepository;
  private final ProjectRepository projectRepository;
  private final TrustClientMatterRule clientMatterRule;
  private final VerticalModuleGuard moduleGuard;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public UnidentifiedDepositService(
      UnidentifiedDepositRepository depositRepository,
      TrustAccountRepository trustAccountRepository,
      TrustPostingService postingService,
      TrustTransactionRepository transactionRepository,
      CustomerRepository customerRepository,
      ProjectRepository projectRepository,
      TrustClientMatterRule clientMatterRule,
      VerticalModuleGuard moduleGuard,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this.depositRepository = depositRepository;
    this.trustAccountRepository = trustAccountRepository;
    this.postingService = postingService;
    this.transactionRepository = transactionRepository;
    this.customerRepository = customerRepository;
    this.projectRepository = projectRepository;
    this.clientMatterRule = clientMatterRule;
    this.moduleGuard = moduleGuard;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  // --- DTO Records ---

  /**
   * One item (§16.3.6). {@code ageDays} counts from the received date to today for every item
   * (never negative — 0 for an item received after today); {@code olderThanOneYear} flags only a
   * HELD item older than 365 days. The allocated client and matter (with their names) are set from
   * the request of an allocation until it is rejected or undone; {@code resolutionStatus} is the
   * status of the pending or approved resolution row ({@code AWAITING_APPROVAL} / {@code
   * APPROVED}), null when there is none.
   */
  public record UnidentifiedDepositResponse(
      UUID id,
      UUID trustAccountId,
      LocalDate receivedDate,
      String bankDescription,
      String bankReference,
      BigDecimal amount,
      String status,
      int ageDays,
      boolean olderThanOneYear,
      UUID allocatedClientId,
      String allocatedClientName,
      UUID allocatedMatterId,
      String allocatedMatterName,
      UUID resolutionTransactionId,
      String resolutionStatus,
      LocalDate resolvedDate,
      UUID bankStatementLineId,
      String notes) {}

  /**
   * Request an allocation to a client and matter (§16.3.6, §16.4.2). {@code allocationDate} — the
   * day the firm identified the owner (OL-6) — defaults to today and must lie from the item's
   * received date to today. {@code notes} go to the audit trail.
   */
  public record AllocateRequest(
      @NotNull UUID clientId,
      @NotNull UUID matterId,
      LocalDate allocationDate,
      @Size(max = 2000) String notes) {}

  /**
   * Request payment to the Fund (§16.3.6): the payment's own reference and date (from the item's
   * received date to today). When an item may be paid over is not enforced (OL-4).
   */
  public record PayToFundRequest(
      @NotBlank @Size(max = 200) String reference,
      @NotNull LocalDate transactionDate,
      @Size(max = 2000) String notes) {}

  /**
   * The statement line's values an item copies (§16.3.6): its id, date (the received date),
   * description and reference. Owned here so this package never depends on the {@code
   * reconciliation} package's entity.
   */
  public record StatementCredit(
      UUID lineId, LocalDate receivedDate, String bankDescription, String bankReference) {}

  /** The item and the row that opened it. */
  public record Opened(UnidentifiedDeposit item, TrustTransaction depositRow) {}

  // --- Service Methods ---

  /** The trust account's items, oldest first; {@code status} filters when given (§16.4.2). */
  @Transactional(readOnly = true)
  public Page<UnidentifiedDepositResponse> list(
      UUID trustAccountId, UnidentifiedDepositStatus status, Pageable pageable) {
    moduleGuard.requireModule(MODULE_ID);
    if (!trustAccountRepository.existsById(trustAccountId)) {
      throw new ResourceNotFoundException("TrustAccount", trustAccountId);
    }
    // The order is fixed in the finder; any client sort is dropped.
    Pageable unsorted =
        pageable.isPaged()
            ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
            : Pageable.unpaged();
    var page =
        status == null
            ? depositRepository.findByTrustAccountIdOrderByReceivedDateAscCreatedAtAscIdAsc(
                trustAccountId, unsorted)
            : depositRepository
                .findByTrustAccountIdAndStatusOrderByReceivedDateAscCreatedAtAscIdAsc(
                    trustAccountId, status, unsorted);
    var names = namesFor(page.getContent());
    var today = LocalDate.now(clock);
    return page.map(item -> toResponse(item, today, names));
  }

  /**
   * Opens the item for a line being recorded as {@code UNIDENTIFIED_DEPOSIT} (§16.3.5, §16.2.5):
   * the item is saved (and flushed — the two tables reference each other through bare ids, so
   * Hibernate cannot order the inserts) first so the row can carry its id ({@code
   * chk_trust_txn_unidentified_item}); {@code draft} — the unsaved row, already on the account's
   * unidentified-deposits ledger and linked to the line — posts {@code RECORDED} through {@link
   * TrustPostingService#record}; then the item records its opening row. {@code MANDATORY}: runs
   * inside {@code StatementLinePostingService.recordFromLine} (line and statement locked).
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public Opened open(
      StatementCredit line, UUID creditorAccountId, TrustTransaction draft, String notes) {
    moduleGuard.requireModule(MODULE_ID);
    var item =
        depositRepository.saveAndFlush(
            new UnidentifiedDeposit(
                draft.getTrustAccountId(),
                creditorAccountId,
                line.lineId(),
                line.receivedDate(),
                line.bankDescription(),
                line.bankReference(),
                draft.getAmount(),
                notes == null || notes.isBlank() ? null : notes));
    draft.setUnidentifiedDepositId(item.getId());
    var row = postingService.record(draft);
    item.attachDepositTransaction(row.getId());
    depositRepository.save(item);

    var details = new LinkedHashMap<String, Object>();
    details.put("amount", item.getAmount().toPlainString());
    details.put("received_date", item.getReceivedDate().toString());
    details.put("trust_account_id", item.getTrustAccountId().toString());
    details.put("bank_statement_line_id", item.getBankStatementLineId().toString());
    details.put("deposit_transaction_id", row.getId().toString());
    audit("unidentified_deposit.opened", item, details);
    return new Opened(item, row);
  }

  /**
   * The unmatch hook, before anything is reversed (lock order line → item → transactions →
   * statement): locks the item a {@code RECORDED} line opened — found by the line's primary row —
   * and refuses unless it is HELD with no pending or approved resolution (400 "Undo the allocation
   * first"; a pending payment to the Fund is refused the same way). {@code MANDATORY}: runs inside
   * {@code StatementLinePostingService.unmatch}.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public UnidentifiedDeposit lockHeldForUnmatch(UUID depositTransactionId) {
    moduleGuard.requireModule(MODULE_ID);
    var item =
        depositRepository
            .findByDepositTransactionIdForUpdate(depositTransactionId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No unidentified deposit was opened by transaction "
                            + depositTransactionId));
    if (!item.isHeldWithoutResolution()) {
      throw new InvalidStateException(UNMATCH_TITLE, "Undo the allocation first");
    }
    return item;
  }

  /**
   * After the opening row's contra posted: the item is {@code REVERSED}, resolved on the contra's
   * date (= the line's date); audited. {@code MANDATORY}: runs inside {@code
   * StatementLinePostingService.unmatch}, after {@link #lockHeldForUnmatch}.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void markReversedByUnmatch(UnidentifiedDeposit item, TrustTransaction contra) {
    moduleGuard.requireModule(MODULE_ID);
    item.markReversed(contra.getTransactionDate());
    depositRepository.save(item);

    var details = new LinkedHashMap<String, Object>();
    details.put("amount", item.getAmount().toPlainString());
    details.put("received_date", item.getReceivedDate().toString());
    details.put("trust_account_id", item.getTrustAccountId().toString());
    details.put("bank_statement_line_id", item.getBankStatementLineId().toString());
    details.put("deposit_transaction_id", item.getDepositTransactionId().toString());
    details.put("reversal_transaction_id", contra.getId().toString());
    details.put("resolved_date", item.getResolvedDate().toString());
    audit("unidentified_deposit.reversed", item, details);
  }

  /**
   * Requests an allocation of a HELD item to a client and matter (§16.3.6, 647D): one {@code
   * UNIDENT_ALLOC_OUT} row on the unidentified-deposits ledger, {@code AWAITING_APPROVAL}, dated
   * the allocation date, reference {@code UID-{8 hex}}; the item stays HELD with the pending row
   * and the requested client and matter. Nothing moves until another member approves it. Lock
   * order: the item, then (after every refusal) the client's lock anchor.
   */
  @Transactional
  public UnidentifiedDepositResponse allocate(UUID depositId, AllocateRequest request) {
    moduleGuard.requireModule(MODULE_ID);
    var item = lockItem(depositId);
    requireResolvable(item, ALLOCATE_TITLE);
    var client =
        customerRepository
            .findById(request.clientId())
            .orElseThrow(
                () -> new ResourceNotFoundException(EntityNoun.CLIENT, request.clientId()));
    clientMatterRule.requireClientMatter(client, request.matterId());
    var today = LocalDate.now(clock);
    var date =
        requireDateInWindow(
            request.allocationDate() != null ? request.allocationDate() : today,
            item,
            today,
            ALLOCATE_TITLE,
            "allocation");
    var memberId = RequestScopes.requireMemberId();
    postingService.lockClient(item.getTrustAccountId(), client.getId());
    var matter =
        projectRepository
            .findById(request.matterId())
            .orElseThrow(
                () -> new ResourceNotFoundException(EntityNoun.MATTER, request.matterId()));

    var draft =
        new TrustTransaction(
            item.getTrustAccountId(),
            ALLOC_OUT,
            item.getAmount(),
            null,
            null,
            null,
            reference(item),
            "To " + client.getName() + " — " + matter.getName() + ": " + receivedPhrase(item),
            date,
            "AWAITING_APPROVAL",
            memberId);
    draft.setCreditorAccountId(item.getCreditorAccountId()); // chk_trust_txn_ledger_target
    draft.setUnidentifiedDepositId(item.getId()); // chk_trust_txn_unidentified_item
    var pending = postingService.recordAwaitingApproval(draft);
    item.requestResolution(pending.getId(), client.getId(), matter.getId());
    depositRepository.save(item);

    var details = requestDetails(item, pending, request.notes());
    details.put("allocation_date", date.toString());
    audit(
        "unidentified_deposit.allocation_requested",
        item,
        AuditDetails.matterScoped(matter.getId(), client.getId(), details));
    publishAwaitingApproval(pending, memberId);
    return toResponse(item);
  }

  /**
   * Requests payment of a HELD item to the Fund (section 87(4), §16.3.6, 647D): one {@code
   * UNIDENT_FUND_PAY} row, {@code AWAITING_APPROVAL}, with the payment's own reference and date;
   * the item stays HELD with the pending row. Nothing enforces <i>when</i> an item may be paid over
   * (OL-4) — the dialog shows its age and warns.
   */
  @Transactional
  public UnidentifiedDepositResponse payToFund(UUID depositId, PayToFundRequest request) {
    moduleGuard.requireModule(MODULE_ID);
    var item = lockItem(depositId);
    requireResolvable(item, PAY_TITLE);
    if (request.reference() == null || request.reference().isBlank()) {
      throw new InvalidStateException(PAY_TITLE, "A payment reference is required");
    }
    if (request.transactionDate() == null) {
      throw new InvalidStateException(PAY_TITLE, "A payment date is required");
    }
    var today = LocalDate.now(clock);
    var date = requireDateInWindow(request.transactionDate(), item, today, PAY_TITLE, "payment");
    var memberId = RequestScopes.requireMemberId();

    var draft =
        new TrustTransaction(
            item.getTrustAccountId(),
            FUND_PAY,
            item.getAmount(),
            null,
            null,
            null,
            request.reference().trim(),
            "To the Legal Practitioners' Fidelity Fund: " + receivedPhrase(item),
            date,
            "AWAITING_APPROVAL",
            memberId);
    draft.setCreditorAccountId(item.getCreditorAccountId());
    draft.setUnidentifiedDepositId(item.getId());
    var pending = postingService.recordAwaitingApproval(draft);
    item.requestResolution(pending.getId(), null, null);
    depositRepository.save(item);

    var details = requestDetails(item, pending, request.notes());
    details.put("transaction_date", date.toString());
    audit("unidentified_deposit.payment_requested", item, details);
    publishAwaitingApproval(pending, memberId);
    return toResponse(item);
  }

  // --- Resolution hooks (UnidentifiedResolutionHooks; called by TrustTransactionService) ---

  /**
   * The resolution row has just posted on final approval. An allocation: the {@code
   * UNIDENT_ALLOC_IN} credit leg is created APPROVED and posted for the client and matter (same
   * date and reference, its approvers inherited), described "Deposit received {date} — {bank
   * description}"; the item is ALLOCATED. A payment to the Fund: the item is PAID_TO_FUND. Lock
   * order: the resolution row (caller), the item, the client's lock anchor.
   */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void onResolutionApproved(TrustTransaction resolution) {
    moduleGuard.requireModule(MODULE_ID);
    var item = lockResolvedItem(resolution, UnidentifiedDepositStatus.HELD);
    var date = resolution.getTransactionDate();
    var details = new LinkedHashMap<String, Object>();
    details.put("amount", item.getAmount().toPlainString());
    details.put("resolution_transaction_id", resolution.getId().toString());

    if (ALLOC_OUT.equals(resolution.getTransactionType())) {
      var customerId = item.getAllocatedCustomerId();
      var projectId = item.getAllocatedProjectId();
      postingService.lockClient(item.getTrustAccountId(), customerId);
      var draft =
          new TrustTransaction(
              item.getTrustAccountId(),
              ALLOC_IN,
              item.getAmount(),
              customerId,
              projectId,
              null,
              resolution.getReference(),
              "Deposit received "
                  + RECEIVED.format(item.getReceivedDate())
                  + " — "
                  + item.getBankDescription(),
              date,
              "APPROVED",
              resolution.getRecordedBy());
      draft.setUnidentifiedDepositId(item.getId());
      var credit = postingService.recordWithPair(draft, resolution);
      item.markAllocated(credit.getId(), date);
      depositRepository.save(item);

      details.put("allocation_credit_transaction_id", credit.getId().toString());
      details.put("resolved_date", date.toString());
      audit(
          "unidentified_deposit.allocated",
          item,
          AuditDetails.matterScoped(projectId, customerId, details));
      // The approval event names the NON_CLIENT ALLOC_OUT, which no client sees: the credit leg's
      // own event re-projects the client's matter in the portal (the email channel mails DEPOSIT
      // only, so no client email).
      eventPublisher.publishEvent(
          TrustTransactionRecordedEvent.recorded(
              credit.getId(),
              credit.getTrustAccountId(),
              projectId,
              ALLOC_IN,
              credit.getAmount(),
              date,
              customerId,
              RequestScopes.requireMemberId(),
              RequestScopes.getTenantIdOrNull(),
              RequestScopes.getOrgIdOrNull()));
    } else {
      item.markPaidToFund(date);
      depositRepository.save(item);
      details.put("resolved_date", date.toString());
      audit("unidentified_deposit.paid_to_fund", item, details);
    }
  }

  /** The resolution row was rejected: the item stays HELD, its resolution and ids cleared. */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void onResolutionRejected(TrustTransaction resolution) {
    moduleGuard.requireModule(MODULE_ID);
    var item = lockResolvedItem(resolution, UnidentifiedDepositStatus.HELD);
    var customerId = item.getAllocatedCustomerId();
    var projectId = item.getAllocatedProjectId();
    var details = new LinkedHashMap<String, Object>();
    details.put("amount", item.getAmount().toPlainString());
    details.put("resolution_transaction_id", resolution.getId().toString());
    if (resolution.getRejectionReason() != null) {
      details.put("reason", resolution.getRejectionReason());
    }
    item.clearResolution();
    depositRepository.save(item);

    if (ALLOC_OUT.equals(resolution.getTransactionType())) {
      audit(
          "unidentified_deposit.allocation_rejected",
          item,
          AuditDetails.matterScoped(projectId, customerId, details));
    } else {
      audit("unidentified_deposit.payment_rejected", item, details);
    }
  }

  /**
   * The posted resolution is being reversed (the caller posts the contra(s) next, in the same
   * transaction): the item returns to HELD with its resolved date, resolution and allocated ids
   * cleared. Its rows keep the item's id, so it still reads as allocated / paid between the
   * resolution's date and the reversal's (EQ-4). Returns the {@code UNIDENT_ALLOC_IN} to reverse
   * with an allocation, null for a payment to the Fund.
   */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public UUID onResolutionReversed(TrustTransaction resolution, LocalDate reversalDate) {
    moduleGuard.requireModule(MODULE_ID);
    boolean allocation = ALLOC_OUT.equals(resolution.getTransactionType());
    var item =
        lockResolvedItem(
            resolution,
            allocation
                ? UnidentifiedDepositStatus.ALLOCATED
                : UnidentifiedDepositStatus.PAID_TO_FUND);
    var creditId = item.getAllocationCreditTransactionId();
    var customerId = item.getAllocatedCustomerId();
    var projectId = item.getAllocatedProjectId();
    var details = new LinkedHashMap<String, Object>();
    details.put("amount", item.getAmount().toPlainString());
    details.put("resolution_transaction_id", resolution.getId().toString());
    details.put("resolution_type", resolution.getTransactionType());
    details.put("reversal_date", reversalDate.toString());
    item.reopen();
    depositRepository.save(item);

    if (allocation) {
      details.put("allocation_credit_transaction_id", creditId.toString());
      audit(
          "unidentified_deposit.reopened",
          item,
          AuditDetails.matterScoped(projectId, customerId, details));
      return creditId;
    }
    audit("unidentified_deposit.reopened", item, details);
    return null;
  }

  // --- Helpers ---

  private UnidentifiedDeposit lockItem(UUID depositId) {
    return depositRepository
        .findByIdForUpdate(depositId)
        .orElseThrow(() -> new ResourceNotFoundException("Unidentified deposit", depositId));
  }

  /**
   * The item {@code resolution} resolves, locked; it must name {@code resolution} as its resolution
   * and be in {@code expected} — anything else is a data defect, not a user error.
   */
  private UnidentifiedDeposit lockResolvedItem(
      TrustTransaction resolution, UnidentifiedDepositStatus expected) {
    var depositId = resolution.getUnidentifiedDepositId();
    var item =
        depositRepository
            .findByIdForUpdate(depositId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Resolution " + resolution.getId() + " names no unidentified deposit"));
    if (!resolution.getId().equals(item.getResolutionTransactionId())
        || item.getStatus() != expected) {
      throw new IllegalStateException(
          "Unidentified deposit "
              + item.getId()
              + " ("
              + item.getStatus()
              + ") is not resolved by "
              + resolution.getId());
    }
    return item;
  }

  /** HELD, no pending or approved resolution, on an open account — else 400 under {@code title}. */
  private void requireResolvable(UnidentifiedDeposit item, String title) {
    if (item.getStatus() != UnidentifiedDepositStatus.HELD) {
      throw new InvalidStateException(title, NOT_HELD_DETAIL);
    }
    if (item.getResolutionTransactionId() != null) {
      throw new InvalidStateException(title, PENDING_DETAIL);
    }
    var account =
        trustAccountRepository
            .findById(item.getTrustAccountId())
            .orElseThrow(
                () -> new ResourceNotFoundException("TrustAccount", item.getTrustAccountId()));
    if (TrustAccountStatus.CLOSED == account.getStatus()) {
      throw new InvalidStateException(title, "Trust account is closed");
    }
  }

  /** A resolution is dated from the item's received date to today (OL-6, OL-4 ruling). */
  private static LocalDate requireDateInWindow(
      LocalDate date, UnidentifiedDeposit item, LocalDate today, String title, String noun) {
    if (date.isBefore(item.getReceivedDate()) || date.isAfter(today)) {
      throw new InvalidStateException(
          title,
          "The "
              + noun
              + " date must be from the date the deposit was received ("
              + item.getReceivedDate()
              + ") to today");
    }
    return date;
  }

  /** {@code UID-} and the first 8 hex digits of the item's id, upper case (both legs share it). */
  private static String reference(UnidentifiedDeposit item) {
    return "UID-" + item.getId().toString().substring(0, 8).toUpperCase(Locale.ROOT);
  }

  private static String receivedPhrase(UnidentifiedDeposit item) {
    return "deposit received "
        + RECEIVED.format(item.getReceivedDate())
        + " — "
        + item.getBankDescription();
  }

  private static LinkedHashMap<String, Object> requestDetails(
      UnidentifiedDeposit item, TrustTransaction pending, String notes) {
    var details = new LinkedHashMap<String, Object>();
    details.put("amount", item.getAmount().toPlainString());
    details.put("received_date", item.getReceivedDate().toString());
    details.put("trust_account_id", item.getTrustAccountId().toString());
    details.put("resolution_transaction_id", pending.getId().toString());
    details.put("reference", pending.getReference());
    if (notes != null && !notes.isBlank()) {
      details.put("notes", notes.trim());
    }
    return details;
  }

  /** Approvers get the in-app "requires approval" notification (as for a payment). */
  private void publishAwaitingApproval(TrustTransaction pending, UUID memberId) {
    eventPublisher.publishEvent(
        TrustTransactionApprovalEvent.awaitingApproval(
            pending.getId(),
            pending.getTrustAccountId(),
            null,
            pending.getTransactionType(),
            pending.getAmount(),
            pending.getTransactionDate(),
            null,
            memberId,
            RequestScopes.getTenantIdOrNull(),
            RequestScopes.getOrgIdOrNull()));
  }

  private void audit(String eventType, UnidentifiedDeposit item, Map<String, Object> details) {
    auditService.log(
        AuditEventBuilder.builder()
            .eventType(eventType)
            .entityType("unidentified_deposit")
            .entityId(item.getId())
            .details(details)
            .build());
  }

  private UnidentifiedDepositResponse toResponse(UnidentifiedDeposit item) {
    return toResponse(item, LocalDate.now(clock), namesFor(List.of(item)));
  }

  /** The allocated clients' and matters' names and the resolution rows' statuses, by id. */
  private record Names(
      Map<UUID, String> clients, Map<UUID, String> matters, Map<UUID, String> resolutionStatuses) {}

  /** Batch-loaded for a page: three queries at most, never one per row. */
  private Names namesFor(List<UnidentifiedDeposit> items) {
    return new Names(
        byId(
            ifAny(
                ids(items, UnidentifiedDeposit::getAllocatedCustomerId),
                customerRepository::findByIdIn),
            Customer::getId,
            Customer::getName),
        byId(
            ifAny(
                ids(items, UnidentifiedDeposit::getAllocatedProjectId),
                projectRepository::findByIdIn),
            Project::getId,
            Project::getName),
        byId(
            ifAny(
                ids(items, UnidentifiedDeposit::getResolutionTransactionId),
                transactionRepository::findAllById),
            TrustTransaction::getId,
            TrustTransaction::getStatus));
  }

  private static List<UUID> ids(
      List<UnidentifiedDeposit> items, Function<UnidentifiedDeposit, UUID> id) {
    return items.stream().map(id).filter(Objects::nonNull).distinct().toList();
  }

  /** No query for an empty id list (most pages hold no allocated or pending item). */
  private static <T> List<T> ifAny(List<UUID> ids, Function<List<UUID>, List<T>> load) {
    return ids.isEmpty() ? List.of() : load.apply(ids);
  }

  private static <T> Map<UUID, String> byId(
      List<T> rows, Function<T, UUID> id, Function<T, String> value) {
    var map = new HashMap<UUID, String>();
    for (var row : rows) {
      map.put(id.apply(row), value.apply(row));
    }
    return map;
  }

  private static UnidentifiedDepositResponse toResponse(
      UnidentifiedDeposit d, LocalDate today, Names names) {
    // Clamped: a future-dated statement line can be recorded today (647A), and a negative age
    // must never reach the wire.
    int ageDays = (int) Math.max(0, ChronoUnit.DAYS.between(d.getReceivedDate(), today));
    boolean held = d.getStatus() == UnidentifiedDepositStatus.HELD;
    return new UnidentifiedDepositResponse(
        d.getId(),
        d.getTrustAccountId(),
        d.getReceivedDate(),
        d.getBankDescription(),
        d.getBankReference(),
        d.getAmount(),
        d.getStatus().name(),
        ageDays,
        held && ageDays > ONE_YEAR_DAYS,
        d.getAllocatedCustomerId(),
        names.clients().get(d.getAllocatedCustomerId()),
        d.getAllocatedProjectId(),
        names.matters().get(d.getAllocatedProjectId()),
        d.getResolutionTransactionId(),
        names.resolutionStatuses().get(d.getResolutionTransactionId()),
        d.getResolvedDate(),
        d.getBankStatementLineId(),
        d.getNotes());
  }
}
