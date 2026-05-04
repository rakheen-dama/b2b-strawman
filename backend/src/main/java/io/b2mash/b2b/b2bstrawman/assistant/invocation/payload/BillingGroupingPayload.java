package io.b2mash.b2b.b2bstrawman.assistant.invocation.payload;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Billing specialist proposed output payload for invoice line-item grouping.
 *
 * <p>Recorded by {@code ProposeInvoiceLineGroupingTool} with status PENDING_APPROVAL. Applied (on
 * approval) by {@code BillingGroupingApplier}, which restructures the invoice's line items via
 * {@code InvoiceService} on approval.
 *
 * <p>Per architecture §2.4: each {@link LineGroup} carries an aggregated description, the total
 * billable hours for the group, and the source time-entry ids that compose it.
 */
public record BillingGroupingPayload(UUID invoiceId, List<LineGroup> groups)
    implements OutputPayload {

  public BillingGroupingPayload {
    groups = groups != null ? List.copyOf(groups) : List.of();
  }

  public record LineGroup(String description, BigDecimal hours, List<UUID> sourceTimeEntryIds) {
    public LineGroup {
      sourceTimeEntryIds = sourceTimeEntryIds != null ? List.copyOf(sourceTimeEntryIds) : List.of();
    }
  }
}
