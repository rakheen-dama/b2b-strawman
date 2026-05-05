package io.b2mash.b2b.b2bstrawman.assistant.invocation.applier;

import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.BillingGroupingPayload;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceService;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Applies an approved {@link BillingGroupingPayload} by calling {@link
 * InvoiceService#setFieldGroups} with the grouped source time-entry IDs.
 *
 * <p>The service's own permission checks apply at apply-time.
 */
@Component
public class BillingGroupingApplier implements OutputApplier<BillingGroupingPayload> {

  private final InvoiceService invoiceService;

  public BillingGroupingApplier(InvoiceService invoiceService) {
    this.invoiceService = invoiceService;
  }

  @Override
  public Class<BillingGroupingPayload> payloadType() {
    return BillingGroupingPayload.class;
  }

  @Override
  public void apply(BillingGroupingPayload payload, UUID actorId) {
    // Collect all source time entry IDs across groups for the field-group application
    var allSourceIds =
        payload.groups().stream().flatMap(g -> g.sourceTimeEntryIds().stream()).toList();
    invoiceService.setFieldGroups(payload.invoiceId(), allSourceIds);
  }
}
