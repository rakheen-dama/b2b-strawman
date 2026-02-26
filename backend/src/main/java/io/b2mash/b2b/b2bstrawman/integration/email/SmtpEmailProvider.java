package io.b2mash.b2b.b2bstrawman.integration.email;

import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationAdapter;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * SMTP-based email provider that sends emails via {@link JavaMailSender}. Only active when {@code
 * spring.mail.host} is configured.
 */
@Component
@IntegrationAdapter(domain = IntegrationDomain.EMAIL, slug = "smtp")
@ConditionalOnProperty(name = "spring.mail.host")
public class SmtpEmailProvider implements EmailProvider {

  private static final Logger log = LoggerFactory.getLogger(SmtpEmailProvider.class);

  private final JavaMailSender mailSender;
  private final String senderAddress;

  public SmtpEmailProvider(
      JavaMailSender mailSender,
      @org.springframework.beans.factory.annotation.Value("${docteams.email.sender-address}")
          String senderAddress) {
    this.mailSender = mailSender;
    this.senderAddress = senderAddress;
  }

  @Override
  public String providerId() {
    return "smtp";
  }

  @Override
  public SendResult sendEmail(EmailMessage message) {
    try {
      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
      populateMessage(helper, message);
      mailSender.send(mimeMessage);
      String messageId = mimeMessage.getMessageID();
      log.debug("SMTP email sent to {} with Message-ID: {}", message.to(), messageId);
      return new SendResult(true, messageId, null);
    } catch (MailException | MessagingException e) {
      log.error("Failed to send SMTP email to {}: {}", message.to(), e.getMessage());
      return new SendResult(false, null, e.getMessage());
    }
  }

  @Override
  public SendResult sendEmailWithAttachment(EmailMessage message, EmailAttachment attachment) {
    try {
      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
      populateMessage(helper, message);
      helper.addAttachment(
          attachment.filename(),
          new ByteArrayResource(attachment.content()),
          attachment.contentType());
      mailSender.send(mimeMessage);
      String messageId = mimeMessage.getMessageID();
      log.debug(
          "SMTP email with attachment '{}' sent to {} with Message-ID: {}",
          attachment.filename(),
          message.to(),
          messageId);
      return new SendResult(true, messageId, null);
    } catch (MailException | MessagingException e) {
      log.error(
          "Failed to send SMTP email with attachment to {}: {}", message.to(), e.getMessage());
      return new SendResult(false, null, e.getMessage());
    }
  }

  @Override
  public ConnectionTestResult testConnection() {
    try {
      if (mailSender instanceof JavaMailSenderImpl impl) {
        impl.testConnection();
        return new ConnectionTestResult(true, "smtp", null);
      }
      return new ConnectionTestResult(
          false, "smtp", "Cannot test connection: unsupported JavaMailSender implementation");
    } catch (MessagingException e) {
      log.error("SMTP connection test failed: {}", e.getMessage());
      return new ConnectionTestResult(false, "smtp", e.getMessage());
    }
  }

  private void populateMessage(MimeMessageHelper helper, EmailMessage message)
      throws MessagingException {
    if (message.htmlBody() == null && message.plainTextBody() == null) {
      throw new IllegalArgumentException(
          "Email must have at least one of htmlBody or plainTextBody");
    }
    helper.setFrom(senderAddress);
    helper.setTo(message.to());
    helper.setSubject(message.subject());
    if (message.htmlBody() != null && message.plainTextBody() != null) {
      helper.setText(message.plainTextBody(), message.htmlBody());
    } else if (message.htmlBody() != null) {
      helper.setText(message.htmlBody(), true);
    } else if (message.plainTextBody() != null) {
      helper.setText(message.plainTextBody(), false);
    }
    if (message.replyTo() != null) {
      helper.setReplyTo(message.replyTo());
    }

    // Set List-Unsubscribe headers from message metadata (RFC 8058)
    if (message.metadata() != null) {
      MimeMessage mimeMessage = helper.getMimeMessage();
      String listUnsubscribe = message.metadata().get("List-Unsubscribe");
      if (listUnsubscribe != null) {
        mimeMessage.setHeader("List-Unsubscribe", listUnsubscribe);
      }
      String listUnsubscribePost = message.metadata().get("List-Unsubscribe-Post");
      if (listUnsubscribePost != null) {
        mimeMessage.setHeader("List-Unsubscribe-Post", listUnsubscribePost);
      }
    }
  }
}
