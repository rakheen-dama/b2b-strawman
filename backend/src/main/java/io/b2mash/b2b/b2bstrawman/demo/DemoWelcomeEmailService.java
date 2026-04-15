package io.b2mash.b2b.b2bstrawman.demo;

import io.b2mash.b2b.b2bstrawman.notification.template.EmailTemplateRenderer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends a welcome email to newly provisioned demo org admins. Uses platform-level SMTP (not
 * tenant-scoped email provider) since the email is sent before the tenant has any integrations
 * configured. Fire-and-forget: exceptions are caught and logged, never propagated.
 */
@Service
public class DemoWelcomeEmailService {

  private static final Logger log = LoggerFactory.getLogger(DemoWelcomeEmailService.class);
  private static final String TEMPLATE_NAME = "demo-welcome";

  private final Optional<JavaMailSender> mailSender;
  private final EmailTemplateRenderer emailTemplateRenderer;
  private final String senderAddress;
  private final String productName;

  public DemoWelcomeEmailService(
      Optional<JavaMailSender> mailSender,
      EmailTemplateRenderer emailTemplateRenderer,
      @Value("${docteams.email.sender-address:noreply@kazi.app}") String senderAddress,
      @Value("${docteams.app.product-name:Kazi}") String productName) {
    this.mailSender = mailSender;
    this.emailTemplateRenderer = emailTemplateRenderer;
    this.senderAddress = senderAddress;
    this.productName = productName;
  }

  /**
   * Sends a welcome email with login credentials. Non-fatal: catches and logs all exceptions.
   *
   * @param adminEmail recipient email address
   * @param orgName display name of the provisioned organization
   * @param orgSlug organization slug (used in login URL)
   * @param verticalProfile the vertical profile (e.g., "legal-za")
   * @param loginUrl full login URL for the demo org
   * @param tempPassword temporary password for first login
   */
  public void sendWelcomeEmail(
      String adminEmail,
      String orgName,
      String orgSlug,
      String verticalProfile,
      String loginUrl,
      String tempPassword) {
    if (mailSender.isEmpty()) {
      log.info(
          "Skipping demo welcome email to {} — no mail sender configured (test/dev environment)",
          adminEmail);
      return;
    }

    try {
      Map<String, Object> context = new HashMap<>();
      context.put("subject", "Welcome to " + productName + " — Your demo is ready");
      context.put("orgName", orgName);
      context.put("orgSlug", orgSlug);
      context.put("verticalProfile", verticalProfile);
      context.put("loginUrl", loginUrl);
      context.put("adminEmail", adminEmail);
      context.put("tempPassword", tempPassword);
      context.put("productName", productName);
      context.put("recipientName", null);
      context.put("brandColor", "#0D9488");
      context.put("orgLogoUrl", null);
      context.put("footerText", null);
      context.put("unsubscribeUrl", null);
      context.put("appUrl", loginUrl);

      var rendered = emailTemplateRenderer.render(TEMPLATE_NAME, context);

      var mimeMessage = mailSender.get().createMimeMessage();
      var helper = new MimeMessageHelper(mimeMessage, true);
      helper.setFrom(senderAddress);
      helper.setTo(adminEmail);
      helper.setSubject(rendered.subject());
      if (rendered.htmlBody() != null && rendered.plainTextBody() != null) {
        helper.setText(rendered.plainTextBody(), rendered.htmlBody());
      } else if (rendered.htmlBody() != null) {
        helper.setText(rendered.htmlBody(), true);
      }
      mailSender.get().send(mimeMessage);

      log.info("Demo welcome email sent to {} for org '{}'", adminEmail, orgName);
    } catch (Exception e) {
      log.error(
          "Failed to send demo welcome email to {} for org '{}': {}",
          adminEmail,
          orgName,
          e.getMessage());
    }
  }
}
