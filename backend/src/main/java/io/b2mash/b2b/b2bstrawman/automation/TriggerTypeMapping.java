package io.b2mash.b2b.b2bstrawman.automation;

import io.b2mash.b2b.b2bstrawman.event.BudgetThresholdEvent;
import io.b2mash.b2b.b2bstrawman.event.CustomerStatusChangedEvent;
import io.b2mash.b2b.b2bstrawman.event.DocumentUploadedEvent;
import io.b2mash.b2b.b2bstrawman.event.DomainEvent;
import io.b2mash.b2b.b2bstrawman.event.InformationRequestCompletedEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoicePaidEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceSentEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceVoidedEvent;
import io.b2mash.b2b.b2bstrawman.event.ProjectArchivedEvent;
import io.b2mash.b2b.b2bstrawman.event.ProjectCompletedEvent;
import io.b2mash.b2b.b2bstrawman.event.ProjectReopenedEvent;
import io.b2mash.b2b.b2bstrawman.event.ProposalSentEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskStatusChangedEvent;
import io.b2mash.b2b.b2bstrawman.event.TimeEntryChangedEvent;
import java.util.Map;

/**
 * Static mapping of domain event classes to their corresponding {@link TriggerType}. Used by {@link
 * AutomationEventListener} to determine which automation rules to evaluate for a given event.
 */
public final class TriggerTypeMapping {

  private static final Map<Class<? extends DomainEvent>, TriggerType> MAPPINGS =
      Map.ofEntries(
          Map.entry(TaskStatusChangedEvent.class, TriggerType.TASK_STATUS_CHANGED),
          Map.entry(ProjectCompletedEvent.class, TriggerType.PROJECT_STATUS_CHANGED),
          Map.entry(ProjectArchivedEvent.class, TriggerType.PROJECT_STATUS_CHANGED),
          Map.entry(ProjectReopenedEvent.class, TriggerType.PROJECT_STATUS_CHANGED),
          Map.entry(BudgetThresholdEvent.class, TriggerType.BUDGET_THRESHOLD_REACHED),
          Map.entry(TimeEntryChangedEvent.class, TriggerType.TIME_ENTRY_CREATED),
          // Document upload is treated as acceptance in this domain — there is no separate
          // approval step, so uploading a document implicitly marks it as accepted.
          Map.entry(DocumentUploadedEvent.class, TriggerType.DOCUMENT_ACCEPTED),
          Map.entry(InvoiceSentEvent.class, TriggerType.INVOICE_STATUS_CHANGED),
          Map.entry(InvoicePaidEvent.class, TriggerType.INVOICE_STATUS_CHANGED),
          Map.entry(InvoiceVoidedEvent.class, TriggerType.INVOICE_STATUS_CHANGED),
          Map.entry(
              InformationRequestCompletedEvent.class, TriggerType.INFORMATION_REQUEST_COMPLETED),
          Map.entry(ProposalSentEvent.class, TriggerType.PROPOSAL_SENT),
          Map.entry(CustomerStatusChangedEvent.class, TriggerType.CUSTOMER_STATUS_CHANGED));

  private TriggerTypeMapping() {}

  /**
   * Returns the {@link TriggerType} for the given event, or {@code null} if the event type is not
   * mapped to any trigger.
   *
   * <p>For {@link TimeEntryChangedEvent}, only the {@code CREATED} action maps to {@link
   * TriggerType#TIME_ENTRY_CREATED}. Updates and deletes return {@code null}, keeping the door open
   * for future trigger types (e.g. TIME_ENTRY_UPDATED) without modifying the enum.
   */
  public static TriggerType getTriggerType(DomainEvent event) {
    // TimeEntryChangedEvent carries an action field (CREATED/UPDATED/DELETED);
    // only CREATED maps to TIME_ENTRY_CREATED — other actions are not yet wired.
    if (event instanceof TimeEntryChangedEvent te && !"CREATED".equals(te.action())) {
      return null;
    }
    return MAPPINGS.get(event.getClass());
  }
}
