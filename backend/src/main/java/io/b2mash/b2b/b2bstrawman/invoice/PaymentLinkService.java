package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.integration.payment.CheckoutRequest;
import io.b2mash.b2b.b2bstrawman.integration.payment.PaymentGateway;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentLinkService {

  private static final Logger log = LoggerFactory.getLogger(PaymentLinkService.class);

  private final IntegrationRegistry integrationRegistry;
  private final InvoiceRepository invoiceRepository;
  private final PaymentEventRepository paymentEventRepository;
  private final AuditService auditService;
  private final String portalBaseUrl;

  public PaymentLinkService(
      IntegrationRegistry integrationRegistry,
      InvoiceRepository invoiceRepository,
      PaymentEventRepository paymentEventRepository,
      AuditService auditService,
      @Value("${docteams.app.portal-base-url}") String portalBaseUrl) {
    this.integrationRegistry = integrationRegistry;
    this.invoiceRepository = invoiceRepository;
    this.paymentEventRepository = paymentEventRepository;
    this.auditService = auditService;
    this.portalBaseUrl = portalBaseUrl;
  }

  @Transactional
  public void generatePaymentLink(Invoice invoice) {
    var gateway = integrationRegistry.resolve(IntegrationDomain.PAYMENT, PaymentGateway.class);

    String tenantSchema = RequestScopes.requireTenantId();

    var checkoutRequest =
        new CheckoutRequest(
            invoice.getId(),
            invoice.getInvoiceNumber(),
            invoice.getTotal(),
            invoice.getCurrency(),
            invoice.getCustomerEmail(),
            invoice.getCustomerName(),
            buildSuccessUrl(invoice.getId()),
            buildCancelUrl(invoice.getId()),
            Map.of("tenantSchema", tenantSchema));

    var result = gateway.createCheckoutSession(checkoutRequest);

    if (!result.success() || !result.supported()) {
      log.debug(
          "Payment link not generated for invoice {}: success={}, supported={}",
          invoice.getId(),
          result.success(),
          result.supported());
      return;
    }

    invoice.setPaymentSessionId(result.sessionId());
    invoice.setPaymentUrl(result.redirectUrl());
    invoiceRepository.save(invoice);

    var paymentEvent =
        new PaymentEvent(
            invoice.getId(),
            gateway.providerId(),
            result.sessionId(),
            PaymentEventStatus.CREATED,
            invoice.getTotal(),
            invoice.getCurrency(),
            invoice.getPaymentDestination());
    paymentEventRepository.save(paymentEvent);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("payment.session.created")
            .entityType("invoice")
            .entityId(invoice.getId())
            .actorType("SYSTEM")
            .source("SYSTEM")
            .details(
                Map.of(
                    "provider",
                    gateway.providerId(),
                    "session_id",
                    result.sessionId(),
                    "amount",
                    invoice.getTotal().toPlainString()))
            .build());

    log.info("Generated payment link for invoice {} via {}", invoice.getId(), gateway.providerId());
  }

  @Transactional
  public void refreshPaymentLink(Invoice invoice) {
    cancelActiveSessionInternal(invoice);
    generatePaymentLink(invoice);
  }

  @Transactional
  public void cancelActiveSession(Invoice invoice) {
    cancelActiveSessionInternal(invoice);
    invoiceRepository.save(invoice);
  }

  private void cancelActiveSessionInternal(Invoice invoice) {
    if (invoice.getPaymentSessionId() == null) {
      return;
    }

    var existingEvent =
        paymentEventRepository.findBySessionIdAndStatus(
            invoice.getPaymentSessionId(), PaymentEventStatus.CREATED);

    existingEvent.ifPresent(
        event -> {
          event.updateStatus(PaymentEventStatus.CANCELLED);
          paymentEventRepository.save(event);
        });

    var gateway = integrationRegistry.resolve(IntegrationDomain.PAYMENT, PaymentGateway.class);
    gateway.expireSession(invoice.getPaymentSessionId());

    invoice.setPaymentSessionId(null);
    invoice.setPaymentUrl(null);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("payment.session.cancelled")
            .entityType("invoice")
            .entityId(invoice.getId())
            .actorType("SYSTEM")
            .source("SYSTEM")
            .details(Map.of("action", "session_cancelled"))
            .build());

    log.info("Cancelled active payment session for invoice {}", invoice.getId());
  }

  String buildSuccessUrl(UUID invoiceId) {
    return portalBaseUrl + "/invoices/" + invoiceId + "/payment-success";
  }

  String buildCancelUrl(UUID invoiceId) {
    return portalBaseUrl + "/invoices/" + invoiceId + "/payment-cancelled";
  }
}
