package io.b2mash.b2b.b2bstrawman.notification.channel;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailDeliveryLogService;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailMessage;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailProvider;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailRateLimiter;
import io.b2mash.b2b.b2bstrawman.integration.email.UnsubscribeService;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.Notification;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailContextBuilder;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailTemplateRenderer;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Email notification channel -- production implementation. Sends branded HTML emails via the
 * tenant's configured email provider (platform SMTP or BYOAK SendGrid). Tracks delivery in
 * EmailDeliveryLog and enforces per-tenant rate limits.
 */
@Component
public class EmailNotificationChannel implements NotificationChannel {

  private static final Logger log = LoggerFactory.getLogger(EmailNotificationChannel.class);

  private final IntegrationRegistry integrationRegistry;
  private final EmailTemplateRenderer emailTemplateRenderer;
  private final EmailContextBuilder emailContextBuilder;
  private final EmailDeliveryLogService deliveryLogService;
  private final EmailRateLimiter emailRateLimiter;
  private final MemberRepository memberRepository;
  private final UnsubscribeService unsubscribeService;
  private final String appBaseUrl;

  public EmailNotificationChannel(
      IntegrationRegistry integrationRegistry,
      EmailTemplateRenderer emailTemplateRenderer,
      EmailContextBuilder emailContextBuilder,
      EmailDeliveryLogService deliveryLogService,
      EmailRateLimiter emailRateLimiter,
      MemberRepository memberRepository,
      UnsubscribeService unsubscribeService,
      @Value("${docteams.app.base-url:http://localhost:3000}") String appBaseUrl) {
    this.integrationRegistry = integrationRegistry;
    this.emailTemplateRenderer = emailTemplateRenderer;
    this.emailContextBuilder = emailContextBuilder;
    this.deliveryLogService = deliveryLogService;
    this.emailRateLimiter = emailRateLimiter;
    this.memberRepository = memberRepository;
    this.unsubscribeService = unsubscribeService;
    this.appBaseUrl = appBaseUrl;
  }

  @Override
  public String channelId() {
    return "email";
  }

  @Override
  public void deliver(Notification notification, String recipientEmail) {
    if (recipientEmail == null || recipientEmail.isBlank()) {
      log.debug("Skipping email for notification {} -- no recipient email", notification.getId());
      return;
    }

    if (!RequestScopes.TENANT_ID.isBound()) {
      log.warn(
          "Skipping email for notification {} -- no tenant context bound", notification.getId());
      return;
    }

    try {
      // 1. Resolve provider
      EmailProvider provider =
          integrationRegistry.resolve(IntegrationDomain.EMAIL, EmailProvider.class);

      // 2. Map type to template
      String templateName = resolveTemplateName(notification.getType());
      if (templateName == null) {
        log.warn(
            "Skipping email for notification {} -- unmapped type '{}'",
            notification.getId(),
            notification.getType());
        return;
      }

      // 3. Generate unsubscribe URL
      String tenantSchema = RequestScopes.requireTenantId();
      String unsubscribeUrl = generateUnsubscribeUrl(notification, tenantSchema);

      // 4. Build context
      String recipientName =
          memberRepository
              .findById(notification.getRecipientMemberId())
              .map(Member::getName)
              .orElse(null);
      Map<String, Object> context =
          buildNotificationContext(notification, recipientName, unsubscribeUrl);

      // 5. Render
      var rendered = emailTemplateRenderer.render(templateName, context);

      // 6. Check rate limit
      if (!emailRateLimiter.tryAcquire(tenantSchema, provider.providerId())) {
        log.warn(
            "Rate limit exceeded for tenant={}, provider={}, skipping email to={}",
            tenantSchema,
            provider.providerId(),
            recipientEmail);
        deliveryLogService.recordRateLimited(
            "NOTIFICATION",
            notification.getId(),
            templateName,
            recipientEmail,
            provider.providerId());
        return;
      }

      // 7. Construct message with tracking metadata and List-Unsubscribe headers
      var metadata =
          new HashMap<>(
              Map.of(
                  "referenceType",
                  "NOTIFICATION",
                  "referenceId",
                  notification.getId().toString(),
                  "tenantSchema",
                  tenantSchema));
      if (unsubscribeUrl != null) {
        metadata.put("List-Unsubscribe", "<" + unsubscribeUrl + ">");
        metadata.put("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
      }
      var message =
          new EmailMessage(
              recipientEmail,
              rendered.subject(),
              rendered.htmlBody(),
              rendered.plainTextBody(),
              null,
              metadata);

      // 8. Send
      var result = provider.sendEmail(message);

      // 9. Record delivery
      deliveryLogService.record(
          "NOTIFICATION",
          notification.getId(),
          templateName,
          recipientEmail,
          provider.providerId(),
          result);

      if (result.success()) {
        log.debug(
            "Email sent for notification={} to={} via provider={}",
            notification.getId(),
            recipientEmail,
            provider.providerId());
      } else {
        log.warn(
            "Email send failed for notification={} to={}: {}",
            notification.getId(),
            recipientEmail,
            result.errorMessage());
      }

    } catch (Exception e) {
      log.error(
          "Unexpected error delivering email for notification={} to={}",
          notification.getId(),
          recipientEmail,
          e);
    }
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  private String generateUnsubscribeUrl(Notification notification, String tenantSchema) {
    try {
      String token =
          unsubscribeService.generateToken(
              notification.getRecipientMemberId(), notification.getType(), tenantSchema);
      return appBaseUrl + "/api/email/unsubscribe?token=" + token;
    } catch (InvalidStateException e) {
      log.warn("Failed to generate unsubscribe URL for notification {}", notification.getId(), e);
      return null;
    }
  }

  private String resolveTemplateName(String notificationType) {
    return switch (notificationType) {
      case "TASK_ASSIGNED", "TASK_CLAIMED", "TASK_UPDATED" -> "notification-task";
      case "COMMENT_ADDED" -> "notification-comment";
      case "DOCUMENT_SHARED", "DOCUMENT_GENERATED" -> "notification-document";
      case "MEMBER_INVITED" -> "notification-member";
      case "BUDGET_ALERT" -> "notification-budget";
      case "INVOICE_APPROVED", "INVOICE_SENT", "INVOICE_PAID", "INVOICE_VOIDED" ->
          "notification-invoice";
      case "RECURRING_PROJECT_CREATED", "SCHEDULE_SKIPPED", "SCHEDULE_COMPLETED" ->
          "notification-schedule";
      case "RETAINER_PERIOD_READY_TO_CLOSE",
          "RETAINER_PERIOD_CLOSED",
          "RETAINER_APPROACHING_CAPACITY",
          "RETAINER_FULLY_CONSUMED",
          "RETAINER_TERMINATED" ->
          "notification-retainer";
      default -> {
        log.warn("No email template mapping for notification type '{}'", notificationType);
        yield null;
      }
    };
  }

  private Map<String, Object> buildNotificationContext(
      Notification notification, String recipientName, String unsubscribeUrl) {
    Map<String, Object> context =
        emailContextBuilder.buildBaseContext(recipientName, unsubscribeUrl);

    context.put("subject", notification.getTitle());
    context.put("notificationTitle", notification.getTitle());
    context.put("notificationBody", notification.getBody());

    String type = notification.getType();
    String appUrl = (String) context.get("appUrl");

    switch (type) {
      case "TASK_ASSIGNED", "TASK_CLAIMED", "TASK_UPDATED" -> {
        context.put(
            "action",
            switch (type) {
              case "TASK_ASSIGNED" -> "assigned";
              case "TASK_CLAIMED" -> "claimed";
              default -> "updated";
            });
        context.put(
            "taskUrl",
            notification.getReferenceEntityId() != null
                    && notification.getReferenceProjectId() != null
                ? appUrl
                    + "/projects/"
                    + notification.getReferenceProjectId()
                    + "/tasks/"
                    + notification.getReferenceEntityId()
                : null);
      }
      case "COMMENT_ADDED" -> {
        String preview = notification.getBody();
        context.put(
            "commentPreview",
            preview != null && preview.length() > 200 ? preview.substring(0, 200) : preview);
        context.put("entityUrl", null);
      }
      case "DOCUMENT_SHARED", "DOCUMENT_GENERATED" -> {
        context.put("documentUrl", null);
      }
      case "MEMBER_INVITED" -> {
        context.put("joinUrl", appUrl);
      }
      case "BUDGET_ALERT" -> {
        context.put(
            "projectUrl",
            notification.getReferenceEntityId() != null
                ? appUrl + "/projects/" + notification.getReferenceEntityId()
                : null);
      }
      case "INVOICE_APPROVED", "INVOICE_SENT", "INVOICE_PAID", "INVOICE_VOIDED" -> {
        String statusSuffix = type.substring("INVOICE_".length()).toLowerCase();
        context.put("status", statusSuffix);
        context.put(
            "invoiceUrl",
            notification.getReferenceEntityId() != null
                ? appUrl + "/invoices/" + notification.getReferenceEntityId()
                : null);
      }
      case "RECURRING_PROJECT_CREATED", "SCHEDULE_SKIPPED", "SCHEDULE_COMPLETED" -> {
        context.put(
            "action",
            switch (type) {
              case "RECURRING_PROJECT_CREATED" -> "created";
              case "SCHEDULE_SKIPPED" -> "skipped";
              default -> "completed";
            });
        context.put("scheduleUrl", appUrl);
      }
      case "RETAINER_PERIOD_READY_TO_CLOSE",
          "RETAINER_PERIOD_CLOSED",
          "RETAINER_APPROACHING_CAPACITY",
          "RETAINER_FULLY_CONSUMED",
          "RETAINER_TERMINATED" -> {
        context.put("retainerUrl", appUrl);
      }
      default -> {
        // No additional context for unmapped types
      }
    }

    return context;
  }
}
