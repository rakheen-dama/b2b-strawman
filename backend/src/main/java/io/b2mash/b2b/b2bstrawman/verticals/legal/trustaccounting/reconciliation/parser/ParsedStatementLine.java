package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser;

import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLineClassification;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Immutable representation of a single parsed bank statement line.
 *
 * @param date the transaction date
 * @param description the transaction description
 * @param reference the payment reference (nullable)
 * @param amount the signed amount (positive=credit, negative=debit)
 * @param runningBalance the running balance after this transaction (nullable)
 * @param suggestedClassification the parser's suggestion (Phase 92 / 647C, architecture §16.3.5,
 *     ADR-380) — null when no keyword applies; never acted on by the system, it only pre-selects
 *     the Record from statement dialog
 */
public record ParsedStatementLine(
    LocalDate date,
    String description,
    String reference,
    BigDecimal amount,
    BigDecimal runningBalance,
    StatementLineClassification suggestedClassification) {}
