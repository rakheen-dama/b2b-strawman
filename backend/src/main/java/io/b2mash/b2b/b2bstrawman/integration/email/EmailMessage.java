package io.b2mash.b2b.b2bstrawman.integration.email;

import java.util.Map;
import java.util.Objects;

/**
 * Provider-agnostic email payload. Contains the recipient, subject, body (HTML and plain text), and
 * optional metadata for tracking.
 */
public record EmailMessage(
    String to,
    String subject,
    String htmlBody,
    String plainTextBody,
    String replyTo,
    Map<String, String> metadata) {

  /** Compact constructor — validates required fields. */
  public EmailMessage {
    Objects.requireNonNull(to, "to");
    Objects.requireNonNull(subject, "subject");
  }

  /**
   * Factory method that builds an EmailMessage with tracking metadata pre-populated.
   *
   * @param to recipient email address
   * @param subject email subject line
   * @param htmlBody HTML body content
   * @param plainTextBody plain text fallback body
   * @param replyTo reply-to address (may be null)
   * @param referenceType the type of entity this email relates to (e.g., "INVOICE", "NOTIFICATION")
   * @param referenceId the ID of the referenced entity
   * @param tenantSchema the tenant schema for tracking
   * @return an EmailMessage with metadata containing referenceType, referenceId, and tenantSchema
   */
  public static EmailMessage withTracking(
      String to,
      String subject,
      String htmlBody,
      String plainTextBody,
      String replyTo,
      String referenceType,
      String referenceId,
      String tenantSchema) {
    Objects.requireNonNull(referenceType, "referenceType");
    Objects.requireNonNull(referenceId, "referenceId");
    Objects.requireNonNull(tenantSchema, "tenantSchema");
    return new EmailMessage(
        to,
        subject,
        htmlBody,
        plainTextBody,
        replyTo,
        Map.of(
            "referenceType", referenceType,
            "referenceId", referenceId,
            "tenantSchema", tenantSchema));
  }
}
