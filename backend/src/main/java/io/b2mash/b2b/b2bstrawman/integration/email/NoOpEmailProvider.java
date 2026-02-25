package io.b2mash.b2b.b2bstrawman.integration.email;

import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationAdapter;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * No-op email provider used as a fallback when no SMTP configuration is present. Logs email details
 * instead of sending them.
 */
@Component
@IntegrationAdapter(domain = IntegrationDomain.EMAIL, slug = "smtp")
@ConditionalOnMissingBean(SmtpEmailProvider.class)
public class NoOpEmailProvider implements EmailProvider {

  private static final Logger log = LoggerFactory.getLogger(NoOpEmailProvider.class);

  @Override
  public String providerId() {
    return "noop";
  }

  @Override
  public SendResult sendEmail(EmailMessage message) {
    log.info("NoOp email: would send to {} with subject '{}'", message.to(), message.subject());
    return new SendResult(true, "NOOP-" + UUID.randomUUID(), null);
  }

  @Override
  public SendResult sendEmailWithAttachment(EmailMessage message, EmailAttachment attachment) {
    log.info(
        "NoOp email: would send to {} with subject '{}' and attachment '{}'",
        message.to(),
        message.subject(),
        attachment.filename());
    return new SendResult(true, "NOOP-" + UUID.randomUUID(), null);
  }

  @Override
  public ConnectionTestResult testConnection() {
    return new ConnectionTestResult(true, "noop", null);
  }
}
