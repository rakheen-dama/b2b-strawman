package io.b2mash.b2b.b2bstrawman.automation;

import io.b2mash.b2b.b2bstrawman.event.BudgetThresholdEvent;
import io.b2mash.b2b.b2bstrawman.event.DocumentUploadedEvent;
import io.b2mash.b2b.b2bstrawman.event.DomainEvent;
import io.b2mash.b2b.b2bstrawman.event.InformationRequestCompletedEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoicePaidEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceSentEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceVoidedEvent;
import io.b2mash.b2b.b2bstrawman.event.ProjectArchivedEvent;
import io.b2mash.b2b.b2bstrawman.event.ProjectCompletedEvent;
import io.b2mash.b2b.b2bstrawman.event.ProjectReopenedEvent;
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
          Map.entry(DocumentUploadedEvent.class, TriggerType.DOCUMENT_ACCEPTED),
          Map.entry(InvoiceSentEvent.class, TriggerType.INVOICE_STATUS_CHANGED),
          Map.entry(InvoicePaidEvent.class, TriggerType.INVOICE_STATUS_CHANGED),
          Map.entry(InvoiceVoidedEvent.class, TriggerType.INVOICE_STATUS_CHANGED),
          Map.entry(
              InformationRequestCompletedEvent.class, TriggerType.INFORMATION_REQUEST_COMPLETED));

  private TriggerTypeMapping() {}

  /**
   * Returns the {@link TriggerType} for the given event, or {@code null} if the event type is not
   * mapped to any trigger.
   */
  public static TriggerType getTriggerType(DomainEvent event) {
    return MAPPINGS.get(event.getClass());
  }
}
