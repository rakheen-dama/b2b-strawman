package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.InvoiceSyncEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.TaxContext;
import io.b2mash.b2b.b2bstrawman.event.InvoiceApprovedEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoicePaidEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoicePartiallyPaidEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoicePaymentPartiallyReversedEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoicePaymentReversedEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceSentEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceVoidedEvent;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.InvoiceValidationFailedException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.expense.ExpenseRepository;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.integration.payment.PaymentGateway;
import io.b2mash.b2b.b2bstrawman.integration.payment.PaymentRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.InvoiceResponse;
import io.b2mash.b2b.b2bstrawman.invoice.dto.SendInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.settings.BillingSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.tax.TaxCalculationService;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles invoice lifecycle transitions: approve, send, record payment, void, and payment link
 * refresh. Extracted from InvoiceService as a focused collaborator.
 */
@Service
public class InvoiceTransitionService {

  private static final Logger log = LoggerFactory.getLogger(InvoiceTransitionService.class);

  private final InvoiceRepository invoiceRepository;
  private final InvoiceLineRepository lineRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final ExpenseRepository expenseRepository;
  private final MemberNameResolver memberNameResolver;
  private final InvoiceNumberService invoiceNumberService;
  private final IntegrationRegistry integrationRegistry;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final InvoiceValidationService invoiceValidationService;
  private final PaymentEventRepository paymentEventRepository;
  private final PaymentLinkService paymentLinkService;
  private final OrgSettingsRepository orgSettingsRepository;
  private final TaxCalculationService taxCalculationService;
  private final InvoiceRenderingService invoiceRenderingService;

  public InvoiceTransitionService(
      InvoiceRepository invoiceRepository,
      InvoiceLineRepository lineRepository,
      TimeEntryRepository timeEntryRepository,
      ExpenseRepository expenseRepository,
      MemberNameResolver memberNameResolver,
      InvoiceNumberService invoiceNumberService,
      IntegrationRegistry integrationRegistry,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      InvoiceValidationService invoiceValidationService,
      PaymentEventRepository paymentEventRepository,
      PaymentLinkService paymentLinkService,
      OrgSettingsRepository orgSettingsRepository,
      TaxCalculationService taxCalculationService,
      InvoiceRenderingService invoiceRenderingService) {
    this.invoiceRepository = invoiceRepository;
    this.lineRepository = lineRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.expenseRepository = expenseRepository;
    this.memberNameResolver = memberNameResolver;
    this.invoiceNumberService = invoiceNumberService;
    this.integrationRegistry = integrationRegistry;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.invoiceValidationService = invoiceValidationService;
    this.paymentEventRepository = paymentEventRepository;
    this.paymentLinkService = paymentLinkService;
    this.orgSettingsRepository = orgSettingsRepository;
    this.taxCalculationService = taxCalculationService;
    this.invoiceRenderingService = invoiceRenderingService;
  }

  @Transactional
  public InvoiceResponse approve(UUID invoiceId, UUID approvedBy) {
    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    var lines = lineRepository.findByInvoiceIdOrderBySortOrder(invoiceId);
    if (lines.isEmpty()) {
      throw new InvalidStateException(
          "No line items", "Invoice must have at least one line item before approval");
    }

    for (var line : lines) {
      if (line.getTimeEntryId() != null) {
        var timeEntry =
            timeEntryRepository
                .findById(line.getTimeEntryId())
                .orElseThrow(
                    () -> new ResourceNotFoundException("TimeEntry", line.getTimeEntryId()));
        if (timeEntry.getInvoiceId() != null && !timeEntry.getInvoiceId().equals(invoiceId)) {
          throw new ResourceConflictException(
              "Time entry already billed",
              "Time entry " + line.getTimeEntryId() + " is already linked to another invoice");
        }
      }
    }

    String invoiceNumber = invoiceNumberService.assignNumber();

    int defaultTermsDays =
        orgSettingsRepository
            .findForCurrentTenant()
            .map(s -> s.getBilling().getDefaultPaymentTermsDays())
            .orElse(BillingSettings.DEFAULT_PAYMENT_TERMS_DAYS);

    try {
      invoice.approve(invoiceNumber, approvedBy, defaultTermsDays);
    } catch (IllegalStateException e) {
      throw new ResourceConflictException("Invalid status transition", e.getMessage());
    }

    invoice = invoiceRepository.save(invoice);

    for (var line : lines) {
      if (line.getTimeEntryId() != null) {
        timeEntryRepository
            .findById(line.getTimeEntryId())
            .ifPresent(
                te -> {
                  te.setInvoiceId(invoiceId);
                  timeEntryRepository.save(te);
                });
      }
    }

    for (var line : lines) {
      if (line.getExpenseId() != null) {
        var expense =
            expenseRepository
                .findById(line.getExpenseId())
                .orElseThrow(() -> new ResourceNotFoundException("Expense", line.getExpenseId()));
        if (expense.getInvoiceId() != null && !expense.getInvoiceId().equals(invoiceId)) {
          throw new ResourceConflictException(
              "Expense already billed",
              "Expense " + line.getExpenseId() + " is already linked to another invoice");
        }
        if (expense.getInvoiceId() == null) {
          expense.markBilled(invoiceId);
          expenseRepository.save(expense);
        }
      }
    }

    log.info("Approved invoice {} with number {}", invoiceId, invoiceNumber);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invoice.approved")
            .entityType("invoice")
            .entityId(invoice.getId())
            .details(
                Map.of(
                    "invoice_number",
                    invoiceNumber,
                    "issue_date",
                    invoice.getIssueDate().toString(),
                    "total",
                    invoice.getTotal().toString(),
                    "time_entry_count",
                    String.valueOf(lines.stream().filter(l -> l.getTimeEntryId() != null).count()),
                    "expense_count",
                    String.valueOf(lines.stream().filter(l -> l.getExpenseId() != null).count())))
            .build());

    String tenantIdForEvent = RequestScopes.getTenantIdOrNull();
    String orgIdForEvent = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new InvoiceApprovedEvent(
            "invoice.approved",
            "invoice",
            invoice.getId(),
            null,
            approvedBy,
            resolveActorName(approvedBy),
            tenantIdForEvent,
            orgIdForEvent,
            Instant.now(),
            Map.of("invoice_number", invoiceNumber, "customer_name", invoice.getCustomerName()),
            invoice.getCreatedBy(),
            invoiceNumber,
            invoice.getCustomerName()));

    return invoiceRenderingService.buildResponse(invoice);
  }

  @Transactional
  public InvoiceResponse send(UUID invoiceId, SendInvoiceRequest request) {
    boolean overrideWarnings = request != null && request.overrideWarnings();
    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    var validationChecks = invoiceValidationService.validateInvoiceSend(invoice);
    if (invoiceValidationService.hasCriticalFailures(validationChecks) && !overrideWarnings) {
      throw new InvoiceValidationFailedException(validationChecks);
    }

    try {
      invoice.markSent();
    } catch (IllegalStateException e) {
      throw new ResourceConflictException("Invalid status transition", e.getMessage());
    }

    invoice = invoiceRepository.save(invoice);
    log.info("Marked invoice {} as sent", invoiceId);

    paymentLinkService.generatePaymentLink(invoice);

    UUID actorId = RequestScopes.MEMBER_ID.isBound() ? RequestScopes.MEMBER_ID.get() : null;

    // Derive the matter from the invoice lines (the invoice has no direct project_id column).
    // Single-matter invoices resolve to one project; spanning/empty invoices yield "". The
    // details.project_id field is load-bearing — the matter Activity feed
    // (AuditEventRepository.findByProjectId) and the portal Firm-actions trail
    // (findActivityFirmForCustomer) both scope on details->>'project_id', so without it this
    // client-safe milestone is silently dropped from both feeds (OBS-8801). Mirrors the
    // portal.invoice.paid emission in PaymentReconciliationService.
    var sentProjectIds = lineRepository.findDistinctProjectIdsByInvoiceId(invoice.getId());
    String sentProjectId = sentProjectIds.size() == 1 ? sentProjectIds.getFirst().toString() : "";

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invoice.sent")
            .entityType("invoice")
            .entityId(invoice.getId())
            .details(
                Map.of("invoice_number", invoice.getInvoiceNumber(), "project_id", sentProjectId))
            .build());

    String tenantIdForEvent = RequestScopes.getTenantIdOrNull();
    String orgIdForEvent = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new InvoiceSentEvent(
            "invoice.sent",
            "invoice",
            invoice.getId(),
            null,
            actorId,
            resolveActorName(actorId),
            tenantIdForEvent,
            orgIdForEvent,
            Instant.now(),
            Map.of(
                "invoice_number",
                invoice.getInvoiceNumber(),
                "customer_name",
                invoice.getCustomerName(),
                "total",
                invoice.getTotal().toString()),
            invoice.getCreatedBy(),
            invoice.getInvoiceNumber(),
            invoice.getCustomerName()));

    var sentLines = lineRepository.findByInvoiceIdOrderBySortOrder(invoice.getId());
    var settings = orgSettingsRepository.findForCurrentTenant().orElse(null);
    var sentTaxBreakdown = taxCalculationService.buildTaxBreakdown(sentLines);
    var sentHasPerLineTax = taxCalculationService.hasPerLineTax(sentLines);
    var taxContext =
        new TaxContext(
            sentTaxBreakdown,
            settings != null ? settings.getTax().getTaxRegistrationNumber() : null,
            settings != null ? settings.getTax().getTaxRegistrationLabel() : null,
            settings != null ? settings.getTax().getTaxLabel() : null,
            settings != null && settings.getTax().isTaxInclusive(),
            sentHasPerLineTax);

    eventPublisher.publishEvent(
        new InvoiceSyncEvent(
            invoice.getId(),
            invoice.getCustomerId(),
            invoice.getInvoiceNumber(),
            "SENT",
            invoice.getIssueDate(),
            invoice.getDueDate(),
            invoice.getSubtotal(),
            invoice.getTaxAmount(),
            invoice.getTotal(),
            invoice.getCurrency(),
            invoice.getNotes(),
            invoice.getPaymentUrl(),
            invoice.getPaymentSessionId(),
            orgIdForEvent,
            tenantIdForEvent,
            taxContext));

    return invoiceRenderingService.buildResponse(invoice);
  }

  /** Statuses that can take a payment (ADR-347). */
  private static final Set<InvoiceStatus> PAYABLE =
      Set.of(InvoiceStatus.SENT, InvoiceStatus.PARTIALLY_PAID);

  /** Statuses a payment can be reversed from (ADR-347 rationale 4). */
  private static final Set<InvoiceStatus> REVERSIBLE =
      Set.of(InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.PAID);

  /**
   * The one internal payment entry point (Phase 88 U15, ADR-347, architecture §13.3.8). Posts the
   * real amount: SENT/PARTIALLY_PAID only; an excess is refused (409) for manual entries and capped
   * at PAID + {@code invoice.payment_excess} for CARD/XERO/TRUST_TRANSFER sources.
   */
  @Transactional
  public InvoiceResponse postPayment(UUID invoiceId, PaymentPosting posting) {
    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));
    try {
      invoice.requireStatus(PAYABLE, "take a payment");
    } catch (IllegalStateException e) {
      throw new ResourceConflictException("Invalid status transition", e.getMessage());
    }

    // The excess check precedes every side effect, so a refused payment changes nothing.
    var balanceBefore = invoice.getBalanceDue();
    var excess = posting.amount().subtract(balanceBefore);
    if (excess.signum() > 0 && posting.refusesExcess()) {
      throw new ResourceConflictException(
          "Payment exceeds balance due",
          "Balance due is " + formatMoney(balanceBefore, invoice.getCurrency()));
    }

    var effective = posting;
    if (posting.isManualEntry()) {
      // Manual-path side effects carried over from the pre-U15 recordPayment(UUID, String).
      if (invoice.getPaymentSessionId() != null) {
        paymentLinkService.cancelActiveSession(invoice);
      }
      var paymentResult =
          resolvePaymentGateway()
              .recordManualPayment(
                  new PaymentRequest(
                      invoiceId,
                      posting.amount(),
                      invoice.getCurrency(),
                      "Payment for invoice " + invoice.getInvoiceNumber()));
      if (!paymentResult.success()) {
        throw new InvalidStateException("Payment failed", paymentResult.errorMessage());
      }
      if (posting.reference() == null) {
        effective = posting.withReference(paymentResult.paymentReference());
      }
    }

    // The event always records the real amount (an external overpayment included).
    paymentEventRepository.save(
        PaymentEvent.completed(
            invoiceId, invoice.getCurrency(), invoice.getPaymentDestination(), effective));

    invoice.applyPayment(effective.amount(), effective.reference());
    invoice = invoiceRepository.save(invoice);
    boolean paid = invoice.getStatus() == InvoiceStatus.PAID;

    log.info(
        "Posted {} {} payment on invoice {} (balance due {}, status {})",
        effective.method(),
        effective.amount(),
        invoiceId,
        invoice.getBalanceDue(),
        invoice.getStatus());

    UUID actorId = RequestScopes.MEMBER_ID.isBound() ? RequestScopes.MEMBER_ID.get() : null;
    UUID projectId = inferProjectIdFromInvoice(invoiceId);
    String reference = effective.reference() != null ? effective.reference() : "";

    var details = new LinkedHashMap<String, Object>();
    details.put("invoice_number", invoice.getInvoiceNumber());
    details.put("payment_reference", reference);
    details.put("amount", effective.amount().toPlainString());
    details.put("payment_method", effective.method().name());
    details.put("balance_due", invoice.getBalanceDue().toPlainString());
    details.put("total", invoice.getTotal().toString());
    if (paid) {
      details.put("paid_at", invoice.getPaidAt().toString());
    }
    if (projectId != null) {
      details.put("project_id", projectId.toString());
    }
    auditService.log(
        AuditEventBuilder.builder()
            // ADR-342: storage key, do not rename (invoice.paid pre-existing;
            // invoice.partially_paid
            // is the additive Phase 88 key)
            .eventType(paid ? "invoice.paid" : "invoice.partially_paid")
            .entityType("invoice")
            .entityId(invoice.getId())
            .details(details)
            .build());

    if (excess.signum() > 0) { // only reachable for CARD / XERO / TRUST_TRANSFER sources
      var excessDetails = new LinkedHashMap<String, Object>();
      excessDetails.put("invoice_number", invoice.getInvoiceNumber());
      excessDetails.put("amount", effective.amount().toPlainString());
      excessDetails.put("excess", excess.toPlainString());
      excessDetails.put("payment_method", effective.method().name());
      excessDetails.put("payment_reference", reference);
      if (projectId != null) {
        excessDetails.put("project_id", projectId.toString());
      }
      auditService.log(
          AuditEventBuilder.builder()
              // ADR-342: storage key, do not rename
              .eventType("invoice.payment_excess")
              .entityType("invoice")
              .entityId(invoice.getId())
              .details(excessDetails)
              .build());
    }

    String tenantIdForEvent = RequestScopes.getTenantIdOrNull();
    String orgIdForEvent = RequestScopes.getOrgIdOrNull();
    if (paid) {
      eventPublisher.publishEvent(
          new InvoicePaidEvent(
              "invoice.paid",
              "invoice",
              invoice.getId(),
              null,
              actorId,
              resolveActorName(actorId),
              tenantIdForEvent,
              orgIdForEvent,
              Instant.now(),
              Map.of(
                  "invoice_number",
                  invoice.getInvoiceNumber(),
                  "customer_name",
                  invoice.getCustomerName(),
                  "payment_reference",
                  reference,
                  "total",
                  invoice.getTotal().toString()),
              invoice.getCreatedBy(),
              invoice.getInvoiceNumber(),
              invoice.getCustomerName(),
              effective.reference()));
    } else {
      eventPublisher.publishEvent(
          new InvoicePartiallyPaidEvent(
              "invoice.partially_paid",
              "invoice",
              invoice.getId(),
              projectId,
              actorId,
              resolveActorName(actorId),
              tenantIdForEvent,
              orgIdForEvent,
              Instant.now(),
              Map.of(
                  "invoice_number",
                  invoice.getInvoiceNumber(),
                  "customer_name",
                  invoice.getCustomerName(),
                  "payment_reference",
                  reference,
                  "amount",
                  effective.amount().toPlainString(),
                  "balance_due",
                  invoice.getBalanceDue().toPlainString()),
              invoice.getCreatedBy(),
              invoice.getInvoiceNumber(),
              invoice.getCustomerName(),
              effective.reference(),
              effective.amount(),
              invoice.getAmountPaid(),
              invoice.getBalanceDue()));
    }
    publishSync(invoice, orgIdForEvent, tenantIdForEvent);

    return invoiceRenderingService.buildResponse(invoice);
  }

  /**
   * Settles the remaining balance in full through {@link #postPayment} (manual source) — the
   * automation "mark PAID" action, the legacy reference-only Record Payment body and {@code
   * InvoiceService.recordPayment(UUID, String)}.
   */
  @Transactional
  public InvoiceResponse settleBalance(UUID invoiceId, PaymentMethod method, String reference) {
    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));
    return postPayment(
        invoiceId, PaymentPosting.manual(invoice.getBalanceDue(), method, reference));
  }

  /**
   * INTERIM (until 622C): full settlement for the three external callers — trust fee-transfer
   * approval, pay-now webhook reconciliation, Xero sync — which save their own PaymentEvent, so
   * this path saves none (no double count). 622C re-points them to {@link #postPayment} with their
   * real amounts (N-5) and deletes this overload.
   */
  @Transactional
  public InvoiceResponse recordPayment(
      UUID invoiceId, String paymentReference, boolean fromWebhook) {
    if (!fromWebhook) {
      return settleBalance(invoiceId, PaymentMethod.OTHER, paymentReference);
    }

    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    if (invoice.getStatus() == InvoiceStatus.PAID) {
      return invoiceRenderingService.buildResponse(invoice);
    }

    try {
      // Settles the remaining balance → PAID, amount_paid = total (SENT or PARTIALLY_PAID).
      invoice.recordPayment(paymentReference);
    } catch (IllegalStateException e) {
      throw new ResourceConflictException("Invalid status transition", e.getMessage());
    }
    invoice = invoiceRepository.save(invoice);

    log.info(
        "Recorded webhook payment for invoice {} with reference {}", invoiceId, paymentReference);

    UUID actorId = RequestScopes.MEMBER_ID.isBound() ? RequestScopes.MEMBER_ID.get() : null;
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invoice.paid")
            .entityType("invoice")
            .entityId(invoice.getId())
            .actorType("SYSTEM")
            .source("WEBHOOK")
            .details(
                Map.of(
                    "invoice_number",
                    invoice.getInvoiceNumber(),
                    "payment_reference",
                    paymentReference != null ? paymentReference : "",
                    "total",
                    invoice.getTotal().toString(),
                    "paid_at",
                    invoice.getPaidAt().toString()))
            .build());

    String tenantIdForEvent = RequestScopes.getTenantIdOrNull();
    String orgIdForEvent = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new InvoicePaidEvent(
            "invoice.paid",
            "invoice",
            invoice.getId(),
            null,
            actorId,
            resolveActorName(actorId),
            tenantIdForEvent,
            orgIdForEvent,
            Instant.now(),
            Map.of(
                "invoice_number",
                invoice.getInvoiceNumber(),
                "customer_name",
                invoice.getCustomerName(),
                "payment_reference",
                paymentReference != null ? paymentReference : "",
                "total",
                invoice.getTotal().toString()),
            invoice.getCreatedBy(),
            invoice.getInvoiceNumber(),
            invoice.getCustomerName(),
            paymentReference));

    publishSync(invoice, orgIdForEvent, tenantIdForEvent);

    return invoiceRenderingService.buildResponse(invoice);
  }

  /** Portal sync with the invoice's current status ({@code amountPaid} on the event is 622C's). */
  private void publishSync(Invoice invoice, String orgId, String tenantId) {
    eventPublisher.publishEvent(
        new InvoiceSyncEvent(
            invoice.getId(),
            invoice.getCustomerId(),
            invoice.getInvoiceNumber(),
            invoice.getStatus().name(),
            invoice.getIssueDate(),
            invoice.getDueDate(),
            invoice.getSubtotal(),
            invoice.getTaxAmount(),
            invoice.getTotal(),
            invoice.getCurrency(),
            invoice.getNotes(),
            invoice.getPaymentUrl(),
            invoice.getPaymentSessionId(),
            orgId,
            tenantId,
            null));
  }

  /** "R 37 182,00" for ZAR — fixed symbols, stable across JDK locale data. */
  private static String formatMoney(BigDecimal amount, String currency) {
    var symbols = new DecimalFormatSymbols(Locale.ENGLISH);
    symbols.setGroupingSeparator(' ');
    symbols.setDecimalSeparator(',');
    var formatted = new DecimalFormat("#,##0.00", symbols).format(amount);
    return (currency == null || "ZAR".equals(currency) ? "R" : currency) + " " + formatted;
  }

  @Transactional
  public InvoiceResponse refreshPaymentLink(UUID invoiceId) {
    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    if (invoice.getStatus() != InvoiceStatus.SENT) {
      throw new ResourceConflictException(
          "Invalid status", "Only sent invoices can have their payment link refreshed");
    }

    paymentLinkService.refreshPaymentLink(invoice);

    return invoiceRenderingService.buildResponse(invoice);
  }

  /**
   * Reverses a previously-recorded payment on an invoice. Called from the trust-side reversal path
   * ({@code TrustTransactionService.reverseTransaction}) when a FEE_TRANSFER linked to this invoice
   * is reversed.
   *
   * <p>Inverse of {@link #postPayment}: deletes the named payment_event row, then recomputes {@code
   * amount_paid = LEAST(total, Σ remaining COMPLETED)} (Phase 88 U15, ADR-347 rationale 4) — SENT
   * at zero, PAID while still fully covered ({@code paidAt} kept), otherwise PARTIALLY_PAID.
   *
   * <p>Audit-event types: {@code invoice.payment_reversed} (full) or {@code
   * invoice.payment_partially_reversed} (partial). These are referenced by the portal /activity
   * page (slice 20 / E4.3) and must remain stable.
   *
   * <p>Uses default {@link org.springframework.transaction.annotation.Propagation#REQUIRED} so it
   * joins the caller's transaction — trust ledger and invoice changes commit atomically or not at
   * all.
   *
   * @throws InvalidStateException when the invoice is not PAID/PARTIALLY_PAID, the payment event
   *     doesn't belong to the invoice, or the payment event is not in COMPLETED status (idempotency
   *     guard).
   * @throws ResourceNotFoundException when the invoice or payment event cannot be found.
   */
  @Transactional
  public void reversePayment(UUID invoiceId, UUID paymentEventId) {
    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    if (!REVERSIBLE.contains(invoice.getStatus())) {
      throw new InvalidStateException(
          "Invalid status for payment reversal",
          "Only paid or partially paid invoices can have a payment reversed (current status: "
              + invoice.getStatus()
              + ")");
    }

    var paymentEvent =
        paymentEventRepository
            .findById(paymentEventId)
            .orElseThrow(() -> new ResourceNotFoundException("PaymentEvent", paymentEventId));

    if (!paymentEvent.getInvoiceId().equals(invoiceId)) {
      throw new InvalidStateException(
          "Mismatched payment event",
          "Payment event " + paymentEventId + " does not belong to invoice " + invoiceId);
    }

    if (paymentEvent.getStatus() != PaymentEventStatus.COMPLETED) {
      throw new InvalidStateException(
          "Payment event not reversible",
          "Only COMPLETED payment events can be reversed (current status: "
              + paymentEvent.getStatus()
              + ")");
    }

    var amount = paymentEvent.getAmount();
    paymentEventRepository.delete(paymentEvent);
    paymentEventRepository.flush();

    var remainingCompleted =
        paymentEventRepository.findByInvoiceIdAndStatus(invoiceId, PaymentEventStatus.COMPLETED);
    var remainingSum =
        remainingCompleted.stream()
            .map(PaymentEvent::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    var statusBefore = invoice.getStatus();
    try {
      invoice.reapplyPayments(remainingSum); // amount_paid = LEAST(total, Σ remaining)
    } catch (IllegalStateException e) {
      throw new ResourceConflictException("Invalid status transition", e.getMessage());
    }
    invoice = invoiceRepository.save(invoice);

    UUID actorId = RequestScopes.MEMBER_ID.isBound() ? RequestScopes.MEMBER_ID.get() : null;
    String tenantIdForEvent = RequestScopes.getTenantIdOrNull();
    String orgIdForEvent = RequestScopes.getOrgIdOrNull();
    UUID projectId = inferProjectIdFromInvoice(invoiceId);

    if (remainingCompleted.isEmpty()) {
      log.info(
          "Reversed payment {} on invoice {} — {} -> SENT (no other completed payments)",
          paymentEventId,
          invoiceId,
          statusBefore);

      auditService.log(
          AuditEventBuilder.builder()
              .eventType("invoice.payment_reversed")
              .entityType("invoice")
              .entityId(invoice.getId())
              .details(buildReversalAuditDetails(invoice, paymentEventId, projectId, amount, false))
              .build());

      eventPublisher.publishEvent(
          new InvoicePaymentReversedEvent(
              "invoice.payment_reversed",
              "invoice",
              invoice.getId(),
              projectId,
              actorId,
              resolveActorName(actorId),
              tenantIdForEvent,
              orgIdForEvent,
              Instant.now(),
              Map.of(
                  "invoice_number",
                  invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "",
                  "customer_name",
                  invoice.getCustomerName(),
                  "payment_event_id",
                  paymentEventId.toString(),
                  "amount",
                  amount.toString()),
              invoice.getCreatedBy(),
              invoice.getInvoiceNumber(),
              invoice.getCustomerName(),
              invoice.getCustomerId(),
              paymentEventId,
              amount));

      publishSync(invoice, orgIdForEvent, tenantIdForEvent);
    } else {
      log.info(
          "Reversed payment {} on invoice {} — {} -> {} ({} other completed payments remain)",
          paymentEventId,
          invoiceId,
          statusBefore,
          invoice.getStatus(),
          remainingCompleted.size());

      auditService.log(
          AuditEventBuilder.builder()
              .eventType("invoice.payment_partially_reversed")
              .entityType("invoice")
              .entityId(invoice.getId())
              .details(buildReversalAuditDetails(invoice, paymentEventId, projectId, amount, true))
              .build());

      eventPublisher.publishEvent(
          new InvoicePaymentPartiallyReversedEvent(
              "invoice.payment_partially_reversed",
              "invoice",
              invoice.getId(),
              projectId,
              actorId,
              resolveActorName(actorId),
              tenantIdForEvent,
              orgIdForEvent,
              Instant.now(),
              Map.of(
                  "invoice_number",
                  invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "",
                  "customer_name",
                  invoice.getCustomerName(),
                  "payment_event_id",
                  paymentEventId.toString(),
                  "amount",
                  amount.toString(),
                  "remaining_completed_payments",
                  String.valueOf(remainingCompleted.size())),
              invoice.getCreatedBy(),
              invoice.getInvoiceNumber(),
              invoice.getCustomerName(),
              invoice.getCustomerId(),
              paymentEventId,
              amount));
      if (invoice.getStatus() != statusBefore) {
        publishSync(invoice, orgIdForEvent, tenantIdForEvent);
      }
    }
  }

  private Map<String, Object> buildReversalAuditDetails(
      Invoice invoice, UUID paymentEventId, UUID projectId, BigDecimal amount, boolean partial) {
    var details = new LinkedHashMap<String, Object>();
    details.put(
        "invoice_number", invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "");
    details.put("customer_id", invoice.getCustomerId().toString());
    details.put("payment_event_id", paymentEventId.toString());
    details.put("amount", amount.toString());
    details.put("partial", String.valueOf(partial));
    details.put("amount_paid", invoice.getAmountPaid().toPlainString());
    details.put("balance_due", invoice.getBalanceDue().toPlainString());
    if (projectId != null) {
      details.put("project_id", projectId.toString());
    }
    return details;
  }

  /**
   * Returns the single distinct projectId across the invoice's lines, or null when the invoice
   * spans multiple matters or has no project-bound lines. Mirrors the inference used by the trust
   * fee-transfer recording path (GAP-L-69) so reversal events carry the same matter binding.
   */
  private UUID inferProjectIdFromInvoice(UUID invoiceId) {
    var distinctProjectIds = lineRepository.findDistinctProjectIdsByInvoiceId(invoiceId);
    return distinctProjectIds.size() == 1 ? distinctProjectIds.get(0) : null;
  }

  @Transactional
  public InvoiceResponse voidInvoice(UUID invoiceId) {
    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    try {
      invoice.voidInvoice();
    } catch (IllegalStateException e) {
      throw new ResourceConflictException("Invalid status transition", e.getMessage());
    }

    invoice = invoiceRepository.save(invoice);

    var lines = lineRepository.findByInvoiceIdOrderBySortOrder(invoiceId);
    for (var line : lines) {
      if (line.getTimeEntryId() != null) {
        timeEntryRepository
            .findById(line.getTimeEntryId())
            .ifPresent(
                te -> {
                  te.setInvoiceId(null);
                  timeEntryRepository.save(te);
                });
      }
    }

    for (var line : lines) {
      if (line.getExpenseId() != null) {
        expenseRepository
            .findById(line.getExpenseId())
            .ifPresent(
                expense -> {
                  try {
                    expense.unbill();
                    expenseRepository.save(expense);
                  } catch (IllegalStateException e) {
                    log.warn(
                        "Could not unbill expense {} on invoice {}: {}",
                        line.getExpenseId(),
                        invoiceId,
                        e.getMessage());
                  }
                });
      }
    }

    log.info("Voided invoice {}", invoiceId);

    UUID actorId = RequestScopes.MEMBER_ID.isBound() ? RequestScopes.MEMBER_ID.get() : null;
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invoice.voided")
            .entityType("invoice")
            .entityId(invoice.getId())
            .details(
                Map.of(
                    "invoice_number",
                    invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "",
                    "total",
                    invoice.getTotal().toString(),
                    "reverted_time_entry_count",
                    String.valueOf(lines.stream().filter(l -> l.getTimeEntryId() != null).count()),
                    "reverted_expense_count",
                    String.valueOf(lines.stream().filter(l -> l.getExpenseId() != null).count())))
            .build());

    String tenantIdForEvent = RequestScopes.getTenantIdOrNull();
    String orgIdForEvent = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(
        new InvoiceVoidedEvent(
            "invoice.voided",
            "invoice",
            invoice.getId(),
            null,
            actorId,
            resolveActorName(actorId),
            tenantIdForEvent,
            orgIdForEvent,
            Instant.now(),
            Map.of(
                "invoice_number",
                invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "",
                "customer_name",
                invoice.getCustomerName(),
                "total",
                invoice.getTotal().toString()),
            invoice.getCreatedBy(),
            invoice.getInvoiceNumber(),
            invoice.getCustomerName(),
            invoice.getApprovedBy()));

    eventPublisher.publishEvent(
        new InvoiceSyncEvent(
            invoice.getId(),
            invoice.getCustomerId(),
            invoice.getInvoiceNumber(),
            "VOID",
            invoice.getIssueDate(),
            invoice.getDueDate(),
            invoice.getSubtotal(),
            invoice.getTaxAmount(),
            invoice.getTotal(),
            invoice.getCurrency(),
            invoice.getNotes(),
            invoice.getPaymentUrl(),
            invoice.getPaymentSessionId(),
            orgIdForEvent,
            tenantIdForEvent,
            null));

    return invoiceRenderingService.buildResponse(invoice);
  }

  // --- Private helpers ---

  private String resolveActorName(UUID memberId) {
    if (memberId == null) return "System";
    return memberNameResolver.resolveName(memberId);
  }

  private PaymentGateway resolvePaymentGateway() {
    return integrationRegistry.resolve(IntegrationDomain.PAYMENT, PaymentGateway.class);
  }
}
