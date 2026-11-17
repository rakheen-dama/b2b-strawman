package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.BankStatementLine;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustPostingService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unidentified deposits (Phase 92, ADR-376, §16.3.6) — the "hold" half (647B): an item is opened
 * when a statement line is recorded as {@code UNIDENTIFIED_DEPOSIT}, listed oldest first with its
 * age, and reversed when its line is unmatched. Allocation to a client and matter and payment to
 * the Fund are 647D.
 *
 * <p>Dependency direction: {@code StatementLinePostingService} calls this service; this service
 * never calls back (it depends on the {@link BankStatementLine} entity type and {@link
 * TrustPostingService} only). Every posting goes through {@link TrustPostingService}.
 */
@Service
public class UnidentifiedDepositService {

  private static final String MODULE_ID = "trust_accounting";
  private static final String UNMATCH_TITLE = "Cannot unmatch statement line";

  /** An item held longer than this many days is flagged (§16.8.4 band "over one year"). */
  private static final int ONE_YEAR_DAYS = 365;

  private final UnidentifiedDepositRepository depositRepository;
  private final TrustAccountRepository trustAccountRepository;
  private final TrustPostingService postingService;
  private final VerticalModuleGuard moduleGuard;
  private final AuditService auditService;
  private final Clock clock;

  public UnidentifiedDepositService(
      UnidentifiedDepositRepository depositRepository,
      TrustAccountRepository trustAccountRepository,
      TrustPostingService postingService,
      VerticalModuleGuard moduleGuard,
      AuditService auditService,
      Clock clock) {
    this.depositRepository = depositRepository;
    this.trustAccountRepository = trustAccountRepository;
    this.postingService = postingService;
    this.moduleGuard = moduleGuard;
    this.auditService = auditService;
    this.clock = clock;
  }

  // --- DTO Records ---

  /**
   * One item (§16.3.6). {@code ageDays} counts from the received date to today for every item;
   * {@code olderThanOneYear} flags only a HELD item older than 365 days. Allocation and resolution
   * fields are null until 647D ({@code allocatedClientName}, {@code allocatedMatterName} and {@code
   * resolutionStatus} are filled there).
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
    var today = LocalDate.now(clock);
    var page =
        status == null
            ? depositRepository.findByTrustAccountIdOrderByReceivedDateAscCreatedAtAscIdAsc(
                trustAccountId, unsorted)
            : depositRepository
                .findByTrustAccountIdAndStatusOrderByReceivedDateAscCreatedAtAscIdAsc(
                    trustAccountId, status, unsorted);
    return page.map(item -> toResponse(item, today));
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
      BankStatementLine line, UUID creditorAccountId, TrustTransaction draft, String notes) {
    moduleGuard.requireModule(MODULE_ID);
    var item =
        depositRepository.saveAndFlush(
            new UnidentifiedDeposit(
                draft.getTrustAccountId(),
                creditorAccountId,
                line.getId(),
                line.getTransactionDate(),
                line.getDescription(),
                line.getReference(),
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

  // --- Helpers ---

  private void audit(String eventType, UnidentifiedDeposit item, Map<String, Object> details) {
    auditService.log(
        AuditEventBuilder.builder()
            .eventType(eventType)
            .entityType("unidentified_deposit")
            .entityId(item.getId())
            .details(details)
            .build());
  }

  private static UnidentifiedDepositResponse toResponse(UnidentifiedDeposit d, LocalDate today) {
    int ageDays = (int) ChronoUnit.DAYS.between(d.getReceivedDate(), today);
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
        null, // allocatedClientName — 647D
        d.getAllocatedProjectId(),
        null, // allocatedMatterName — 647D
        d.getResolutionTransactionId(),
        null, // resolutionStatus — 647D
        d.getResolvedDate(),
        d.getBankStatementLineId(),
        d.getNotes());
  }
}
