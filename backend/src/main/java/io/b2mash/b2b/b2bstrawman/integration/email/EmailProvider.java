package io.b2mash.b2b.b2bstrawman.integration.email;

import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;

/**
 * Port for sending emails via an external provider. Tenant-scoped: each org can configure their own
 * provider (SMTP, SendGrid, etc.).
 */
public interface EmailProvider {

  /** Provider identifier (e.g., "smtp", "sendgrid", "noop"). */
  String providerId();

  /** Send an email message. */
  SendResult sendEmail(EmailMessage message);

  /** Send an email message with a file attachment. */
  SendResult sendEmailWithAttachment(EmailMessage message, EmailAttachment attachment);

  /** Test connectivity with the configured credentials. */
  ConnectionTestResult testConnection();
}
