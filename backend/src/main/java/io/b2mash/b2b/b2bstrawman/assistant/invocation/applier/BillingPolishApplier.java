package io.b2mash.b2b.b2bstrawman.assistant.invocation.applier;

import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.BillingPolishPayload;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.CapabilityAuthorizationService;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryService;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies a {@link BillingPolishPayload} on approval — rewrites the description on each proposed
 * time entry. Description-only edits to time entries attached to a DRAFT invoice are explicitly
 * supported via {@link TimeEntryService#updateTimeEntryDescriptionOnDraftInvoice}.
 *
 * <p>Capability gate: re-checks {@code INVOICE_EDIT} as a belt-and-braces guard on top of the
 * upstream {@code AI_ASSISTANT_USE} check applied in {@code AiSpecialistInvocationService.approve}.
 */
@Component
public class BillingPolishApplier implements OutputApplier<BillingPolishPayload> {

  private final TimeEntryService timeEntryService;
  private final CapabilityAuthorizationService capabilityAuthorizationService;

  public BillingPolishApplier(
      TimeEntryService timeEntryService,
      CapabilityAuthorizationService capabilityAuthorizationService) {
    this.timeEntryService = timeEntryService;
    this.capabilityAuthorizationService = capabilityAuthorizationService;
  }

  @Override
  public Class<BillingPolishPayload> payloadType() {
    return BillingPolishPayload.class;
  }

  @Override
  @Transactional
  public void apply(BillingPolishPayload payload, UUID actorId) {
    capabilityAuthorizationService.requireCapability("INVOICE_EDIT");
    var actor = new ActorContext(actorId, RequestScopes.getOrgRole());
    for (var edit : payload.edits()) {
      timeEntryService.updateTimeEntryDescriptionOnDraftInvoice(
          edit.timeEntryId(), edit.afterText(), actor);
    }
  }
}
