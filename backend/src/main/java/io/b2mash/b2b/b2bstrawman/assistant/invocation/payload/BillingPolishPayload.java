package io.b2mash.b2b.b2bstrawman.assistant.invocation.payload;

import java.util.List;
import java.util.UUID;

/**
 * Billing specialist proposed output payload for time-entry description polishing.
 *
 * <p>Recorded by {@code ProposeTimeEntryPolishTool} with status PENDING_APPROVAL. Applied (on
 * approval) by {@code BillingPolishApplier}, which delegates to {@code TimeEntryService} per edit.
 *
 * <p>Per architecture §2.4: each {@link PolishEdit} carries the time-entry id and both the prior
 * (beforeText) and proposed (afterText) descriptions so the human reviewer can see the diff.
 */
public record BillingPolishPayload(UUID invoiceId, List<PolishEdit> edits)
    implements OutputPayload {

  public BillingPolishPayload {
    edits = edits != null ? List.copyOf(edits) : List.of();
  }

  public record PolishEdit(UUID timeEntryId, String beforeText, String afterText) {}
}
