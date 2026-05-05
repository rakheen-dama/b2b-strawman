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
    // TODO(512A): InvoiceService.setFieldGroups only accepts a flat List<UUID> of time entry IDs.
    // The per-group structure (description, hours) from BillingGroupingPayload is not yet
    // propagated — a richer API (e.g. accepting GroupDefinition records) is needed to preserve
    // the grouping semantics proposed by the specialist. Until then, we apply the flat association
    // so the invoice at least references the correct time entries.
    var allSourceIds =
        payload.groups().stream().flatMap(g -> g.sourceTimeEntryIds().stream()).toList();
    invoiceService.setFieldGroups(payload.invoiceId(), allSourceIds);
  }
}
