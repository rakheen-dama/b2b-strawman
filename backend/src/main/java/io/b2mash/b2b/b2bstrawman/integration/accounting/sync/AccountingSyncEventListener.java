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
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Subscribes to domain events and enqueues accounting sync entries. Runs AFTER_COMMIT to ensure the
 * originating transaction has committed before creating sync entries.
 */
@Component
public class AccountingSyncEventListener {

  private static final Logger log = LoggerFactory.getLogger(AccountingSyncEventListener.class);

  private final AccountingSyncService syncService;

  public AccountingSyncEventListener(AccountingSyncService syncService) {
    this.syncService = syncService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onInvoiceApproved(InvoiceApprovedEvent event) {
    RequestScopes.runForTenantOnShard(
        event.tenantId(),
        event.orgId(),
        event.shardId(),
        () -> {
          try {
            syncService.enqueueInvoicePush(event.entityId(), SyncTrigger.EVENT);
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
    RequestScopes.runForTenantOnShard(
        event.tenantId(),
        event.orgId(),
        event.shardId(),
        () -> {
          try {
            syncService.enqueueInvoicePush(event.entityId(), SyncTrigger.EVENT);
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
    RequestScopes.runForTenantOnShard(
        event.tenantId(),
        event.orgId(),
        event.shardId(),
        () -> {
          try {
            syncService.enqueueInvoicePush(event.entityId(), SyncTrigger.EVENT);
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
    RequestScopes.runForTenant(
        event.getTenantId(),
        event.getOrgId(),
        () -> {
          try {
            syncService.enqueueCustomerPush(event.getCustomerId(), SyncTrigger.EVENT);
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
    RequestScopes.runForTenant(
        event.getTenantId(),
        event.getOrgId(),
        () -> {
          try {
            syncService.enqueueCustomerPush(event.getCustomerId(), SyncTrigger.EVENT);
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
