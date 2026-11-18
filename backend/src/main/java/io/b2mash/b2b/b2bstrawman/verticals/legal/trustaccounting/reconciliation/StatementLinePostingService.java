package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountSection;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.TrustCreditorAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDeposit;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositService.StatementCredit;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.InterestPresentation;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentInterest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService;
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
import java.util.LinkedHashMap;
import java.util.List;
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
 * <p>648B (ADR-379): a credit recorded as {@code INVESTMENT_INTEREST} is section 86(4) interest on
 * a named investment held in the account — delegated, before any lock is taken here, to {@link
 * TrustInvestmentService#recordInterestFromLine} (lock order line → investment → statement);
 * unmatching it locks the entry through {@link TrustInvestmentService#lockEntryForUnmatch} after
 * the line and before any transaction, is refused once the investment is withdrawn, and marks the
 * entry REVERSED. {@code FUND_INTEREST} is refused on a section 86(4) account.
 *
 * <p>Dependency direction: {@link TrustReconciliationService} calls this service (for {@code
 * unmatch}); this service never calls back — it builds its line response through the static {@link
 * BankStatementLineResponse#of}. It calls {@link TrustInvestmentService} (one way); the line rules
 * both share are the static {@link StatementLines}.
 */
@Service
public class StatementLinePostingService {

  private static final String MODULE_ID = "trust_accounting";
  private static final String RECORD_TITLE = StatementLines.RECORD_TITLE;

  private final BankStatementLineRepository lineRepository;
  private final BankStatementRepository statementRepository;
  private final TrustAccountRepository trustAccountRepository;
  private final TrustCreditorAccountRepository creditorAccountRepository;
  private final TrustTransactionRepository transactionRepository;
  private final TrustPostingService postingService;
  private final TrustTransactionService transactionService;
  private final UnidentifiedDepositService unidentifiedDepositService;
  private final TrustInvestmentService investmentService;
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
      TrustInvestmentService investmentService,
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
    this.investmentService = investmentService;
    this.moduleGuard = moduleGuard;
    this.auditService = auditService;
  }

  // --- DTO Records ---

  /**
   * What the firm confirms for a line. {@code chargeRecoverable} and {@code chargeVatAmount} are
   * required for {@code BANK_CHARGE} and refused on every other classification ({@code
   * chargeVatAmount} at most two decimals: numeric(15,2)). {@code notes} is kept in the audit (no
   * column on the line); an unidentified deposit also keeps it on its item (647B). {@code
   * investmentId}, {@code grossAmount}, {@code fundShareAmount} and {@code presentation} (648B) are
   * for {@code INVESTMENT_INTEREST} only — the investment and presentation required, the gross
   * required for {@code NET} (for {@code GROSS} it is the line), the Fund share optional (computed
   * when absent) — and refused on every other classification.
   */
  public record RecordFromLineRequest(
      @NotNull StatementLineClassification classification,
      Boolean chargeRecoverable,
      @Digits(integer = 13, fraction = 2, message = "The VAT amount has at most two decimal places")
          BigDecimal chargeVatAmount,
      UUID investmentId,
      BigDecimal grossAmount,
      BigDecimal fundShareAmount,
      InterestPresentation presentation,
      @Size(max = 2000) String notes) {}

  /**
   * The recorded line and the rows it created. {@code unidentifiedDepositId} is the item an {@code
   * UNIDENTIFIED_DEPOSIT} opened (647B), null otherwise; {@code investmentInterestId} is the
   * section 86(4) interest entry an {@code INVESTMENT_INTEREST} recorded (648B), null otherwise.
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

    // 648B: section 86(4) interest is the investment's — delegated before this service locks
    // anything (lock order line → investment → statement is TrustInvestmentService's).
    if (request.classification() == StatementLineClassification.INVESTMENT_INTEREST) {
      return recordInvestmentInterest(lineId, request);
    }
    if (request.investmentId() != null
        || request.grossAmount() != null
        || request.fundShareAmount() != null
        || request.presentation() != null) {
      throw new InvalidStateException(
          RECORD_TITLE,
          "Investment, gross and presentation apply only to section 86(4) investment interest");
    }

    // Rule 1 — lock, then check state (the lock serialises two members recording one line).
    var line =
        lineRepository
            .findByIdForUpdate(lineId)
            .orElseThrow(() -> new ResourceNotFoundException("BankStatementLine", lineId));
    StatementLines.requireUnmatched(line);
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
    var classification = request.classification();
    var amount = line.getAmount().abs();
    StatementLines.requireSignAgrees(classification, line.getAmount());
    requireChargeDetail(classification, request, amount);
    // 647A found.md D1 (owner 648B): Fund interest is not recorded on a section 86(4) account —
    // the line is routed to the classification that knows the 95/5 split.
    if (classification == StatementLineClassification.FUND_INTEREST
        && account.getSection() == TrustAccountSection.S86_4) {
      throw new InvalidStateException(
          RECORD_TITLE,
          "Interest on a section 86(4) investment is recorded against the investment — choose"
              + " Section 86(4) investment interest");
    }
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
            StatementLines.referenceFor(line),
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
    StatementLines.markRecorded(line, statement, classification, saved);
    lineRepository.save(line);
    statementRepository.save(statement);

    var created = List.of(saved);
    StatementLines.auditRecorded(
        auditService, line, classification, amount, created, request.notes());

    return new RecordFromLineResponse(
        BankStatementLineResponse.of(line, created.stream().map(TrustTransaction::getId).toList()),
        transactionService.toResponses(created),
        unidentifiedDepositId,
        null);
  }

  /**
   * {@code INVESTMENT_INTEREST} (648B, §16.3.5, §16.3.8): the investment and presentation are
   * required and the charge fields refused; {@link TrustInvestmentService#recordInterestFromLine}
   * does the rest (rules 1–4 included) and this maps its result into the record response.
   */
  private RecordFromLineResponse recordInvestmentInterest(
      UUID lineId, RecordFromLineRequest request) {
    if (request.chargeRecoverable() != null || request.chargeVatAmount() != null) {
      throw new InvalidStateException(
          RECORD_TITLE, "Recoverable and VAT apply only to a bank charge");
    }
    if (request.investmentId() == null || request.presentation() == null) {
      throw new InvalidStateException(
          RECORD_TITLE,
          "Section 86(4) investment interest needs the investment and whether the bank credited it"
              + " gross or net of the Fund's 5%");
    }
    var recorded =
        investmentService.recordInterestFromLine(
            lineId,
            request.investmentId(),
            request.grossAmount(),
            request.fundShareAmount(),
            request.presentation(),
            request.notes());
    return new RecordFromLineResponse(
        BankStatementLineResponse.of(
            recorded.line(), recorded.rows().stream().map(TrustTransaction::getId).toList()),
        transactionService.toResponses(recorded.rows()),
        null,
        recorded.investmentInterestId());
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
   *
   * <p>An {@code INVESTMENT_INTEREST} line (648B): the investment row, then its entry, are locked
   * after the line and before any transaction (lock order line → investment → entry → transactions
   * → statement); refused once the investment is withdrawn (nothing moves); the entry becomes
   * {@code REVERSED} with its rows.
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
    // 0b. Section 86(4) interest: lock its investment and entry; refused once withdrawn (648B).
    TrustInvestmentInterest interest = null;
    if (StatementLineClassification.INVESTMENT_INTEREST.name().equals(classification)) {
      interest = investmentService.lockEntryForUnmatch(lineId);
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
    // Refused once the statement is RECONCILED (D-14); the refusal rolls back the reversals above.
    StatementLines.requireNotReconciled(statement);
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
    // 3c. The interest entry follows its rows (648B).
    if (interest != null) {
      investmentService.markReversedByUnmatch(interest, contras);
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
