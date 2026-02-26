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
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/webhooks/payment")
public class PaymentWebhookController {

  private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);
  private static final Set<String> ALLOWED_PROVIDERS = Set.of("stripe", "payfast");
  private static final Pattern TENANT_SCHEMA_PATTERN = Pattern.compile("tenant_[a-f0-9]+");

  private final IntegrationRegistry integrationRegistry;
  private final InvoiceRepository invoiceRepository;
  private final PaymentEventRepository paymentEventRepository;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;

  public PaymentWebhookController(
      IntegrationRegistry integrationRegistry,
      InvoiceRepository invoiceRepository,
      PaymentEventRepository paymentEventRepository,
      AuditService auditService,
      ObjectMapper objectMapper) {
    this.integrationRegistry = integrationRegistry;
    this.invoiceRepository = invoiceRepository;
    this.paymentEventRepository = paymentEventRepository;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  @PostMapping("/{provider}")
  public ResponseEntity<Void> handleWebhook(
      @PathVariable String provider,
      @RequestBody String payload,
      @RequestHeader Map<String, String> headers) {

    if (!ALLOWED_PROVIDERS.contains(provider)) {
      log.warn("Received payment webhook for unknown provider: {}", provider);
      return ResponseEntity.ok().build();
    }

    String tenantSchema = extractTenantSchema(provider, payload);
    if (tenantSchema == null) {
      log.warn("Could not extract tenant schema from {} webhook payload", provider);
      return ResponseEntity.ok().build();
    }

    try {
      ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
          .run(
              () -> {
                var gateway =
                    integrationRegistry.resolveBySlug(
                        IntegrationDomain.PAYMENT, provider, PaymentGateway.class);
                var result = gateway.handleWebhook(payload, headers);

                if (!result.verified()) {
                  log.warn(
                      "Unverified {} webhook for tenant {}: eventType={}",
                      provider,
                      tenantSchema,
                      result.eventType());
                  return;
                }

                if (result.status() == PaymentStatus.COMPLETED && result.sessionId() != null) {
                  processCompletedPayment(gateway, result, tenantSchema);
                }
              });
    } catch (Exception e) {
      log.error("Error processing {} webhook for tenant {}", provider, tenantSchema, e);
    }

    return ResponseEntity.ok().build();
  }

  private void processCompletedPayment(
      PaymentGateway gateway, WebhookResult result, String tenantSchema) {
    var invoiceOpt =
        paymentEventRepository
            .findBySessionIdAndStatus(result.sessionId(), PaymentEventStatus.CREATED)
            .map(event -> invoiceRepository.findById(event.getInvoiceId()).orElse(null));

    if (invoiceOpt.isEmpty() || invoiceOpt.get() == null) {
      log.warn("No invoice found for session {} in tenant {}", result.sessionId(), tenantSchema);
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

  private String extractTenantSchema(String provider, String payload) {
    try {
      String schema =
          switch (provider) {
            case "stripe" -> extractStripeSchema(payload);
            case "payfast" -> extractPayFastSchema(payload);
            default -> null;
          };

      if (schema != null && TENANT_SCHEMA_PATTERN.matcher(schema).matches()) {
        return schema;
      }

      if (schema != null) {
        log.warn("Invalid tenant schema format: {}", schema);
      }
      return null;
    } catch (Exception e) {
      log.warn("Failed to extract tenant schema from {} payload: {}", provider, e.getMessage());
      return null;
    }
  }

  private String extractStripeSchema(String payload) {
    try {
      JsonNode root = objectMapper.readTree(payload);
      JsonNode metadata = root.path("data").path("object").path("metadata");
      JsonNode tenantNode = metadata.path("tenantSchema");
      return tenantNode.isMissingNode() ? null : tenantNode.asText();
    } catch (Exception e) {
      return null;
    }
  }

  private String extractPayFastSchema(String payload) {
    try {
      for (String param : payload.split("&")) {
        String[] parts = param.split("=", 2);
        if (parts.length == 2 && "custom_str1".equals(parts[0])) {
          return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
        }
      }
      return null;
    } catch (Exception e) {
      return null;
    }
  }
}
