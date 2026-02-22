package io.b2mash.b2b.b2bstrawman.integration.accounting;

import java.math.BigDecimal;

/** A single line item within an invoice sync request. */
public record LineItem(
    String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxAmount) {}
