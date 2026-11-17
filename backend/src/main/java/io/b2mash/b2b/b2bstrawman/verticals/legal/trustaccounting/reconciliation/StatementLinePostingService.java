package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDeposit;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositService.StatementCredit;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.BankStatementLineResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustPostingService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.TrustTransactionResponse;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Record from statement (Phase 92 / 647A, ADR-380, ADR-381, architecture §16.3.5): the firm
 * confirms what an unmatched bank statement line is, and Kazi posts the matching trust row dated
 * the line's date, linked to the line, which becomes {@code RECORDED}. This service is the only way
 * a bank-originated type ({@code origin = BANK}) comes into existence; such rows post at once with
 * no approval (the bank has already moved the money). Unmatching a recorded line reverses what it
 * created as a same-date contra through the normal reversal path.
 *
 * <p>647B (ADR-376): a credit recorded as {@code UNIDENTIFIED_DEPOSIT} also opens an item ({@link
 * UnidentifiedDepositService#open}) that its row names; unmatching that line is allowed only while
 * the item is HELD with no pending resolution, and reverses the item with its row.
 *
 * <p>Dependency direction: {@link TrustReconciliationService} calls this service (for {@code
 * unmatch}); this service never calls back — it builds its line response through the static {@link
 * BankStatementLineResponse#of}.
 */
@Service
public class StatementLinePostingService {

  private static final String MODULE_ID = "trust_accounting";
  private static final String RECORD_TITLE = "Cannot record statement line";

  private final BankStatementLineRepository lineRepository;
  private final BankStatementRepository statementRepository;
  private final TrustAccountRepository trustAccountRepository;
  private final TrustCreditorAccountRepository creditorAccountRepository;
  private final TrustTransactionRepository transactionRepository;
  private final TrustPostingService postingService;
  private final TrustTransactionService transactionService;
  private final UnidentifiedDepositService unidentifiedDepositService;
  private final VerticalModuleGuard moduleGuard;
  private final AuditService auditService;

  public StatementLinePostingService(
      BankStatementLineRepository lineRepository,
      BankStatementRepository statementRepository,
      TrustAccountRepository trustAccountRepository,
      TrustCreditorAccountRepository creditorAccountRepository,
      TrustTransactionRepository transactionRepository,
      TrustPostingService postingService,
      TrustTransactionService transactionService,
      UnidentifiedDepositService unidentifiedDepositService,
      VerticalModuleGuard moduleGuard,
      AuditService auditService) {
    this.lineRepository = lineRepository;
    this.statementRepository = statementRepository;
    this.trustAccountRepository = trustAccountRepository;
    this.creditorAccountRepository = creditorAccountRepository;
    this.transactionRepository = transactionRepository;
    this.postingService = postingService;
    this.transactionService = transactionService;
    this.unidentifiedDepositService = unidentifiedDepositService;
    this.moduleGuard = moduleGuard;
    this.auditService = auditService;
  }

  // --- DTO Records ---

  /**
   * What the firm confirms for a line. {@code chargeRecoverable} and {@code chargeVatAmount} are
   * required for {@code BANK_CHARGE} and refused on every other classification ({@code
   * chargeVatAmount} at most two decimals: numeric(15,2)). {@code notes} is kept in the audit (no
   * column on the line); an unidentified deposit also keeps it on its item (647B). 648B adds the
   * section 86(4) interest fields.
   */
  public record RecordFromLineRequest(
      @NotNull StatementLineClassification classification,
      Boolean chargeRecoverable,
      @Digits(integer = 13, fraction = 2, message = "The VAT amount has at most two decimal places")
          BigDecimal chargeVatAmount,
      @Size(max = 2000) String notes) {}

  /**
   * The recorded line and the rows it created. {@code unidentifiedDepositId} is the item an {@code
   * UNIDENTIFIED_DEPOSIT} opened (647B), null otherwise; {@code investmentInterestId} (648B) is
   * always null until then — the spec's response shape (§16.4.3).
   */
  public record RecordFromLineResponse(
      BankStatementLineResponse line,
      List<TrustTransactionResponse> transactions,
      UUID unidentifiedDepositId,
      UUID investmentInterestId) {}

  // --- Service Methods ---

  /**
   * Records an {@code UNMATCHED} line as {@code request.classification()}: one posted {@code
   * NON_CLIENT} row on the account's ledger for the type (the Fund interest-and-charges ledger, or
   * the unidentified-deposits ledger), {@code amount = |line|}, dated the line's date, linked to
   * the line; the line becomes {@code RECORDED} and the statement's {@code matched_count} rises by
   * one. An {@code UNIDENTIFIED_DEPOSIT} also opens a HELD item, saved before the row so the row
   * names it (647B), and is refused on an investment account. Every check runs before the first
   * write, so a refusal moves nothing. No sufficiency check: a Fund ledger in debit is allowed
   * (ADR-381).
   */
  @Transactional
  public RecordFromLineResponse recordFromLine(UUID lineId, RecordFromLineRequest request) {
    moduleGuard.requireModule(MODULE_ID);
    var memberId = RequestScopes.requireMemberId();

    // Rule 1 — lock, then check state (the lock serialises two members recording one line).
    var line =
        lineRepository
            .findByIdForUpdate(lineId)
            .orElseThrow(() -> new ResourceNotFoundException("BankStatementLine", lineId));
    if (!"UNMATCHED".equals(line.getMatchStatus())) {
      throw new InvalidStateException(
          RECORD_TITLE, "This statement line is already matched, recorded or excluded");
    }
    // Line, then statement: the statement lock serialises the matched_count update.
    var statement =
        statementRepository
            .findByIdForUpdate(line.getBankStatementId())
            .orElseThrow(
                () -> new ResourceNotFoundException("BankStatement", line.getBankStatementId()));
    var account =
        trustAccountRepository
            .findById(statement.getTrustAccountId())
            .orElseThrow(
                () -> new ResourceNotFoundException("TrustAccount", statement.getTrustAccountId()));
    if (TrustAccountStatus.CLOSED == account.getStatus()) {
      throw new InvalidStateException(RECORD_TITLE, "Trust account is closed");
    }

    // Rule 2 — the classification agrees with the line's sign; then per-classification rules.
    // (The section 86(4) refusal of FUND_INTEREST needs the account's investment basis, which
    // arrives in 648C; 648B adds it with INVESTMENT_INTEREST — 647A found.md D1.)
    var classification = request.classification();
    var amount = line.getAmount().abs();
    requireSignAgrees(classification, line.getAmount());
    requireChargeDetail(classification, request, amount);
    if (classification == StatementLineClassification.UNIDENTIFIED_DEPOSIT
        && account.getAccountType() == TrustAccountType.INVESTMENT) {
      throw new InvalidStateException(
          RECORD_TITLE,
          "An unidentified deposit is recorded only on a trust banking account. On an investment"
              + " account, exclude the line and take it up with the bank.");
    }

    // Rule 3 — the row, on the type's creditor ledger, linked to the line before its one save.
    var type = classification.transactionType();
    var creditor =
        creditorAccountRepository
            .findByTrustAccountIdAndAccountType(account.getId(), type.creditorAccountType())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Trust account "
                            + account.getId()
                            + " has no "
                            + type.creditorAccountType()
                            + " account"));
    var row =
        new TrustTransaction(
            account.getId(),
            type.name(),
            amount,
            null,
            null,
            null,
            referenceFor(line),
            line.getDescription(),
            line.getTransactionDate(),
            "RECORDED",
            memberId);
    row.setCreditorAccountId(creditor.getId());
    if (classification == StatementLineClassification.BANK_CHARGE) {
      row.setChargeDetail(request.chargeRecoverable(), request.chargeVatAmount());
    }
    row.setBankStatementLineId(line.getId());
    TrustTransaction saved;
    UUID unidentifiedDepositId = null;
    if (classification == StatementLineClassification.UNIDENTIFIED_DEPOSIT) {
      // The item first, so the row names it (chk_trust_txn_unidentified_item); open posts the row.
      var credit =
          new StatementCredit(
              line.getId(), line.getTransactionDate(), line.getDescription(), line.getReference());
      var opened = unidentifiedDepositService.open(credit, creditor.getId(), row, request.notes());
      saved = opened.depositRow();
      unidentifiedDepositId = opened.item().getId();
    } else {
      saved = postingService.record(row);
    }

    // Rule 4 — the line (status and classification in one save: chk_bank_line_classification).
    line.setMatchStatus("RECORDED");
    line.setClassification(classification.name());
    line.setTrustTransactionId(saved.getId());
    line.setMatchConfidence(null);
    lineRepository.save(line);
    statement.incrementMatched();
    statementRepository.save(statement);

    var created = List.of(saved);
    auditRecorded(line, classification, amount, created, request.notes());

    return new RecordFromLineResponse(
        BankStatementLineResponse.of(line, created.stream().map(TrustTransaction::getId).toList()),
        transactionService.toResponses(created),
        unidentifiedDepositId,
        null);
  }

  /**
   * Unmatches a {@code RECORDED} line (§16.3.5, §16.3.11): every posted row it created is reversed
   * by a contra dated the <b>line's</b> date, the rows are unlinked, and the line returns to {@code
   * UNMATCHED} with no classification (its suggestion is kept); {@code matched_count} falls by one.
   * A row already reversed through the generic endpoint has nothing left to reverse — the line is
   * still reset. {@link TrustReconciliationService#unmatch} routes only recorded lines here.
   *
   * <p>An {@code UNIDENTIFIED_DEPOSIT} line (647B): its item is locked after the line and before
   * any transaction (lock order line → item → transactions → statement) and must be HELD with no
   * pending resolution (else 400 "Undo the allocation first", nothing moves); the opening row's
   * contra carries the item, and the item becomes {@code REVERSED} on the line's date.
   */
  @Transactional
  public void unmatch(UUID lineId) {
    moduleGuard.requireModule(MODULE_ID);
    var line =
        lineRepository
            .findByIdForUpdate(lineId)
            .orElseThrow(() -> new ResourceNotFoundException("BankStatementLine", lineId));
    if (!"RECORDED".equals(line.getMatchStatus())) {
      throw new InvalidStateException(
          "Cannot unmatch statement line", "Only a line recorded from the statement is reversed");
    }
    var classification = line.getClassification();

    // 0. An unidentified deposit: lock its item and refuse while a resolution is pending (647B).
    UnidentifiedDeposit item = null;
    if (StatementLineClassification.UNIDENTIFIED_DEPOSIT.name().equals(classification)) {
      item = unidentifiedDepositService.lockHeldForUnmatch(line.getTrustTransactionId());
    }

    // 1. Reverse first: reverseForUnmatch finds the originals by their link to the line.
    var contras = transactionService.reverseForUnmatch(lineId);

    // 2. Unlink every row still pointing at the line (contras are built without a link).
    for (var row : transactionRepository.findByBankStatementLineId(lineId)) {
      postingService.linkBankLine(row, null);
    }

    // 3. The line and the statement.
    line.setMatchStatus("UNMATCHED");
    line.setClassification(null);
    line.setTrustTransactionId(null);
    line.setMatchConfidence(null);
    lineRepository.save(line);
    var statement =
        statementRepository
            .findByIdForUpdate(line.getBankStatementId())
            .orElseThrow(
                () -> new ResourceNotFoundException("BankStatement", line.getBankStatementId()));
    statement.decrementMatched();
    statementRepository.save(statement);

    // 3b. The item follows its opening row: REVERSED on the contra's date (= the line's date).
    if (item != null) {
      var itemId = item.getId();
      var depositTransactionId = item.getDepositTransactionId();
      var contra =
          contras.stream()
              .filter(c -> depositTransactionId.equals(c.getReversalOf()))
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "The opening row of unidentified deposit " + itemId + " has no contra"));
      unidentifiedDepositService.markReversedByUnmatch(item, contra);
    }

    // 4. Audit — the reversed originals' ids (each contra already logs its own reversal).
    var details = new LinkedHashMap<String, Object>();
    details.put("classification", classification);
    details.put(
        "reversed_transaction_ids",
        contras.stream().map(contra -> contra.getReversalOf().toString()).toList());
    details.put("statement_id", line.getBankStatementId().toString());
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("bank_statement_line.unmatched")
            .entityType("bank_statement_line")
            .entityId(lineId)
            .details(details)
            .build());
  }

  // --- Helpers ---

  private void auditRecorded(
      BankStatementLine line,
      StatementLineClassification classification,
      BigDecimal amount,
      List<TrustTransaction> created,
      String notes) {
    var lineDetails = new LinkedHashMap<String, Object>();
    lineDetails.put("classification", classification.name());
    lineDetails.put(
        "transaction_ids", created.stream().map(row -> row.getId().toString()).toList());
    lineDetails.put("amount", amount.toPlainString());
    lineDetails.put("statement_id", line.getBankStatementId().toString());
    if (notes != null && !notes.isBlank()) {
      lineDetails.put("notes", notes);
    }
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("bank_statement_line.recorded")
            .entityType("bank_statement_line")
            .entityId(line.getId())
            .details(lineDetails)
            .build());

    for (var row : created) {
      var type = classification.transactionType();
      Map<String, Object> rowDetails = new LinkedHashMap<>();
      rowDetails.put("amount", row.getAmount().toPlainString());
      rowDetails.put("reference", row.getReference());
      rowDetails.put("transaction_date", row.getTransactionDate().toString());
      rowDetails.put("bank_statement_line_id", line.getId().toString());
      rowDetails.put("creditor_account_id", row.getCreditorAccountId().toString());
      if (row.getUnidentifiedDepositId() != null) {
        rowDetails.put("unidentified_deposit_id", row.getUnidentifiedDepositId().toString());
      }
      if (row.getChargeRecoverable() != null) {
        rowDetails.put("recoverable", row.getChargeRecoverable().toString());
        rowDetails.put("vat_amount", row.getChargeVatAmount().toPlainString());
      }
      auditService.log(
          AuditEventBuilder.builder()
              .eventType(type.auditKey() + ".recorded")
              .entityType("trust_transaction")
              .entityId(row.getId())
              .details(rowDetails)
              .build());
    }
  }

  /** The line's reference, or {@code STMT-{lineNumber}-{yyyyMMdd}} when it has none. */
  private static String referenceFor(BankStatementLine line) {
    var reference = line.getReference();
    if (reference != null && !reference.isBlank()) {
      return reference;
    }
    return "STMT-"
        + line.getLineNumber()
        + "-"
        + line.getTransactionDate().format(DateTimeFormatter.BASIC_ISO_DATE);
  }

  private static void requireSignAgrees(
      StatementLineClassification classification, BigDecimal lineAmount) {
    int sign = lineAmount.signum();
    if (sign == 0) {
      throw new InvalidStateException(
          RECORD_TITLE, "A zero-amount statement line cannot be recorded");
    }
    if (sign != classification.lineSign()) {
      throw new InvalidStateException(
          "Classification does not match the line",
          classification.label()
              + " is recorded from a "
              + (classification.lineSign() > 0 ? "credit" : "debit")
              + " line; this line is a "
              + (sign > 0 ? "credit" : "debit"));
    }
  }

  private static void requireChargeDetail(
      StatementLineClassification classification,
      RecordFromLineRequest request,
      BigDecimal chargeAmount) {
    boolean anyChargeField =
        request.chargeRecoverable() != null || request.chargeVatAmount() != null;
    if (classification != StatementLineClassification.BANK_CHARGE) {
      if (anyChargeField) {
        throw new InvalidStateException(
            RECORD_TITLE, "Recoverable and VAT apply only to a bank charge");
      }
      return;
    }
    if (request.chargeRecoverable() == null || request.chargeVatAmount() == null) {
      throw new InvalidStateException(
          RECORD_TITLE, "A bank charge needs whether it is recoverable and its VAT amount");
    }
    if (request.chargeVatAmount().signum() < 0
        || request.chargeVatAmount().compareTo(chargeAmount) >= 0) {
      throw new InvalidStateException(
          RECORD_TITLE, "The VAT amount must be zero or more and less than the charge");
    }
  }
}
