package io.b2mash.b2b.b2bstrawman.integration.email;

import com.sendgrid.helpers.eventwebhook.EventWebhook;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import java.security.interfaces.ECPublicKey;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class EmailWebhookService {

  private static final Logger log = LoggerFactory.getLogger(EmailWebhookService.class);

  /** Validates tenant schema format: tenant_ followed by exactly 12 hex characters. */
  private static final Pattern TENANT_SCHEMA_PATTERN = Pattern.compile("^tenant_[0-9a-f]{12}$");

  /** Maximum length for the provider path variable to prevent log injection. */
  private static final int MAX_PROVIDER_LENGTH = 32;

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
    String sanitizedProvider = sanitizeProvider(provider);
    if (!"sendgrid".equals(sanitizedProvider)) {
      log.warn("Unsupported webhook provider: {}", sanitizedProvider);
      throw new WebhookPayloadException("Unsupported provider");
    }

    verifySignature(payload, signature, timestamp);

    List<Map<String, Object>> events = parseEvents(payload);
    for (Map<String, Object> event : events) {
      processEvent(event);
    }
  }

  private void verifySignature(String payload, String signature, String timestamp) {
    // Fail-closed: reject all requests when verification key is not configured
    if (webhookVerificationKey == null || webhookVerificationKey.isBlank()) {
      log.warn(
          "Webhook verification key not configured — rejecting request. "
              + "Set docteams.email.sendgrid.webhook-verification-key to enable webhook processing.");
      throw new WebhookAuthenticationException("Webhook verification key not configured");
    }

    if (signature == null || timestamp == null) {
      log.warn("Missing webhook signature or timestamp headers");
      throw new WebhookAuthenticationException("Invalid webhook signature");
    }

    try {
      EventWebhook ew = new EventWebhook();
      ECPublicKey ecPublicKey = ew.ConvertPublicKeyToECDSA(webhookVerificationKey);
      boolean isValid = ew.VerifySignature(ecPublicKey, payload, signature, timestamp);
      if (!isValid) {
        log.warn("Invalid webhook signature");
        throw new WebhookAuthenticationException("Invalid webhook signature");
      }
    } catch (WebhookAuthenticationException e) {
      throw e;
    } catch (Exception e) {
      log.error("Webhook signature verification failed: {}", e.getMessage());
      throw new WebhookAuthenticationException("Invalid webhook signature", e);
    }
  }

  private List<Map<String, Object>> parseEvents(String payload) {
    try {
      return objectMapper.readValue(payload, new TypeReference<>() {});
    } catch (JacksonException e) {
      log.error("Failed to parse webhook payload: {}", e.getMessage());
      throw new WebhookPayloadException("Invalid payload");
    }
  }

  /**
   * Processes a single webhook event. Each operation (status update, audit, notification) is
   * intentionally NOT wrapped in a single transaction — webhook events are idempotent, and partial
   * success is preferable to full rollback (e.g., status update succeeds but audit fails).
   */
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

    // Validate tenant schema format to prevent cross-tenant manipulation
    if (!TENANT_SCHEMA_PATTERN.matcher(tenantSchema).matches()) {
      log.warn("Webhook event has invalid tenantSchema format, skipping: {}", tenantSchema);
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

              // Fetch delivery log once for audit + notification (avoids redundant DB query)
              EmailDeliveryLog deliveryLog = null;
              if (newStatus == EmailDeliveryStatus.BOUNCED
                  || newStatus == EmailDeliveryStatus.FAILED) {
                deliveryLog =
                    deliveryLogService.findByProviderMessageId(providerMessageId).orElse(null);
                publishAuditEvent(newStatus, deliveryLog, recipientEmail, reason, tenantSchema);
              }

              if (newStatus == EmailDeliveryStatus.BOUNCED) {
                notifyAdminsIfInvoiceBounce(deliveryLog, recipientEmail, reason);
              }
            });
  }

  private void publishAuditEvent(
      EmailDeliveryStatus status,
      EmailDeliveryLog deliveryLog,
      String recipientEmail,
      String reason,
      String tenantSchema) {
    try {
      if (deliveryLog == null) {
        log.debug("No delivery log found in schema={}, skipping audit", tenantSchema);
        return;
      }

      String auditEventType =
          status == EmailDeliveryStatus.BOUNCED
              ? "email.delivery.bounced"
              : "email.delivery.failed";

      var record =
          AuditEventBuilder.builder()
              .eventType(auditEventType)
              .entityType("email_delivery_log")
              .entityId(deliveryLog.getId())
              .actorType("SYSTEM")
              .source("WEBHOOK")
              .details(
                  Map.of(
                      "recipientEmail", recipientEmail != null ? recipientEmail : "",
                      "bounceReason", reason != null ? reason : "",
                      "providerMessageId", deliveryLog.getProviderMessageId()))
              .build();

      auditService.log(record);
    } catch (Exception e) {
      log.error(
          "Failed to publish audit event for {}: {}",
          deliveryLog != null ? deliveryLog.getProviderMessageId() : "unknown",
          e.getMessage());
    }
  }

  private void notifyAdminsIfInvoiceBounce(
      EmailDeliveryLog deliveryLog, String recipientEmail, String reason) {
    try {
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
          deliveryLog != null ? deliveryLog.getProviderMessageId() : "unknown",
          e.getMessage());
    }
  }

  /** Truncates and sanitizes the provider value for safe logging. */
  private static String sanitizeProvider(String provider) {
    if (provider == null) {
      return "null";
    }
    String sanitized = provider.replaceAll("[^a-zA-Z0-9_-]", "");
    return sanitized.length() > MAX_PROVIDER_LENGTH
        ? sanitized.substring(0, MAX_PROVIDER_LENGTH)
        : sanitized;
  }
}
