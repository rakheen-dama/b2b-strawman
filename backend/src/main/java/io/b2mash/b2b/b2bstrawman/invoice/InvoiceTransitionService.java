package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.InvoiceSyncEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.TaxContext;
import io.b2mash.b2b.b2bstrawman.event.InvoiceApprovedEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoicePaidEvent;
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
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.tax.TaxCalculationService;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.time.Instant;
import java.util.Map;
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

    try {
      invoice.approve(invoiceNumber, approvedBy);
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
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invoice.sent")
            .entityType("invoice")
            .entityId(invoice.getId())
            .details(Map.of("invoice_number", invoice.getInvoiceNumber()))
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
            settings != null ? settings.getTaxRegistrationNumber() : null,
            settings != null ? settings.getTaxRegistrationLabel() : null,
            settings != null ? settings.getTaxLabel() : null,
            settings != null && settings.isTaxInclusive(),
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

  @Transactional
  public InvoiceResponse recordPayment(UUID invoiceId, String paymentReference) {
    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    if (invoice.getStatus() != InvoiceStatus.SENT) {
      throw new ResourceConflictException(
          "Invalid status transition", "Only sent invoices can be paid");
    }

    if (invoice.getPaymentSessionId() != null) {
      paymentLinkService.cancelActiveSession(invoice);
    }

    var gateway = resolvePaymentGateway();
    var paymentRequest =
        new PaymentRequest(
            invoiceId,
            invoice.getTotal(),
            invoice.getCurrency(),
            "Payment for invoice " + invoice.getInvoiceNumber());
    var paymentResult = gateway.recordManualPayment(paymentRequest);

    if (!paymentResult.success()) {
      throw new InvalidStateException("Payment failed", paymentResult.errorMessage());
    }

    String effectiveReference =
        paymentReference != null ? paymentReference : paymentResult.paymentReference();

    invoice.recordPayment(effectiveReference);

    invoice = invoiceRepository.save(invoice);

    var manualEvent =
        new PaymentEvent(
            invoiceId,
            "manual",
            null,
            PaymentEventStatus.COMPLETED,
            invoice.getTotal(),
            invoice.getCurrency(),
            invoice.getPaymentDestination());
    if (effectiveReference != null) {
      manualEvent.setPaymentReference(effectiveReference);
    }
    paymentEventRepository.save(manualEvent);

    log.info("Recorded payment for invoice {} with reference {}", invoiceId, effectiveReference);

    UUID actorId = RequestScopes.MEMBER_ID.isBound() ? RequestScopes.MEMBER_ID.get() : null;
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("invoice.paid")
            .entityType("invoice")
            .entityId(invoice.getId())
            .details(
                Map.of(
                    "invoice_number",
                    invoice.getInvoiceNumber(),
                    "payment_reference",
                    effectiveReference,
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
                effectiveReference,
                "total",
                invoice.getTotal().toString()),
            invoice.getCreatedBy(),
            invoice.getInvoiceNumber(),
            invoice.getCustomerName(),
            effectiveReference));

    eventPublisher.publishEvent(
        new InvoiceSyncEvent(
            invoice.getId(),
            invoice.getCustomerId(),
            invoice.getInvoiceNumber(),
            "PAID",
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

  @Transactional
  public InvoiceResponse recordPayment(
      UUID invoiceId, String paymentReference, boolean fromWebhook) {
    if (!fromWebhook) {
      return recordPayment(invoiceId, paymentReference);
    }

    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    if (invoice.getStatus() == InvoiceStatus.PAID) {
      return invoiceRenderingService.buildResponse(invoice);
    }

    if (invoice.getStatus() != InvoiceStatus.SENT) {
      throw new ResourceConflictException(
          "Invalid status transition", "Only sent invoices can be paid");
    }

    invoice.recordPayment(paymentReference);
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

    eventPublisher.publishEvent(
        new InvoiceSyncEvent(
            invoice.getId(),
            invoice.getCustomerId(),
            invoice.getInvoiceNumber(),
            "PAID",
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
   * <p>Inverse of {@link #recordPayment(UUID, String)} for the manual-payment path: deletes the
   * named payment_event row, then either flips the invoice {@code PAID → SENT} (no other COMPLETED
   * payments remain) or keeps it {@code PAID} (multi-payment partial reversal).
   *
   * <p>Audit-event types: {@code invoice.payment_reversed} (full) or {@code
   * invoice.payment_partially_reversed} (partial). These are referenced by the portal /activity
   * page (slice 20 / E4.3) and must remain stable.
   *
   * <p>Uses default {@link org.springframework.transaction.annotation.Propagation#REQUIRED} so it
   * joins the caller's transaction — trust ledger and invoice changes commit atomically or not at
   * all.
   *
   * @throws InvalidStateException when the invoice is not PAID, the payment event doesn't belong to
   *     the invoice, or the payment event is not in COMPLETED status (idempotency guard).
   * @throws ResourceNotFoundException when the invoice or payment event cannot be found.
   */
  @Transactional
  public void reversePayment(UUID invoiceId, UUID paymentEventId) {
    var invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

    if (invoice.getStatus() != InvoiceStatus.PAID) {
      throw new InvalidStateException(
          "Invalid status for payment reversal",
          "Only paid invoices can have a payment reversed (current status: "
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

    UUID actorId = RequestScopes.MEMBER_ID.isBound() ? RequestScopes.MEMBER_ID.get() : null;
    String tenantIdForEvent = RequestScopes.getTenantIdOrNull();
    String orgIdForEvent = RequestScopes.getOrgIdOrNull();
    UUID projectId = inferProjectIdFromInvoice(invoiceId);

    if (remainingCompleted.isEmpty()) {
      try {
        invoice.reversePayment();
      } catch (IllegalStateException e) {
        throw new ResourceConflictException("Invalid status transition", e.getMessage());
      }
      invoice = invoiceRepository.save(invoice);

      log.info(
          "Reversed payment {} on invoice {} — flipping PAID -> SENT (no other completed payments)",
          paymentEventId,
          invoiceId);

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
              null));
    } else {
      log.info(
          "Reversed payment {} on invoice {} — invoice stays PAID ({} other completed payments remain)",
          paymentEventId,
          invoiceId,
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
    }
  }

  private Map<String, Object> buildReversalAuditDetails(
      Invoice invoice,
      UUID paymentEventId,
      UUID projectId,
      java.math.BigDecimal amount,
      boolean partial) {
    var details = new java.util.LinkedHashMap<String, Object>();
    details.put(
        "invoice_number", invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "");
    details.put("customer_id", invoice.getCustomerId().toString());
    details.put("payment_event_id", paymentEventId.toString());
    details.put("amount", amount.toString());
    details.put("partial", String.valueOf(partial));
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
