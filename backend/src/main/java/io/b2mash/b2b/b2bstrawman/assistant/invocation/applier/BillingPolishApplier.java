package io.b2mash.b2b.b2bstrawman.assistant.invocation.applier;

import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.BillingPolishPayload;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryService;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Applies an approved {@link BillingPolishPayload} by updating each time entry's description to the
 * polished text.
 *
 * <p>Delegates to {@link TimeEntryService#updateTimeEntryDescription} which bypasses the billed
 * guard since polish operates on draft-invoice time entries (invoiceId is set but descriptions are
 * safe to update). The service's own permission checks still apply at apply-time.
 */
@Component("billingPolishApplier")
public class BillingPolishApplier implements OutputApplier<BillingPolishPayload> {

  private final TimeEntryService timeEntryService;

  public BillingPolishApplier(TimeEntryService timeEntryService) {
    this.timeEntryService = timeEntryService;
  }

  @Override
  public Class<BillingPolishPayload> payloadType() {
    return BillingPolishPayload.class;
  }

  @Override
  public void apply(BillingPolishPayload payload, UUID actorId) {
    var role = RequestScopes.getOrgRole();
    var actor = new ActorContext(actorId, role != null ? role : Roles.ORG_MEMBER);
    for (var edit : payload.edits()) {
      timeEntryService.updateTimeEntryDescription(edit.timeEntryId(), edit.afterText(), actor);
    }
  }
}
