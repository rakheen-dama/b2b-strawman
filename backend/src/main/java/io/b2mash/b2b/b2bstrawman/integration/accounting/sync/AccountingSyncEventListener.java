package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

import io.b2mash.b2b.b2bstrawman.customerbackend.event.CustomerCreatedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.CustomerUpdatedEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceApprovedEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceSentEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceVoidedEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Subscribes to domain events and enqueues accounting sync entries. Runs AFTER_COMMIT to ensure the
 * originating transaction has committed before creating sync entries.
 *
 * <p>Every enqueue runs in a fresh {@code REQUIRES_NEW} transaction, opened inside the tenant
 * binding so the new session resolves the event's tenant schema. At AFTER_COMMIT the originating
 * transaction's resources are still bound to the thread, so the service's REQUIRED
 * {@code @Transactional} would otherwise join that finished transaction and its insert would never
 * be committed (F-658A-1). Idempotency stays in {@link AccountingSyncService}: an active or
 * terminal entry for the entity makes the enqueue a no-op.
 *
 * <p>Nothing is queued while {@link AccountingPushSwitch} is off (the default until the worker
 * sends real payloads).
 */
@Component
public class AccountingSyncEventListener {

  private static final Logger log = LoggerFactory.getLogger(AccountingSyncEventListener.class);

  private final AccountingSyncService syncService;
  private final AccountingPushSwitch pushSwitch;
  private final TransactionTemplate requiresNewTransactionTemplate;

  public AccountingSyncEventListener(
      AccountingSyncService syncService,
      AccountingPushSwitch pushSwitch,
      PlatformTransactionManager transactionManager) {
    this.syncService = syncService;
    this.pushSwitch = pushSwitch;
    this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
    this.requiresNewTransactionTemplate.setPropagationBehavior(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onInvoiceApproved(InvoiceApprovedEvent event) {
    if (!pushSwitch.isEnabled()) {
      return;
    }
    RequestScopes.runForTenantOnShard(
        event.tenantId(),
        event.orgId(),
        event.shardId(),
        () -> {
          try {
            requiresNewTransactionTemplate.executeWithoutResult(
                status -> syncService.enqueueInvoicePush(event.entityId(), SyncTrigger.EVENT));
          } catch (Exception e) {
            log.warn(
                "Failed to enqueue invoice push for approved invoice {}: {}",
                event.entityId(),
                e.getMessage(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onInvoiceSent(InvoiceSentEvent event) {
    if (!pushSwitch.isEnabled()) {
      return;
    }
    RequestScopes.runForTenantOnShard(
        event.tenantId(),
        event.orgId(),
        event.shardId(),
        () -> {
          try {
            requiresNewTransactionTemplate.executeWithoutResult(
                status -> syncService.enqueueInvoicePush(event.entityId(), SyncTrigger.EVENT));
          } catch (Exception e) {
            log.warn(
                "Failed to enqueue invoice push for sent invoice {}: {}",
                event.entityId(),
                e.getMessage(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onInvoiceVoided(InvoiceVoidedEvent event) {
    if (!pushSwitch.isEnabled()) {
      return;
    }
    RequestScopes.runForTenantOnShard(
        event.tenantId(),
        event.orgId(),
        event.shardId(),
        () -> {
          try {
            requiresNewTransactionTemplate.executeWithoutResult(
                status -> syncService.enqueueInvoicePush(event.entityId(), SyncTrigger.EVENT));
          } catch (Exception e) {
            log.warn(
                "Failed to enqueue invoice push for voided invoice {}: {}",
                event.entityId(),
                e.getMessage(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCustomerCreated(CustomerCreatedEvent event) {
    if (!pushSwitch.isEnabled()) {
      return;
    }
    RequestScopes.runForTenant(
        event.getTenantId(),
        event.getOrgId(),
        () -> {
          try {
            requiresNewTransactionTemplate.executeWithoutResult(
                status ->
                    syncService.enqueueCustomerPush(event.getCustomerId(), SyncTrigger.EVENT));
          } catch (Exception e) {
            log.warn(
                "Failed to enqueue customer push for created customer {}: {}",
                event.getCustomerId(),
                e.getMessage(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCustomerUpdated(CustomerUpdatedEvent event) {
    if (!pushSwitch.isEnabled()) {
      return;
    }
    RequestScopes.runForTenant(
        event.getTenantId(),
        event.getOrgId(),
        () -> {
          try {
            requiresNewTransactionTemplate.executeWithoutResult(
                status ->
                    syncService.enqueueCustomerPush(event.getCustomerId(), SyncTrigger.EVENT));
          } catch (Exception e) {
            log.warn(
                "Failed to enqueue customer push for updated customer {}: {}",
                event.getCustomerId(),
                e.getMessage(),
                e);
          }
        });
  }
}
