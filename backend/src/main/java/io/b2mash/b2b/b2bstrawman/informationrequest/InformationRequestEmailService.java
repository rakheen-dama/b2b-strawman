package io.b2mash.b2b.b2bstrawman.informationrequest;

import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailDeliveryLogService;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailMessage;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailProvider;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailRateLimiter;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailContextBuilder;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailTemplateRenderer;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class InformationRequestEmailService {

  private static final Logger log = LoggerFactory.getLogger(InformationRequestEmailService.class);
  private static final String REFERENCE_TYPE = "INFORMATION_REQUEST";

  private final IntegrationRegistry integrationRegistry;
  private final EmailTemplateRenderer emailTemplateRenderer;
  private final EmailContextBuilder emailContextBuilder;
  private final EmailDeliveryLogService deliveryLogService;
  private final EmailRateLimiter emailRateLimiter;
  private final String appBaseUrl;

  public InformationRequestEmailService(
      IntegrationRegistry integrationRegistry,
      EmailTemplateRenderer emailTemplateRenderer,
      EmailContextBuilder emailContextBuilder,
      EmailDeliveryLogService deliveryLogService,
      EmailRateLimiter emailRateLimiter,
      @Value("${docteams.app.base-url:http://localhost:3000}") String appBaseUrl) {
    this.integrationRegistry = integrationRegistry;
    this.emailTemplateRenderer = emailTemplateRenderer;
    this.emailContextBuilder = emailContextBuilder;
    this.deliveryLogService = deliveryLogService;
    this.emailRateLimiter = emailRateLimiter;
    this.appBaseUrl = appBaseUrl;
  }

  public void sendRequestSentEmail(
      String recipientEmail,
      String contactName,
      String requestNumber,
      int itemCount,
      UUID requestId) {
    var context = emailContextBuilder.buildBaseContext(contactName, null);
    context.put("contactName", contactName);
    context.put("requestNumber", requestNumber);
    context.put("itemCount", String.valueOf(itemCount));
    context.put("portalUrl", appBaseUrl + "/portal");
    String orgName = (String) context.get("orgName");
    context.put("subject", "Information request %s from %s".formatted(requestNumber, orgName));
    sendEmail("request-sent", recipientEmail, context, requestId);
  }

  public void sendItemAcceptedEmail(
      String recipientEmail,
      String contactName,
      String itemName,
      String requestNumber,
      UUID requestId) {
    var context = emailContextBuilder.buildBaseContext(contactName, null);
    context.put("contactName", contactName);
    context.put("itemName", itemName);
    context.put("requestNumber", requestNumber);
    context.put("portalUrl", appBaseUrl + "/portal");
    String orgName = (String) context.get("orgName");
    context.put("subject", "Item accepted — %s (%s)".formatted(itemName, orgName));
    sendEmail("request-item-accepted", recipientEmail, context, requestId);
  }

  public void sendItemRejectedEmail(
      String recipientEmail,
      String contactName,
      String itemName,
      String requestNumber,
      String rejectionReason,
      UUID requestId) {
    var context = emailContextBuilder.buildBaseContext(contactName, null);
    context.put("contactName", contactName);
    context.put("itemName", itemName);
    context.put("requestNumber", requestNumber);
    context.put("rejectionReason", rejectionReason);
    context.put("portalUrl", appBaseUrl + "/portal");
    String orgName = (String) context.get("orgName");
    context.put("subject", "Item requires re-submission — %s (%s)".formatted(itemName, orgName));
    sendEmail("request-item-rejected", recipientEmail, context, requestId);
  }

  public void sendRequestCompletedEmail(
      String recipientEmail,
      String contactName,
      String requestNumber,
      int totalItems,
      UUID requestId) {
    var context = emailContextBuilder.buildBaseContext(contactName, null);
    context.put("contactName", contactName);
    context.put("requestNumber", requestNumber);
    context.put("totalItems", String.valueOf(totalItems));
    context.put("portalUrl", appBaseUrl + "/portal");
    String orgName = (String) context.get("orgName");
    context.put("subject", "Request %s completed (%s)".formatted(requestNumber, orgName));
    sendEmail("request-completed", recipientEmail, context, requestId);
  }

  private void sendEmail(
      String templateName, String recipientEmail, Map<String, Object> context, UUID referenceId) {
    if (recipientEmail == null || recipientEmail.isBlank()) {
      log.warn("Skipping information request email — no recipient email");
      return;
    }
    if (!RequestScopes.TENANT_ID.isBound()) {
      log.warn("Skipping information request email — no tenant context");
      return;
    }
    try {
      EmailProvider provider =
          integrationRegistry.resolve(IntegrationDomain.EMAIL, EmailProvider.class);
      var rendered = emailTemplateRenderer.render(templateName, context);
      String tenantSchema = RequestScopes.TENANT_ID.get();

      if (!emailRateLimiter.tryAcquire(tenantSchema, provider.providerId())) {
        log.warn("Rate limit exceeded for information request email, template={}", templateName);
        deliveryLogService.recordRateLimited(
            REFERENCE_TYPE, referenceId, templateName, recipientEmail, provider.providerId());
        return;
      }

      var message =
          EmailMessage.withTracking(
              recipientEmail,
              rendered.subject(),
              rendered.htmlBody(),
              rendered.plainTextBody(),
              null,
              REFERENCE_TYPE,
              referenceId.toString(),
              tenantSchema);
      var result = provider.sendEmail(message);
      deliveryLogService.record(
          REFERENCE_TYPE, referenceId, templateName, recipientEmail, provider.providerId(), result);

      if (result.success()) {
        log.info(
            "Information request email sent: template={}, to={}", templateName, recipientEmail);
      } else {
        log.warn(
            "Information request email failed: template={}, to={}, error={}",
            templateName,
            recipientEmail,
            result.errorMessage());
      }
    } catch (Exception e) {
      log.error("Unexpected error sending information request email: template={}", templateName, e);
    }
  }
}
