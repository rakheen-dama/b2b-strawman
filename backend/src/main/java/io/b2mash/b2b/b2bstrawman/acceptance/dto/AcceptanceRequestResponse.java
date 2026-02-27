package io.b2mash.b2b.b2bstrawman.acceptance.dto;

import io.b2mash.b2b.b2bstrawman.acceptance.AcceptanceRequest;
import io.b2mash.b2b.b2bstrawman.acceptance.AcceptanceStatus;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocument;
import java.time.Instant;
import java.util.UUID;

public record AcceptanceRequestResponse(
    UUID id,
    UUID generatedDocumentId,
    UUID portalContactId,
    UUID customerId,
    AcceptanceStatus status,
    Instant sentAt,
    Instant viewedAt,
    Instant acceptedAt,
    Instant expiresAt,
    Instant revokedAt,
    String acceptorName,
    boolean hasCertificate,
    String certificateFileName,
    UUID sentByMemberId,
    UUID revokedByMemberId,
    int reminderCount,
    Instant lastRemindedAt,
    Instant createdAt,
    Instant updatedAt,
    PortalContactSummary contact,
    GeneratedDocumentSummary document) {

  public record PortalContactSummary(UUID id, String displayName, String email) {}

  public record GeneratedDocumentSummary(UUID id, String fileName) {}

  public static AcceptanceRequestResponse from(
      AcceptanceRequest request, PortalContact contact, GeneratedDocument doc) {
    PortalContactSummary contactSummary =
        contact != null
            ? new PortalContactSummary(
                contact.getId(), contact.getDisplayName(), contact.getEmail())
            : null;
    GeneratedDocumentSummary docSummary =
        doc != null ? new GeneratedDocumentSummary(doc.getId(), doc.getFileName()) : null;
    return new AcceptanceRequestResponse(
        request.getId(),
        request.getGeneratedDocumentId(),
        request.getPortalContactId(),
        request.getCustomerId(),
        request.getStatus(),
        request.getSentAt(),
        request.getViewedAt(),
        request.getAcceptedAt(),
        request.getExpiresAt(),
        request.getRevokedAt(),
        request.getAcceptorName(),
        request.getCertificateS3Key() != null,
        request.getCertificateFileName(),
        request.getSentByMemberId(),
        request.getRevokedByMemberId(),
        request.getReminderCount(),
        request.getLastRemindedAt(),
        request.getCreatedAt(),
        request.getUpdatedAt(),
        contactSummary,
        docSummary);
  }
}
