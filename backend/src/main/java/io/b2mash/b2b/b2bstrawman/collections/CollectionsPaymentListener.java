package io.b2mash.b2b.b2bstrawman.collections;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.event.InvoicePaidEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceVoidedEvent;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGateService;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiGateExpiredEvent;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiGateRejectedEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Closes the collections safety loop (Phase 83, ADR-325). Reacts, {@code AFTER_COMMIT}, to the
 * events that make a pending collection reminder moot or terminal:
 *
 * <ul>
 *   <li>{@link InvoicePaidEvent} / {@link InvoiceVoidedEvent} — the invoice was settled or voided
 *       on ANY route (manual, PSP webhook, Xero pull): expire each PROPOSED activity's PENDING gate
 *       and transition the activity to {@code CANCELLED_PAYMENT}, writing a {@code
 *       collections.reminder.cancelled} audit row.
 *   <li>{@link AiGateRejectedEvent} (gateType {@code SEND_COLLECTION_REMINDER} only) — a human
 *       declined to chase: the activity becomes {@code REJECTED} (terminal for that stage).
 *   <li>{@link AiGateExpiredEvent} (same gateType filter) — the 72h review window lapsed: the
 *       activity becomes {@code SKIPPED(gate_expired)} (retryable; the {@code gateId} is retained
 *       so the last draft stays traceable, and the next scan re-proposes).
 * </ul>
 *
 * <p>Every DB write runs in a fresh {@code REQUIRES_NEW} transaction because the originating
 * transaction has already committed by the time an {@code AFTER_COMMIT} listener runs. Each event
 * is handled defensively (catch-and-log): a listener exception must never propagate — the
 * payment/gate transition already committed, and poisoning the worker thread would be worse than a
 * dropped follow-up. Transitions are guarded on the activity still being {@code PROPOSED}, so a
 * terminal activity (e.g. already {@code SENT}) is a safe no-op regardless of commit order (§5.2).
 */
@Component
public class CollectionsPaymentListener {

  private static final Logger log = LoggerFactory.getLogger(CollectionsPaymentListener.class);

  /** Gate discriminator for collection reminders (a free String; 590B makes it executable). */
  static final String GATE_TYPE_SEND_COLLECTION_REMINDER = "SEND_COLLECTION_REMINDER";

  static final String REASON_INVOICE_PAID = "invoice_paid";
  static final String REASON_INVOICE_VOIDED = "invoice_voided";
  static final String REASON_GATE_EXPIRED = "gate_expired";

  private final CollectionActivityRepository activityRepository;
  private final AiExecutionGateService gateService;
  private final AuditService auditService;
  private final TransactionTemplate requiresNewTransactionTemplate;

  public CollectionsPaymentListener(
      CollectionActivityRepository activityRepository,
      AiExecutionGateService gateService,
      AuditService auditService,
      PlatformTransactionManager transactionManager) {
    this.activityRepository = activityRepository;
    this.gateService = gateService;
    this.auditService = auditService;
    this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
    this.requiresNewTransactionTemplate.setPropagationBehavior(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onInvoicePaid(InvoicePaidEvent event) {
    if (event.tenantId() == null) {
      log.warn("InvoicePaidEvent has null tenantId, dropping: invoice={}", event.entityId());
      return;
    }
    RequestScopes.runForTenantOnShard(
        event.tenantId(),
        event.orgId(),
        event.shardId(),
        () -> cancelPendingReminders(event.entityId(), event.invoiceNumber(), REASON_INVOICE_PAID));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onInvoiceVoided(InvoiceVoidedEvent event) {
    if (event.tenantId() == null) {
      log.warn("InvoiceVoidedEvent has null tenantId, dropping: invoice={}", event.entityId());
      return;
    }
    RequestScopes.runForTenantOnShard(
        event.tenantId(),
        event.orgId(),
        event.shardId(),
        () ->
            cancelPendingReminders(event.entityId(), event.invoiceNumber(), REASON_INVOICE_VOIDED));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onGateRejected(AiGateRejectedEvent event) {
    if (!GATE_TYPE_SEND_COLLECTION_REMINDER.equals(event.gateType())) {
      return;
    }
    // These events carry no tenant fields — the ambient ScopedValue binding from the publishing
    // (tenant-bound) thread is still in effect on this AFTER_COMMIT thread; do not re-bind.
    transitionByGate(event.gateId(), CollectionActivity::markRejected, "reject");
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onGateExpired(AiGateExpiredEvent event) {
    if (!GATE_TYPE_SEND_COLLECTION_REMINDER.equals(event.gateType())) {
      return;
    }
    transitionByGate(
        event.gateId(), activity -> activity.markSkipped(REASON_GATE_EXPIRED), "expire");
  }

  /** Expire the PENDING gate and cancel every PROPOSED activity for a paid/voided invoice. */
  private void cancelPendingReminders(UUID invoiceId, String invoiceNumber, String reason) {
    var proposed =
        activityRepository.findByInvoiceIdAndStatus(invoiceId, CollectionActivityStatus.PROPOSED);
    for (var activityRef : proposed) {
      try {
        requiresNewTransactionTemplate.executeWithoutResult(
            tx -> {
              var activity = activityRepository.findOneById(activityRef.getId()).orElse(null);
              if (activity == null || activity.getStatus() != CollectionActivityStatus.PROPOSED) {
                return; // already terminal (race) — no-op
              }
              if (activity.getGateId() != null) {
                gateService.expirePendingGate(activity.getGateId(), reason);
              }
              activity.markCancelled(reason);
              activityRepository.save(activity);
              auditService.log(
                  AuditEventBuilder.builder()
                      .eventType("collections.reminder.cancelled")
                      .entityType("collection_activity")
                      .entityId(activity.getId())
                      .actorType("SYSTEM")
                      .source("SYSTEM")
                      .details(
                          Map.of(
                              "invoice_id",
                              invoiceId.toString(),
                              "invoice_number",
                              invoiceNumber != null ? invoiceNumber : "",
                              "reason",
                              reason,
                              "stage",
                              activity.getStage().name()))
                      .build());
            });
      } catch (RuntimeException e) {
        log.error(
            "Failed to cancel collection reminder for invoice={} activity={}: {}",
            invoiceId,
            activityRef.getId(),
            e.getMessage(),
            e);
      }
    }
  }

  /** Apply a terminal/retryable transition to the PROPOSED activity carrying this gate. */
  private void transitionByGate(
      UUID gateId, java.util.function.Consumer<CollectionActivity> transition, String verb) {
    try {
      requiresNewTransactionTemplate.executeWithoutResult(
          tx -> {
            var activity = activityRepository.findByGateId(gateId).orElse(null);
            if (activity == null || activity.getStatus() != CollectionActivityStatus.PROPOSED) {
              return; // no matching PROPOSED activity — no-op
            }
            transition.accept(activity);
            activityRepository.save(activity);
          });
    } catch (RuntimeException e) {
      log.error(
          "Failed to {} collection activity for gate={}: {}", verb, gateId, e.getMessage(), e);
    }
  }
}
