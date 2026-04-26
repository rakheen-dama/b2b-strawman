package io.b2mash.b2b.b2bstrawman.event;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Base interface for all domain events published via Spring ApplicationEventPublisher. All
 * implementations must be records with primitive/UUID fields only — no JPA entity references, no
 * lazy proxies. This ensures events remain valid after the publishing transaction commits and the
 * persistence context closes.
 *
 * <p>Events carry enough context for any consumer to act without additional DB queries. The {@code
 * tenantId} field enables tenant-scoped processing in event handlers.
 */
public sealed interface DomainEvent
    permits BudgetThresholdEvent,
        CommentCreatedEvent,
        CommentUpdatedEvent,
        CommentDeletedEvent,
        CommentVisibilityChangedEvent,
        TaskAssignedEvent,
        TaskClaimedEvent,
        TaskStatusChangedEvent,
        TaskCompletedEvent,
        TaskCancelledEvent,
        TaskReopenedEvent,
        DocumentUploadedEvent,
        MemberAddedToProjectEvent,
        InvoiceApprovedEvent,
        InvoiceSentEvent,
        InvoicePaidEvent,
        InvoicePaymentReversedEvent,
        InvoicePaymentPartiallyReversedEvent,
        InvoiceVoidedEvent,
        DocumentGeneratedEvent,
        TimeEntryChangedEvent,
        AcceptanceRequestSentEvent,
        AcceptanceRequestViewedEvent,
        AcceptanceRequestAcceptedEvent,
        AcceptanceRequestRevokedEvent,
        AcceptanceRequestExpiredEvent,
        ProjectCompletedEvent,
        ProjectArchivedEvent,
        ProjectReopenedEvent,
        ExpenseCreatedEvent,
        ExpenseDeletedEvent,
        TaskRecurrenceCreatedEvent,
        ProposalSentEvent,
        InformationRequestSentEvent,
        InformationRequestCancelledEvent,
        InformationRequestCompletedEvent,
        RequestItemSubmittedEvent,
        RequestItemAcceptedEvent,
        RequestItemRejectedEvent,
        InformationRequestDraftCreatedEvent,
        CustomerStatusChangedEvent,
        FieldDateApproachingEvent {

  String eventType();

  String entityType();

  UUID entityId();

  UUID projectId();

  UUID actorMemberId();

  String actorName();

  String tenantId();

  /** Clerk org ID for ORG_ID ScopedValue binding. */
  String orgId();

  Instant occurredAt();

  Map<String, Object> details();

  /**
   * Returns the automation execution ID if this event was triggered by an automation action. Used
   * for cycle detection — events with a non-null execution ID are skipped by the automation engine
   * to prevent infinite loops.
   *
   * <p>The default implementation reads from {@link RequestScopes#AUTOMATION_EXECUTION_ID}, which
   * is bound by {@code AutomationActionExecutor} when executing actions. Events that carry the
   * execution ID as an explicit field (e.g., {@link TaskStatusChangedEvent}, {@link
   * ProjectCompletedEvent}) override this method.
   *
   * @return the automation execution ID, or null if this event was not automation-triggered
   */
  default UUID automationExecutionId() {
    return RequestScopes.AUTOMATION_EXECUTION_ID.isBound()
        ? RequestScopes.AUTOMATION_EXECUTION_ID.get()
        : null;
  }
}
