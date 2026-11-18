package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The record-from-statement rules every recording path shares (§16.3.5 rules 1–4, 647A): {@link
 * StatementLinePostingService} for the Fund, charge and unidentified classifications, and {@code
 * investment.TrustInvestmentService} for section 86(4) interest (648B — it owns that path so the
 * two services never depend on each other, OQ-5). Stateless: no bean, no dependency direction.
 */
public final class StatementLines {

  /** The title of every refusal about the line itself. */
  public static final String RECORD_TITLE = "Cannot record statement line";

  private StatementLines() {}

  /** Rule 1: only an {@code UNMATCHED} line is recorded (the caller holds the line's lock). */
  public static void requireUnmatched(BankStatementLine line) {
    if (!"UNMATCHED".equals(line.getMatchStatus())) {
      throw new InvalidStateException(
          RECORD_TITLE, "This statement line is already matched, recorded or excluded");
    }
  }

  /**
   * An unmatch (a matched line's or a recorded line's) is refused once the line's statement is
   * {@code RECONCILED}: its reconciliation is complete, and unmatching would leave a line of a
   * completed reconciliation unresolved (649C, ruling D-14). Call on the statement row locked with
   * {@code findByIdForUpdate}, after the line and any transaction (lock order line → transactions →
   * statement), so a concurrent {@code completeReconciliation} — which locks the statement before
   * it reads the gate inputs — is serialised against the unmatch; the refusal rolls back whatever
   * the unmatch had already changed.
   */
  public static void requireNotReconciled(BankStatement statement) {
    if ("RECONCILED".equals(statement.getStatus())) {
      throw new InvalidStateException(
          "Statement already reconciled",
          "This statement's reconciliation is complete, so its lines cannot be unmatched.");
    }
  }

  /** Rule 2: the classification agrees with the line's sign; a zero line is never recorded. */
  public static void requireSignAgrees(
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

  /** Rule 3: the line's reference, or {@code STMT-{lineNumber}-{yyyyMMdd}} when it has none. */
  public static String referenceFor(BankStatementLine line) {
    var reference = line.getReference();
    if (reference != null && !reference.isBlank()) {
      return reference;
    }
    return "STMT-"
        + line.getLineNumber()
        + "-"
        + line.getTransactionDate().format(DateTimeFormatter.BASIC_ISO_DATE);
  }

  /**
   * Rule 4: the line becomes {@code RECORDED} as {@code classification} (status and classification
   * together: {@code chk_bank_line_classification}), its primary row is {@code primaryRow}, and the
   * statement's {@code matched_count} rises by one. The caller saves both.
   */
  public static void markRecorded(
      BankStatementLine line,
      BankStatement statement,
      StatementLineClassification classification,
      TrustTransaction primaryRow) {
    line.setMatchStatus("RECORDED");
    line.setClassification(classification.name());
    line.setTrustTransactionId(primaryRow.getId());
    line.setMatchConfidence(null);
    statement.incrementMatched();
  }

  /**
   * The audit of a recorded line: {@code bank_statement_line.recorded}, then {@code
   * <auditKey>.recorded} for each created row — keyed by the row's own type (648B: a gross interest
   * line creates an {@code INV_INTEREST} and a {@code FUND_INV_SHARE} row), with the row's creditor
   * account, investment, unidentified deposit and charge detail where it has them.
   */
  public static void auditRecorded(
      AuditService auditService,
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
      auditService.log(
          AuditEventBuilder.builder()
              .eventType(TrustTransactionType.of(row.getTransactionType()).auditKey() + ".recorded")
              .entityType("trust_transaction")
              .entityId(row.getId())
              .details(rowDetails(row))
              .build());
    }
  }

  /** The details of a row's {@code <auditKey>.recorded} event. */
  public static Map<String, Object> rowDetails(TrustTransaction row) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("amount", row.getAmount().toPlainString());
    details.put("reference", row.getReference());
    details.put("transaction_date", row.getTransactionDate().toString());
    if (row.getBankStatementLineId() != null) {
      details.put("bank_statement_line_id", row.getBankStatementLineId().toString());
    }
    if (row.getCreditorAccountId() != null) {
      details.put("creditor_account_id", row.getCreditorAccountId().toString());
    }
    if (row.getInvestmentId() != null) {
      details.put("investment_id", row.getInvestmentId().toString());
    }
    if (row.getUnidentifiedDepositId() != null) {
      details.put("unidentified_deposit_id", row.getUnidentifiedDepositId().toString());
    }
    if (row.getChargeRecoverable() != null) {
      details.put("recoverable", row.getChargeRecoverable().toString());
      details.put("vat_amount", row.getChargeVatAmount().toPlainString());
    }
    return details;
  }
}
