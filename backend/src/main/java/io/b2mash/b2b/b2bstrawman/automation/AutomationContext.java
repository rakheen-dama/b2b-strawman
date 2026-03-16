package io.b2mash.b2b.b2bstrawman.automation;

import io.b2mash.b2b.b2bstrawman.event.BudgetThresholdEvent;
import io.b2mash.b2b.b2bstrawman.event.DocumentUploadedEvent;
import io.b2mash.b2b.b2bstrawman.event.DomainEvent;
import io.b2mash.b2b.b2bstrawman.event.FieldDateApproachingEvent;
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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds a structured context map for automation condition evaluation. Each trigger type produces a
 * context with entity-specific fields plus common {@code actor.*} and {@code rule.*} sections.
 *
 * <p>All data is extracted from the {@link DomainEvent} — no additional database queries are
 * performed.
 */
public final class AutomationContext {

  private AutomationContext() {}

  /**
   * Builds a context map for the given trigger type, event, and rule.
   *
   * @param triggerType the trigger type that matched this event
   * @param event the domain event carrying entity data
   * @param rule the automation rule being evaluated
   * @return a nested map keyed by entity name (e.g., "task", "project", "actor")
   */
  public static Map<String, Map<String, Object>> build(
      TriggerType triggerType, DomainEvent event, AutomationRule rule) {
    var context = new LinkedHashMap<String, Map<String, Object>>();

    // Common sections for all trigger types
    context.put("actor", buildActor(event));
    context.put("rule", buildRule(rule));

    // Trigger-specific entity sections
    switch (triggerType) {
      case TASK_STATUS_CHANGED -> buildTaskStatusChanged((TaskStatusChangedEvent) event, context);
      case PROJECT_STATUS_CHANGED -> buildProjectStatusChanged(event, context);
      case CUSTOMER_STATUS_CHANGED -> buildCustomerStatusChanged(event, context);
      case INVOICE_STATUS_CHANGED -> buildInvoiceStatusChanged(event, context);
      case TIME_ENTRY_CREATED -> buildTimeEntryCreated((TimeEntryChangedEvent) event, context);
      case BUDGET_THRESHOLD_REACHED ->
          buildBudgetThresholdReached((BudgetThresholdEvent) event, context);
      case DOCUMENT_ACCEPTED -> buildDocumentAccepted((DocumentUploadedEvent) event, context);
      case INFORMATION_REQUEST_COMPLETED ->
          buildInformationRequestCompleted((InformationRequestCompletedEvent) event, context);
      case PROPOSAL_SENT -> buildProposalSent((ProposalSentEvent) event, context);
      case FIELD_DATE_APPROACHING ->
          buildFieldDateApproaching((FieldDateApproachingEvent) event, context);
    }

    return context;
  }

  private static Map<String, Object> buildActor(DomainEvent event) {
    var actor = new LinkedHashMap<String, Object>();
    actor.put("id", uuidToString(event.actorMemberId()));
    actor.put("name", event.actorName());
    return actor;
  }

  private static Map<String, Object> buildRule(AutomationRule rule) {
    var ruleMap = new LinkedHashMap<String, Object>();
    ruleMap.put("id", uuidToString(rule.getId()));
    ruleMap.put("name", rule.getName());
    return ruleMap;
  }

  private static void buildTaskStatusChanged(
      TaskStatusChangedEvent event, Map<String, Map<String, Object>> context) {
    var task = new LinkedHashMap<String, Object>();
    task.put("id", uuidToString(event.entityId()));
    task.put("name", event.taskTitle());
    task.put("status", event.newStatus());
    task.put("previousStatus", event.oldStatus());
    task.put("assigneeId", uuidToString(event.assigneeMemberId()));
    task.put("projectId", uuidToString(event.projectId()));
    context.put("task", task);

    var project = new LinkedHashMap<String, Object>();
    project.put("id", uuidToString(event.projectId()));
    project.put("name", detailValue(event, "project_name"));
    context.put("project", project);

    var customer = new LinkedHashMap<String, Object>();
    customer.put("id", detailValue(event, "customer_id"));
    customer.put("name", detailValue(event, "customer_name"));
    context.put("customer", customer);
  }

  private static void buildProjectStatusChanged(
      DomainEvent event, Map<String, Map<String, Object>> context) {
    var project = new LinkedHashMap<String, Object>();
    project.put("id", uuidToString(event.entityId()));

    if (event instanceof ProjectCompletedEvent pce) {
      project.put("name", pce.projectName());
      project.put("status", "COMPLETED");
      project.put("previousStatus", detailValue(event, "previous_status"));
    } else if (event instanceof ProjectArchivedEvent pae) {
      project.put("name", pae.projectName());
      project.put("status", "ARCHIVED");
      project.put("previousStatus", detailValue(event, "previous_status"));
    } else if (event instanceof ProjectReopenedEvent pre) {
      project.put("name", pre.projectName());
      project.put("status", "ACTIVE");
      project.put("previousStatus", pre.previousStatus());
    }

    project.put("customerId", detailValue(event, "customer_id"));
    context.put("project", project);

    var customer = new LinkedHashMap<String, Object>();
    customer.put("id", detailValue(event, "customer_id"));
    customer.put("name", detailValue(event, "customer_name"));
    context.put("customer", customer);
  }

  private static void buildCustomerStatusChanged(
      DomainEvent event, Map<String, Map<String, Object>> context) {
    var customer = new LinkedHashMap<String, Object>();
    customer.put("id", uuidToString(event.entityId()));
    customer.put("name", detailValue(event, "customer_name"));
    customer.put("status", detailValue(event, "new_status"));
    customer.put("previousStatus", detailValue(event, "old_status"));
    context.put("customer", customer);
  }

  private static void buildProposalSent(
      ProposalSentEvent event, Map<String, Map<String, Object>> context) {
    var proposal = new LinkedHashMap<String, Object>();
    proposal.put("id", uuidToString(event.entityId()));
    proposal.put("sentAt", event.occurredAt().toString());
    context.put("proposal", proposal);

    var customer = new LinkedHashMap<String, Object>();
    customer.put("id", detailValue(event, "customer_id"));
    customer.put("name", detailValue(event, "customer_name"));
    context.put("customer", customer);

    var project = new LinkedHashMap<String, Object>();
    project.put("id", uuidToString(event.projectId()));
    project.put("name", detailValue(event, "project_name"));
    context.put("project", project);
  }

  private static void buildInvoiceStatusChanged(
      DomainEvent event, Map<String, Map<String, Object>> context) {
    var invoice = new LinkedHashMap<String, Object>();
    invoice.put("id", uuidToString(event.entityId()));

    if (event instanceof InvoiceSentEvent ise) {
      invoice.put("invoiceNumber", ise.invoiceNumber());
      invoice.put("status", "SENT");
      invoice.put("customerId", detailValue(event, "customer_id"));
      addCustomerContext(context, ise.customerName());
    } else if (event instanceof InvoicePaidEvent ipe) {
      invoice.put("invoiceNumber", ipe.invoiceNumber());
      invoice.put("status", "PAID");
      invoice.put("customerId", detailValue(event, "customer_id"));
      addCustomerContext(context, ipe.customerName());
    } else if (event instanceof InvoiceVoidedEvent ive) {
      invoice.put("invoiceNumber", ive.invoiceNumber());
      invoice.put("status", "VOIDED");
      invoice.put("customerId", detailValue(event, "customer_id"));
      addCustomerContext(context, ive.customerName());
    }

    context.put("invoice", invoice);
  }

  private static void addCustomerContext(
      Map<String, Map<String, Object>> context, String customerName) {
    var customer = new LinkedHashMap<String, Object>();
    customer.put("name", customerName);
    context.put("customer", customer);
  }

  private static void buildTimeEntryCreated(
      TimeEntryChangedEvent event, Map<String, Map<String, Object>> context) {
    var timeEntry = new LinkedHashMap<String, Object>();
    timeEntry.put("id", uuidToString(event.entityId()));
    timeEntry.put("projectId", uuidToString(event.projectId()));
    timeEntry.put("action", event.action());
    // Include details fields if available
    if (event.details() != null) {
      timeEntry.put("hours", event.details().get("hours"));
      timeEntry.put("taskId", event.details().get("task_id"));
    }
    context.put("timeEntry", timeEntry);

    var project = new LinkedHashMap<String, Object>();
    project.put("id", uuidToString(event.projectId()));
    context.put("project", project);
  }

  private static void buildBudgetThresholdReached(
      BudgetThresholdEvent event, Map<String, Map<String, Object>> context) {
    var budget = new LinkedHashMap<String, Object>();
    budget.put("projectId", uuidToString(event.projectId()));
    budget.put("consumedPercent", detailValue(event, "consumed_pct"));
    budget.put("thresholdPercent", detailValue(event, "threshold_pct"));
    budget.put("dimension", detailValue(event, "dimension"));
    context.put("budget", budget);

    var project = new LinkedHashMap<String, Object>();
    project.put("id", uuidToString(event.projectId()));
    project.put("name", detailValue(event, "project_name"));
    context.put("project", project);
  }

  private static void buildDocumentAccepted(
      DocumentUploadedEvent event, Map<String, Map<String, Object>> context) {
    var document = new LinkedHashMap<String, Object>();
    document.put("id", uuidToString(event.entityId()));
    document.put("name", event.documentName());
    document.put("projectId", uuidToString(event.projectId()));
    context.put("document", document);

    var project = new LinkedHashMap<String, Object>();
    project.put("id", uuidToString(event.projectId()));
    context.put("project", project);
  }

  private static void buildInformationRequestCompleted(
      InformationRequestCompletedEvent event, Map<String, Map<String, Object>> context) {
    var request = new LinkedHashMap<String, Object>();
    request.put("id", uuidToString(event.requestId()));
    request.put("customerId", uuidToString(event.customerId()));
    request.put("portalContactId", uuidToString(event.portalContactId()));
    context.put("request", request);

    var customer = new LinkedHashMap<String, Object>();
    customer.put("id", uuidToString(event.customerId()));
    context.put("customer", customer);
  }

  private static void buildFieldDateApproaching(
      FieldDateApproachingEvent event, Map<String, Map<String, Object>> context) {
    var entity = new LinkedHashMap<String, Object>();
    entity.put("type", event.entityType());
    entity.put("id", uuidToString(event.entityId()));
    entity.put("name", detailValue(event, "entity_name"));
    context.put("entity", entity);

    var field = new LinkedHashMap<String, Object>();
    field.put("name", detailValue(event, "field_name"));
    field.put("label", detailValue(event, "field_label"));
    field.put("value", detailValue(event, "field_value"));
    field.put("daysUntil", detailValue(event, "days_until"));
    context.put("field", field);
  }

  private static Object detailValue(DomainEvent event, String key) {
    Map<String, Object> details = event.details();
    if (details == null) {
      return null;
    }
    return details.get(key);
  }

  private static String uuidToString(Object uuid) {
    return uuid != null ? uuid.toString() : null;
  }
}
