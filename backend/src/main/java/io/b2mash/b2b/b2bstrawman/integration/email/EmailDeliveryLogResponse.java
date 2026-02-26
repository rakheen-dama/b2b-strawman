package io.b2mash.b2b.b2bstrawman.integration.email;

import java.time.Instant;
import java.util.UUID;

/** DTO record for the email delivery log API response. */
public record EmailDeliveryLogResponse(
    UUID id,
    String recipientEmail,
    String templateName,
    String referenceType,
    UUID referenceId,
    EmailDeliveryStatus status,
    String providerMessageId,
    String providerSlug,
    String errorMessage,
    Instant createdAt,
    Instant updatedAt) {

  public static EmailDeliveryLogResponse from(EmailDeliveryLog log) {
    return new EmailDeliveryLogResponse(
        log.getId(),
        log.getRecipientEmail(),
        log.getTemplateName(),
        log.getReferenceType(),
        log.getReferenceId(),
        log.getStatus(),
        log.getProviderMessageId(),
        log.getProviderSlug(),
        log.getErrorMessage(),
        log.getCreatedAt(),
        log.getUpdatedAt());
  }
}
