package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Immutable representation of a parsed bank statement.
 *
 * @param periodStart the statement period start date
 * @param periodEnd the statement period end date
 * @param openingBalance the opening balance
 * @param closingBalance the closing balance
 * @param lines the parsed transaction lines
 */
public record ParsedStatement(
    LocalDate periodStart,
    LocalDate periodEnd,
    BigDecimal openingBalance,
    BigDecimal closingBalance,
    List<ParsedStatementLine> lines) {}
