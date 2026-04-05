package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser;

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
 */
public record ParsedStatementLine(
    LocalDate date,
    String description,
    String reference,
    BigDecimal amount,
    BigDecimal runningBalance) {}
