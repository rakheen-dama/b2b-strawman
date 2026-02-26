package io.b2mash.b2b.b2bstrawman.integration.payment;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEvent;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEventRepository;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEventStatus;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Processes verified payment webhooks from external PSP providers. */
@Service
public class PaymentWebhookService {

  private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

  private final IntegrationRegistry integrationRegistry;
  private final InvoiceRepository invoiceRepository;
  private final PaymentEventRepository paymentEventRepository;
  private final AuditService auditService;

  public PaymentWebhookService(
      IntegrationRegistry integrationRegistry,
      InvoiceRepository invoiceRepository,
      PaymentEventRepository paymentEventRepository,
      AuditService auditService) {
    this.integrationRegistry = integrationRegistry;
    this.invoiceRepository = invoiceRepository;
    this.paymentEventRepository = paymentEventRepository;
    this.auditService = auditService;
  }

  /**
   * Verifies and processes a payment webhook. Returns silently for unverified or non-actionable
   * webhooks.
   */
  @Transactional
  public void processWebhook(String provider, String payload, Map<String, String> headers) {
    var gateway =
        integrationRegistry.resolveBySlug(
            IntegrationDomain.PAYMENT, provider, PaymentGateway.class);
    var result = gateway.handleWebhook(payload, headers);

    if (!result.verified()) {
      log.warn("Unverified {} webhook: eventType={}", provider, result.eventType());
      return;
    }

    if (result.status() == PaymentStatus.COMPLETED && result.sessionId() != null) {
      processCompletedPayment(gateway, result);
    }
  }

  private void processCompletedPayment(PaymentGateway gateway, WebhookResult result) {
    var invoiceOpt =
        paymentEventRepository
            .findBySessionIdAndStatus(result.sessionId(), PaymentEventStatus.CREATED)
            .flatMap(event -> invoiceRepository.findById(event.getInvoiceId()));

    if (invoiceOpt.isEmpty()) {
      log.warn("No invoice found for session {}", result.sessionId());
      return;
    }

    Invoice invoice = invoiceOpt.get();

    if (invoice.getStatus() != InvoiceStatus.SENT) {
      log.warn(
          "Invoice {} not in SENT status (current: {}), skipping payment",
          invoice.getId(),
          invoice.getStatus());
      return;
    }

    String paymentRef =
        result.paymentReference() != null ? result.paymentReference() : result.sessionId();
    invoice.recordPayment(paymentRef);
    invoiceRepository.save(invoice);

    var completedEvent =
        new PaymentEvent(
            invoice.getId(),
            gateway.providerId(),
            result.sessionId(),
            PaymentEventStatus.COMPLETED,
            invoice.getTotal(),
            invoice.getCurrency(),
            invoice.getPaymentDestination());
    if (result.paymentReference() != null) {
      completedEvent.setPaymentReference(result.paymentReference());
    }
    paymentEventRepository.save(completedEvent);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("payment.completed")
            .entityType("invoice")
            .entityId(invoice.getId())
            .actorType("SYSTEM")
            .source("WEBHOOK")
            .details(
                Map.of(
                    "provider",
                    gateway.providerId(),
                    "session_id",
                    result.sessionId(),
                    "payment_reference",
                    paymentRef,
                    "amount",
                    invoice.getTotal().toPlainString()))
            .build());

    log.info(
        "Processed completed payment for invoice {} via {} webhook",
        invoice.getId(),
        gateway.providerId());
  }
}
