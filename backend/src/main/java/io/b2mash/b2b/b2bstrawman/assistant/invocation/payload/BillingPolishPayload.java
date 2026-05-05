package io.b2mash.b2b.b2bstrawman.assistant.invocation.payload;

import java.util.List;
import java.util.UUID;

/**
 * Proposed output for the Billing specialist's time-entry polish operation.
 *
 * <p>Each {@link PolishEdit} maps a time entry to its polished (SA English, LSSA-vocabulary)
 * description. The applier updates only the {@code description} field on each time entry.
 */
public record BillingPolishPayload(UUID invoiceId, List<PolishEdit> edits)
    implements OutputPayload {

  public record PolishEdit(UUID timeEntryId, String beforeText, String afterText) {}
}
