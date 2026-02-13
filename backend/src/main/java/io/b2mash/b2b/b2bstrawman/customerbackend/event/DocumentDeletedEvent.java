package io.b2mash.b2b.b2bstrawman.customerbackend.event;

import java.util.UUID;

/** Published when a document upload is cancelled (deleted). */
public final class DocumentDeletedEvent extends PortalDomainEvent {

  private final UUID documentId;

  public DocumentDeletedEvent(UUID documentId, String orgId, String tenantId) {
    super(orgId, tenantId);
    this.documentId = documentId;
  }

  public UUID getDocumentId() {
    return documentId;
  }
}
