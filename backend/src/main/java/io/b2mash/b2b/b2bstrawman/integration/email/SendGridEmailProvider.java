package io.b2mash.b2b.b2bstrawman.integration.email;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Attachments;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationAdapter;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.secret.SecretStore;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * SendGrid-based email provider for orgs using BYOAK (Bring Your Own API Key). Only active when
 * {@code sendgrid-java} is on the classpath. Uses SendGrid custom args for webhook tenant
 * identification per ADR-096.
 */
@Component
@IntegrationAdapter(domain = IntegrationDomain.EMAIL, slug = "sendgrid")
@ConditionalOnClass(name = "com.sendgrid.SendGrid")
public class SendGridEmailProvider implements EmailProvider {

  private static final Logger log = LoggerFactory.getLogger(SendGridEmailProvider.class);

  static final String SECRET_KEY = "email:sendgrid:api_key";

  private final SecretStore secretStore;
  private final String senderAddress;
  private final Function<String, SendGrid> sendGridFactory;

  @Autowired
  public SendGridEmailProvider(
      SecretStore secretStore, @Value("${docteams.email.sender-address}") String senderAddress) {
    this(secretStore, senderAddress, SendGrid::new);
  }

  SendGridEmailProvider(
      SecretStore secretStore, String senderAddress, Function<String, SendGrid> sendGridFactory) {
    this.secretStore = secretStore;
    this.senderAddress = senderAddress;
    this.sendGridFactory = sendGridFactory;
  }

  @Override
  public String providerId() {
    return "sendgrid";
  }

  @Override
  public SendResult sendEmail(EmailMessage message) {
    try {
      Mail mail = buildMail(message);
      return send(mail);
    } catch (IOException e) {
      log.error("Failed to send SendGrid email to {}: {}", message.to(), e.getMessage());
      return new SendResult(false, null, e.getMessage());
    }
  }

  @Override
  public SendResult sendEmailWithAttachment(EmailMessage message, EmailAttachment attachment) {
    try {
      Mail mail = buildMail(message);
      Attachments sgAttachment = new Attachments();
      sgAttachment.setContent(Base64.getEncoder().encodeToString(attachment.content()));
      sgAttachment.setType(attachment.contentType());
      sgAttachment.setFilename(attachment.filename());
      sgAttachment.setDisposition("attachment");
      mail.addAttachments(sgAttachment);
      return send(mail);
    } catch (IOException e) {
      log.error(
          "Failed to send SendGrid email with attachment to {}: {}", message.to(), e.getMessage());
      return new SendResult(false, null, e.getMessage());
    }
  }

  @Override
  public ConnectionTestResult testConnection() {
    try {
      String apiKey = secretStore.retrieve(SECRET_KEY);
      SendGrid sg = sendGridFactory.apply(apiKey);
      Request request = new Request();
      request.setMethod(Method.GET);
      request.setEndpoint("api_keys");
      Response response = sg.api(request);
      // 200 = full access, 403 = authenticated but restricted scope — both confirm valid key
      if (response.getStatusCode() == 200 || response.getStatusCode() == 403) {
        return new ConnectionTestResult(true, "sendgrid", null);
      }
      return new ConnectionTestResult(
          false, "sendgrid", "API key validation failed: HTTP " + response.getStatusCode());
    } catch (IOException e) {
      log.error("SendGrid connection test failed: {}", e.getMessage());
      return new ConnectionTestResult(false, "sendgrid", e.getMessage());
    }
  }

  private Mail buildMail(EmailMessage message) {
    if (message.htmlBody() == null && message.plainTextBody() == null) {
      throw new IllegalArgumentException(
          "Email must have at least one of htmlBody or plainTextBody");
    }

    Email from = new Email(senderAddress);

    Personalization personalization = new Personalization();
    personalization.addTo(new Email(message.to()));

    // Copy metadata to SendGrid custom_args for webhook tenant identification (ADR-096).
    // Required keys: tenantSchema, referenceType, referenceId (from EmailMessage.withTracking).
    // Copy tracking metadata to custom_args, but exclude List-Unsubscribe MIME headers
    // which are only relevant for SMTP providers and would pollute SendGrid custom_args.
    Map<String, String> metadata = message.metadata();
    if (metadata != null) {
      metadata.forEach(
          (key, value) -> {
            if (!key.startsWith("List-Unsubscribe")) {
              personalization.addCustomArg(key, value);
            }
          });
    }

    Mail mail = new Mail();
    mail.setFrom(from);
    mail.setSubject(message.subject());
    mail.addPersonalization(personalization);

    if (message.replyTo() != null) {
      mail.setReplyTo(new Email(message.replyTo()));
    }

    if (message.plainTextBody() != null) {
      mail.addContent(new Content("text/plain", message.plainTextBody()));
    }
    if (message.htmlBody() != null) {
      mail.addContent(new Content("text/html", message.htmlBody()));
    }

    return mail;
  }

  private SendResult send(Mail mail) throws IOException {
    String apiKey = secretStore.retrieve(SECRET_KEY);
    SendGrid sg = sendGridFactory.apply(apiKey);
    Request request = new Request();
    request.setMethod(Method.POST);
    request.setEndpoint("mail/send");
    request.setBody(mail.build());

    Response response = sg.api(request);
    int status = response.getStatusCode();

    if (status >= 200 && status < 300) {
      String sgMessageId = response.getHeaders().get("X-Message-Id");
      log.debug("SendGrid email sent, sg_message_id: {}", sgMessageId);
      return new SendResult(true, sgMessageId, null);
    } else {
      String errorBody = response.getBody();
      log.error("SendGrid API returned {}: {}", status, errorBody);
      return new SendResult(false, null, "SendGrid API error " + status + ": " + errorBody);
    }
  }
}
