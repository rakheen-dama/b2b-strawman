package io.b2mash.b2b.b2bstrawman.integration.email;

import com.sendgrid.helpers.eventwebhook.EventWebhook;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import java.security.interfaces.ECPublicKey;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class EmailWebhookService {

  private static final Logger log = LoggerFactory.getLogger(EmailWebhookService.class);

  private static final Map<String, EmailDeliveryStatus> EVENT_TYPE_MAP =
      Map.of(
          "delivered", EmailDeliveryStatus.DELIVERED,
          "bounce", EmailDeliveryStatus.BOUNCED,
          "dropped", EmailDeliveryStatus.FAILED);

  private final EmailDeliveryLogService deliveryLogService;
  private final NotificationService notificationService;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;
  private final String webhookVerificationKey;

  public EmailWebhookService(
      EmailDeliveryLogService deliveryLogService,
      NotificationService notificationService,
      AuditService auditService,
      ObjectMapper objectMapper,
      @Value("${docteams.email.sendgrid.webhook-verification-key:}")
          String webhookVerificationKey) {
    this.deliveryLogService = deliveryLogService;
    this.notificationService = notificationService;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
    this.webhookVerificationKey = webhookVerificationKey;
  }

  public void processWebhook(String provider, String payload, String signature, String timestamp) {
    if (!"sendgrid".equals(provider)) {
      log.warn("Unsupported webhook provider: {}", provider);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported provider");
    }

    verifySignature(payload, signature, timestamp);

    List<Map<String, Object>> events = parseEvents(payload);
    for (Map<String, Object> event : events) {
      processEvent(event);
    }
  }

  private void verifySignature(String payload, String signature, String timestamp) {
    if (webhookVerificationKey == null || webhookVerificationKey.isBlank()) {
      log.debug("Webhook verification key not configured, skipping signature verification");
      return;
    }

    if (signature == null || timestamp == null) {
      log.warn("Missing webhook signature or timestamp headers");
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
    }

    try {
      EventWebhook ew = new EventWebhook();
      ECPublicKey ecPublicKey = ew.ConvertPublicKeyToECDSA(webhookVerificationKey);
      boolean isValid = ew.VerifySignature(ecPublicKey, payload, signature, timestamp);
      if (!isValid) {
        log.warn("Invalid webhook signature");
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
      }
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      log.error("Webhook signature verification failed: {}", e.getMessage());
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
    }
  }

  private List<Map<String, Object>> parseEvents(String payload) {
    try {
      return objectMapper.readValue(payload, new TypeReference<>() {});
    } catch (JacksonException e) {
      log.error("Failed to parse webhook payload: {}", e.getMessage());
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payload");
    }
  }

  @SuppressWarnings("unchecked")
  private void processEvent(Map<String, Object> event) {
    String eventType = (String) event.get("event");
    if (eventType == null || !EVENT_TYPE_MAP.containsKey(eventType)) {
      return;
    }

    EmailDeliveryStatus newStatus = EVENT_TYPE_MAP.get(eventType);

    Map<String, Object> uniqueArgs = (Map<String, Object>) event.get("unique_args");
    if (uniqueArgs == null) {
      log.warn("Webhook event missing unique_args, skipping: {}", eventType);
      return;
    }

    String tenantSchema = (String) uniqueArgs.get("tenantSchema");
    if (tenantSchema == null || tenantSchema.isBlank()) {
      log.warn("Webhook event missing tenantSchema in unique_args, skipping");
      return;
    }

    String rawMsgId = (String) event.get("sg_message_id");
    if (rawMsgId == null) {
      log.warn("Webhook event missing sg_message_id, skipping");
      return;
    }

    String providerMessageId =
        rawMsgId.contains(".filter")
            ? rawMsgId.substring(0, rawMsgId.indexOf(".filter"))
            : rawMsgId;

    String recipientEmail = (String) event.get("email");
    String reason = (String) event.get("reason");

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              deliveryLogService.updateStatus(providerMessageId, newStatus, reason);

              if (newStatus == EmailDeliveryStatus.BOUNCED
                  || newStatus == EmailDeliveryStatus.FAILED) {
                publishAuditEvent(
                    newStatus, providerMessageId, recipientEmail, reason, tenantSchema);
              }

              if (newStatus == EmailDeliveryStatus.BOUNCED) {
                notifyAdminsIfInvoiceBounce(
                    providerMessageId, recipientEmail, reason, tenantSchema);
              }
            });
  }

  private void publishAuditEvent(
      EmailDeliveryStatus status,
      String providerMessageId,
      String recipientEmail,
      String reason,
      String tenantSchema) {
    try {
      var deliveryLog = deliveryLogService.findByProviderMessageId(providerMessageId).orElse(null);
      if (deliveryLog == null) {
        log.debug(
            "No delivery log found for providerMessageId={} in schema={}, skipping audit",
            providerMessageId,
            tenantSchema);
        return;
      }

      String eventType =
          status == EmailDeliveryStatus.BOUNCED
              ? "email.delivery.bounced"
              : "email.delivery.failed";

      var record =
          AuditEventBuilder.builder()
              .eventType(eventType)
              .entityType("email_delivery_log")
              .entityId(deliveryLog.getId())
              .actorType("SYSTEM")
              .source("WEBHOOK")
              .details(
                  Map.of(
                      "recipientEmail", recipientEmail != null ? recipientEmail : "",
                      "bounceReason", reason != null ? reason : "",
                      "providerMessageId", providerMessageId))
              .build();

      auditService.log(record);
    } catch (Exception e) {
      log.error("Failed to publish audit event for {}: {}", providerMessageId, e.getMessage());
    }
  }

  private void notifyAdminsIfInvoiceBounce(
      String providerMessageId, String recipientEmail, String reason, String tenantSchema) {
    try {
      var deliveryLog = deliveryLogService.findByProviderMessageId(providerMessageId).orElse(null);
      if (deliveryLog == null || !"INVOICE".equals(deliveryLog.getReferenceType())) {
        return;
      }

      String title =
          "Invoice email bounced"
              + (reason != null ? ": " + reason : "")
              + (recipientEmail != null ? " (" + recipientEmail + ")" : "");

      notificationService.notifyAdminsAndOwners(
          "INVOICE_EMAIL_BOUNCED",
          title,
          "An invoice email could not be delivered. Please check the email address and resend.",
          "EMAIL_DELIVERY_LOG",
          deliveryLog.getId());
    } catch (Exception e) {
      log.error(
          "Failed to send admin notification for bounced invoice {}: {}",
          providerMessageId,
          e.getMessage());
    }
  }
}
