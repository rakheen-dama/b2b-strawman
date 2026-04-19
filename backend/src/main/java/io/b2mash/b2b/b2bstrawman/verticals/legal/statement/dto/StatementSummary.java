package io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto;

import java.math.BigDecimal;

/**
 * Numeric summary block of a Statement of Account (architecture §67.4.3 / §67.6.1). Mirrors the six
 * summary lines rendered in the template + the response payload's {@code summary} object.
 */
public record StatementSummary(
    BigDecimal totalFees,
    BigDecimal totalDisbursements,
    BigDecimal previousBalanceOwing,
    BigDecimal paymentsReceived,
    BigDecimal closingBalanceOwing,
    BigDecimal trustBalanceHeld) {}
