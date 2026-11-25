package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Whether Kazi pushes fee notes and clients to the accounting system ({@code
 * kazi.accounting.push.enabled}, false when absent). It stays off until the drain worker sends real
 * payloads: {@link AccountingSyncWorker} still dispatches placeholder requests (PR #1860 ruling).
 *
 * <p>While off: {@link AccountingSyncEventListener} queues nothing, {@link AccountingSyncWorker}
 * leaves INVOICE and CUSTOMER push entries untouched (other entries drain as before), and a manual
 * resync is refused by {@link AccountingSyncService#forceResyncInvoice}. Direct calls to {@link
 * AccountingSyncService#enqueueInvoicePush} are not gated; the worker gate keeps whatever they
 * queue from being sent.
 */
@Component
public class AccountingPushSwitch {

  private static final Logger log = LoggerFactory.getLogger(AccountingPushSwitch.class);

  private final boolean enabled;

  public AccountingPushSwitch(@Value("${kazi.accounting.push.enabled:false}") boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isEnabled() {
    return enabled;
  }

  @EventListener(ApplicationReadyEvent.class)
  void logState() {
    if (enabled) {
      log.info(
          "Accounting pushes are ENABLED (kazi.accounting.push.enabled=true): fee notes and clients"
              + " are queued and sent to the connected accounting system");
    } else {
      log.info(
          "Accounting pushes are DISABLED (kazi.accounting.push.enabled=false): fee notes and"
              + " clients are not queued or sent; manual resync is refused");
    }
  }
}
