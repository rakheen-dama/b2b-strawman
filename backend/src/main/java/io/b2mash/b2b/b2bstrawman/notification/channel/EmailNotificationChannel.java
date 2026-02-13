package io.b2mash.b2b.b2bstrawman.notification.channel;

import io.b2mash.b2b.b2bstrawman.notification.Notification;
import io.b2mash.b2b.b2bstrawman.notification.template.EmailTemplate;
import io.b2mash.b2b.b2bstrawman.notification.template.TemplateRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Email notification channel -- stub implementation for local/dev profiles. Logs the rendered email
 * content instead of sending it.
 *
 * <p>In production, this bean is NOT registered (no prod profile). When SES integration is added
 * (future phase), a production EmailNotificationChannel will inject SesClient and actually send
 * emails.
 */
@Component
@Profile({"local", "dev"})
public class EmailNotificationChannel implements NotificationChannel {

  private static final Logger log = LoggerFactory.getLogger(EmailNotificationChannel.class);

  private final TemplateRenderer templateRenderer;

  public EmailNotificationChannel(TemplateRenderer templateRenderer) {
    this.templateRenderer = templateRenderer;
  }

  @Override
  public String channelId() {
    return "email";
  }

  @Override
  public void deliver(Notification notification, String recipientEmail) {
    EmailTemplate template = EmailTemplate.fromNotificationType(notification.getType());
    String subject = template.renderSubject(notification);
    String body = template.renderBody(notification);

    log.info("[EMAIL STUB] To: {}, Subject: {}, Body: {}", recipientEmail, subject, body);
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
