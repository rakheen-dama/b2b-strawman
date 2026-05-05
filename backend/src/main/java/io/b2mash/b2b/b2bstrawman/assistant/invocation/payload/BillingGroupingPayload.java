package io.b2mash.b2b.b2bstrawman.assistant.invocation.payload;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Proposed output for the Billing specialist's invoice line-grouping operation.
 *
 * <p>Each {@link LineGroup} aggregates multiple time entries into a single descriptive line item
 * for the invoice, rolling up hours across source entries.
 */
public record BillingGroupingPayload(UUID invoiceId, List<LineGroup> groups)
    implements OutputPayload {

  public record LineGroup(String description, BigDecimal hours, List<UUID> sourceTimeEntryIds) {}
}
