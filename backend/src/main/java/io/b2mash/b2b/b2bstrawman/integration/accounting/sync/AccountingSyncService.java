package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingPaymentSource;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.AccountingXeroConnectionRepository;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.XeroConnectionStatus;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceTransitionService;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEvent;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEventRepository;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEventStatus;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Core orchestration service for accounting sync: enqueue, retry, summary, and payment polling. */
@Service
public class AccountingSyncService {

  private static final Logger log = LoggerFactory.getLogger(AccountingSyncService.class);

  private final AccountingSyncEntryRepository syncEntryRepository;
  private final AccountingXeroConnectionRepository xeroConnectionRepository;
  private final IntegrationRegistry integrationRegistry;
  private final ApplicationEventPublisher eventPublisher;
  private final InvoiceRepository invoiceRepository;
  private final InvoiceLineRepository invoiceLineRepository;
  private final CustomerRepository customerRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final AuditService auditService;
  private final TrustBoundaryGuard trustBoundaryGuard;
  private final InvoiceTransitionService invoiceTransitionService;
  private final PaymentEventRepository paymentEventRepository;

  public AccountingSyncService(
      AccountingSyncEntryRepository syncEntryRepository,
      AccountingXeroConnectionRepository xeroConnectionRepository,
      IntegrationRegistry integrationRegistry,
      ApplicationEventPublisher eventPublisher,
      InvoiceRepository invoiceRepository,
      InvoiceLineRepository invoiceLineRepository,
      CustomerRepository customerRepository,
      OrgSettingsRepository orgSettingsRepository,
      AuditService auditService,
      TrustBoundaryGuard trustBoundaryGuard,
      InvoiceTransitionService invoiceTransitionService,
      PaymentEventRepository paymentEventRepository) {
    this.syncEntryRepository = syncEntryRepository;
    this.xeroConnectionRepository = xeroConnectionRepository;
    this.integrationRegistry = integrationRegistry;
    this.eventPublisher = eventPublisher;
    this.invoiceRepository = invoiceRepository;
    this.invoiceLineRepository = invoiceLineRepository;
    this.customerRepository = customerRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.auditService = auditService;
    this.trustBoundaryGuard = trustBoundaryGuard;
    this.invoiceTransitionService = invoiceTransitionService;
    this.paymentEventRepository = paymentEventRepository;
  }

  /**
   * Enqueue an invoice push to Xero. Idempotent: if an active entry already exists for the invoice,
   * this is a no-op. If no CONNECTED Xero connection exists, returns silently.
   *
   * <p>Before creating a PENDING entry, evaluates the trust boundary guard to prevent trust-related
   * invoices from being synced, and validates currency matches the org's default currency.
   */
  @Transactional
  public void enqueueInvoicePush(UUID invoiceId, SyncTrigger trigger) {
    if (!hasConnectedXeroConnection()) {
      log.debug("No connected Xero connection; skipping invoice push for {}", invoiceId);
      return;
    }

    var existing = syncEntryRepository.findActiveEntryForEntity(SyncEntityType.INVOICE, invoiceId);
    if (existing.isPresent()) {
      log.debug("Active sync entry already exists for invoice {}; skipping enqueue", invoiceId);
      return;
    }

    var terminal =
        syncEntryRepository.findTerminalEntryForEntity(SyncEntityType.INVOICE, invoiceId);
    if (terminal.isPresent()) {
      log.debug(
          "Terminal sync entry ({}) already exists for invoice {}; skipping enqueue",
          terminal.get().getState(),
          invoiceId);
      return;
    }

    // Load invoice, lines, and customer for guard evaluation
    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));
    var lines = invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoiceId);
    var customer =
        customerRepository
            .findById(invoice.getCustomerId())
            .orElseThrow(() -> new ResourceNotFoundException("Customer", invoice.getCustomerId()));

    // Currency mismatch check — block when orgSettings is absent (fail-closed) or currency differs
    var orgSettings = orgSettingsRepository.findForCurrentTenant();
    if (orgSettings.isEmpty()) {
      var entry =
          new AccountingSyncEntry(
              SyncEntityType.INVOICE,
              invoiceId,
              "xero",
              SyncDirection.PUSH,
              trigger,
              "KAZI-INV-" + invoiceId);
      entry.markDeadLetter(
          "MULTI_CURRENCY", "Org settings not configured; cannot verify invoice currency");
      syncEntryRepository.save(entry);
      log.warn("Invoice {} dead-lettered: org settings absent, cannot verify currency", invoiceId);
      return;
    }
    if (!invoice.getCurrency().equals(orgSettings.get().getDefaultCurrency())) {
      var entry =
          new AccountingSyncEntry(
              SyncEntityType.INVOICE,
              invoiceId,
              "xero",
              SyncDirection.PUSH,
              trigger,
              "KAZI-INV-" + invoiceId);
      entry.markDeadLetter(
          "MULTI_CURRENCY",
          "Invoice currency %s does not match org default %s"
              .formatted(invoice.getCurrency(), orgSettings.get().getDefaultCurrency()));
      syncEntryRepository.save(entry);
      log.warn(
          "Invoice {} dead-lettered: currency mismatch ({} vs {})",
          invoiceId,
          invoice.getCurrency(),
          orgSettings.get().getDefaultCurrency());
      return;
    }

    // Trust boundary guard
    TrustBoundaryDecision decision = trustBoundaryGuard.evaluate(invoice, lines, customer);
    if (!decision.allowed()) {
      var entry =
          new AccountingSyncEntry(
              SyncEntityType.INVOICE,
              invoiceId,
              "xero",
              SyncDirection.PUSH,
              trigger,
              "KAZI-INV-" + invoiceId);
      entry.markBlockedTrustBoundary(decision.reason());
      var savedEntry = syncEntryRepository.save(entry);

      auditService.log(
          AuditEventBuilder.builder()
              .eventType("integration.xero.push_blocked_trust")
              .entityType("INVOICE")
              .entityId(invoice.getId())
              .actorType("SYSTEM")
              .source("ACCOUNTING_SYNC")
              .details(
                  Map.of(
                      "reason", decision.reason(),
                      "invoiceNumber",
                          invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "",
                      "customerName", customer.getName(),
                      "syncEntryId", savedEntry.getId().toString()))
              .build());

      log.warn("Invoice {} blocked by trust boundary guard: {}", invoiceId, decision.reason());
      return;
    }

    var entry =
        new AccountingSyncEntry(
            SyncEntityType.INVOICE,
            invoiceId,
            "xero",
            SyncDirection.PUSH,
            trigger,
            "KAZI-INV-" + invoiceId);
    syncEntryRepository.save(entry);
    log.info("Enqueued invoice push: invoiceId={}, trigger={}", invoiceId, trigger);
  }

  /**
   * Enqueue a customer push to Xero. Idempotent: if an active entry already exists for the
   * customer, this is a no-op. If no CONNECTED Xero connection exists, returns silently.
   */
  @Transactional
  public void enqueueCustomerPush(UUID customerId, SyncTrigger trigger) {
    if (!hasConnectedXeroConnection()) {
      log.debug("No connected Xero connection; skipping customer push for {}", customerId);
      return;
    }

    var existing =
        syncEntryRepository.findActiveEntryForEntity(SyncEntityType.CUSTOMER, customerId);
    if (existing.isPresent()) {
      log.debug("Active sync entry already exists for customer {}; skipping enqueue", customerId);
      return;
    }

    var entry =
        new AccountingSyncEntry(
            SyncEntityType.CUSTOMER,
            customerId,
            "xero",
            SyncDirection.PUSH,
            trigger,
            "KAZI-CUST-" + customerId);
    syncEntryRepository.save(entry);
    log.info("Enqueued customer push: customerId={}, trigger={}", customerId, trigger);
  }

  /** Reset a dead-lettered entry for manual retry. Resets state to PENDING with attempt count 0. */
  @Transactional
  public void retryFromDeadLetter(UUID syncEntryId) {
    var entry =
        syncEntryRepository
            .findOneById(syncEntryId)
            .orElseThrow(() -> new ResourceNotFoundException("AccountingSyncEntry", syncEntryId));
    entry.resetForRetry();
    syncEntryRepository.save(entry);
    log.info("Reset sync entry {} from DEAD_LETTER to PENDING for retry", syncEntryId);
  }

  /** Returns a summary of sync entry counts grouped by state. */
  @Transactional(readOnly = true)
  public Map<SyncState, Long> getSyncSummary() {
    var results = syncEntryRepository.countByState();
    Map<SyncState, Long> summary = new EnumMap<>(SyncState.class);
    for (Object[] row : results) {
      var state = (SyncState) row[0];
      var count = (Long) row[1];
      summary.put(state, count);
    }
    return summary;
  }

  /**
   * Poll payments for a Xero connection. Resolves external payments via {@link
   * AccountingPaymentSource}, matches them against Kazi invoices through completed PUSH sync
   * entries, and either reconciles (creating {@link PaymentEvent} + transitioning to PAID), detects
   * drift (creating {@code RECONCILE_DRIFT} sync entries), or skips (Xero-native invoices,
   * already-paid, or dedup).
   */
  @Transactional
  public PaymentPollSummary pollPaymentsForConnection(UUID connectionId) {
    var connection =
        xeroConnectionRepository
            .findOneById(connectionId)
            .orElseThrow(
                () -> new ResourceNotFoundException("AccountingXeroConnection", connectionId));

    var paymentSource =
        integrationRegistry.resolve(IntegrationDomain.ACCOUNTING, AccountingPaymentSource.class);

    int matched = 0, drifted = 0, skipped = 0;

    var since =
        connection.getLastPollAt() != null
            ? connection.getLastPollAt()
            : connection.getConnectedAt();
    var payments = paymentSource.getPaymentsModifiedSince(since);
    log.info("Polled {} payments for connection {} since {}", payments.size(), connectionId, since);

    for (var event : payments) {
      // 1. Look up Kazi invoice via external_reference on the completed PUSH sync entry
      var pushEntry =
          syncEntryRepository.findCompletedPushByExternalReference(
              event.externalInvoiceReference());

      if (pushEntry.isEmpty()) {
        log.debug(
            "No matching Kazi invoice for external reference {}; skipping (Xero-native)",
            event.externalInvoiceReference());
        skipped++;
        continue;
      }

      UUID invoiceId = pushEntry.get().getEntityId();
      var invoiceOpt = invoiceRepository.findById(invoiceId);

      if (invoiceOpt.isEmpty()) {
        log.warn(
            "Invoice {} referenced by sync entry {} not found; skipping",
            invoiceId,
            pushEntry.get().getId());
        skipped++;
        continue;
      }

      var invoice = invoiceOpt.get();

      // 2. Already PAID — idempotent skip
      if (invoice.getStatus() == InvoiceStatus.PAID) {
        log.debug("Invoice {} already PAID; skipping (idempotent)", invoiceId);
        skipped++;
        continue;
      }

      // 3. Not in SENT status — cannot auto-transition (only SENT -> PAID is valid)
      if (invoice.getStatus() != InvoiceStatus.SENT) {
        log.warn(
            "Invoice {} is in {} status (not SENT); skipping payment reconciliation",
            invoiceId,
            invoice.getStatus());
        skipped++;
        continue;
      }

      // 4. Dedup check — already have a PaymentEvent for this payment
      if (paymentEventRepository.existsByInvoiceIdAndPaymentReference(
          invoiceId, event.externalPaymentId())) {
        log.debug(
            "PaymentEvent already exists for invoice {} / payment {}; skipping",
            invoiceId,
            event.externalPaymentId());
        skipped++;
        continue;
      }

      // 5. Currency mismatch check
      if (!event.currency().equals(invoice.getCurrency())) {
        var currencyDriftEntry =
            new AccountingSyncEntry(
                SyncEntityType.PAYMENT_PULL,
                invoiceId,
                "xero",
                SyncDirection.PULL,
                SyncTrigger.EVENT,
                event.externalInvoiceReference());
        currencyDriftEntry.markReconcileDrift(
            "CURRENCY_MISMATCH",
            "Xero payment currency %s does not match Kazi invoice currency %s"
                .formatted(event.currency(), invoice.getCurrency()));
        syncEntryRepository.save(currencyDriftEntry);

        auditService.log(
            AuditEventBuilder.builder()
                .eventType("integration.xero.reconcile_drift")
                .entityType("INVOICE")
                .entityId(invoiceId)
                .actorType("SYSTEM")
                .source("ACCOUNTING_SYNC")
                .details(
                    Map.of(
                        "invoiceNumber",
                        invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "",
                        "xeroCurrency",
                        event.currency(),
                        "kaziCurrency",
                        invoice.getCurrency(),
                        "xeroPaymentId",
                        event.externalPaymentId()))
                .build());

        drifted++;
        continue;
      }

      // 6. Amount drift check
      BigDecimal drift = event.amount().subtract(invoice.getTotal()).abs();
      if (drift.compareTo(new BigDecimal("0.01")) > 0) {
        var driftEntry =
            new AccountingSyncEntry(
                SyncEntityType.PAYMENT_PULL,
                invoiceId,
                "xero",
                SyncDirection.PULL,
                SyncTrigger.EVENT,
                event.externalInvoiceReference());
        driftEntry.markReconcileDrift(
            "DRIFT_DETECTED",
            "Xero amount %s does not match Kazi total %s (drift: %s)"
                .formatted(event.amount(), invoice.getTotal(), drift));
        syncEntryRepository.save(driftEntry);

        auditService.log(
            AuditEventBuilder.builder()
                .eventType("integration.xero.reconcile_drift")
                .entityType("INVOICE")
                .entityId(invoiceId)
                .actorType("SYSTEM")
                .source("ACCOUNTING_SYNC")
                .details(
                    Map.of(
                        "invoiceNumber",
                        invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "",
                        "xeroAmount",
                        event.amount().toString(),
                        "kaziTotal",
                        invoice.getTotal().toString(),
                        "drift",
                        drift.toString(),
                        "xeroPaymentId",
                        event.externalPaymentId()))
                .build());

        drifted++;
        continue;
      }

      // 7. Match — create PaymentEvent, transition invoice, create PULL sync entry
      var paymentEvent =
          new PaymentEvent(
              invoiceId,
              "xero",
              null,
              PaymentEventStatus.COMPLETED,
              event.amount(),
              event.currency(),
              invoice.getPaymentDestination());
      paymentEvent.setPaymentReference(event.externalPaymentId());
      paymentEventRepository.save(paymentEvent);

      invoiceTransitionService.recordPayment(invoiceId, event.externalPaymentId(), true);

      var pullEntry =
          new AccountingSyncEntry(
              SyncEntityType.PAYMENT_PULL,
              invoiceId,
              "xero",
              SyncDirection.PULL,
              SyncTrigger.EVENT,
              event.externalInvoiceReference());
      pullEntry.markCompleted(event.externalPaymentId());
      syncEntryRepository.save(pullEntry);

      auditService.log(
          AuditEventBuilder.builder()
              .eventType("integration.xero.payment_reconciled")
              .entityType("INVOICE")
              .entityId(invoiceId)
              .actorType("SYSTEM")
              .source("ACCOUNTING_SYNC")
              .details(
                  Map.of(
                      "invoiceNumber",
                      invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "",
                      "xeroPaymentId",
                      event.externalPaymentId(),
                      "amount",
                      event.amount().toString(),
                      "syncEntryId",
                      pullEntry.getId().toString()))
              .build());

      eventPublisher.publishEvent(
          new XeroPaymentReconciledEvent(
              invoiceId, invoice.getInvoiceNumber(), event.amount(), event.externalPaymentId()));

      matched++;
    }

    connection.recordPoll();
    xeroConnectionRepository.save(connection);

    return new PaymentPollSummary(matched, drifted, skipped);
  }

  // ---- Controller-facing service methods ----

  /** Paginated, filterable sync entry log for the sync dashboard. */
  @Transactional(readOnly = true)
  public Page<AccountingSyncEntry> findEntries(
      SyncState state, SyncEntityType entityType, SyncDirection direction, Pageable pageable) {
    return syncEntryRepository.findFiltered(state, entityType, direction, pageable);
  }

  /** Returns a single sync entry by ID, or throws if not found. */
  @Transactional(readOnly = true)
  public AccountingSyncEntry findEntryById(UUID id) {
    return syncEntryRepository
        .findOneById(id)
        .orElseThrow(() -> new ResourceNotFoundException("AccountingSyncEntry", id));
  }

  /** Returns all sync entries for a given invoice, ordered by most recent first. */
  @Transactional(readOnly = true)
  public List<AccountingSyncEntry> findSyncStatusForInvoice(UUID invoiceId) {
    return syncEntryRepository.findByEntity(SyncEntityType.INVOICE, invoiceId);
  }

  /**
   * Resolves a reconciliation drift entry by marking it as COMPLETED. Only entries in
   * RECONCILE_DRIFT state can be resolved — throws if the entry is in any other state.
   */
  @Transactional
  public void resolveReconcileDrift(UUID syncEntryId) {
    var entry =
        syncEntryRepository
            .findOneById(syncEntryId)
            .orElseThrow(() -> new ResourceNotFoundException("AccountingSyncEntry", syncEntryId));
    if (entry.getState() != SyncState.RECONCILE_DRIFT) {
      throw new InvalidStateException(
          "Entry is not in RECONCILE_DRIFT state",
          "Sync entry " + syncEntryId + " is in state " + entry.getState());
    }
    entry.markCompleted(entry.getExternalId());
    syncEntryRepository.save(entry);
    log.info("Reconcile drift resolved for sync entry {}", syncEntryId);
  }

  /**
   * Trust boundary guard for sync entries. Delegates to {@link TrustBoundaryGuard} for INVOICE
   * entities; permits all other entity types (e.g. CUSTOMER, PAYMENT_PULL). If the invoice or
   * customer cannot be loaded, permits the sync — missing entities are not a trust boundary concern
   * and will be caught at the provider dispatch stage.
   */
  @Transactional(readOnly = true)
  public TrustBoundaryDecision checkTrustBoundary(AccountingSyncEntry entry) {
    if (entry.getEntityType() != SyncEntityType.INVOICE) {
      return TrustBoundaryDecision.permit();
    }

    var invoice = invoiceRepository.findById(entry.getEntityId());
    if (invoice.isEmpty()) {
      log.warn("Invoice {} not found for trust boundary check; permitting", entry.getEntityId());
      return TrustBoundaryDecision.permit();
    }

    var lines = invoiceLineRepository.findByInvoiceIdOrderBySortOrder(entry.getEntityId());
    var customer = customerRepository.findById(invoice.get().getCustomerId());
    if (customer.isEmpty()) {
      log.warn(
          "Customer {} not found for trust boundary check; permitting",
          invoice.get().getCustomerId());
      return TrustBoundaryDecision.permit();
    }

    return trustBoundaryGuard.evaluate(invoice.get(), lines, customer.get());
  }

  private boolean hasConnectedXeroConnection() {
    return xeroConnectionRepository.existsByStatus(XeroConnectionStatus.CONNECTED);
  }
}
