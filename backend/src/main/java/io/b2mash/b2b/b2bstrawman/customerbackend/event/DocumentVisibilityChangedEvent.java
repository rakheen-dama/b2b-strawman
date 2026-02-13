package io.b2mash.b2b.b2bstrawman.customerbackend.event;

import java.util.UUID;

/** Published when a document's visibility is toggled between INTERNAL and SHARED. */
public final class DocumentVisibilityChangedEvent extends PortalDomainEvent {

  private final UUID documentId;
  private final String visibility;
  private final String previousVisibility;

  public DocumentVisibilityChangedEvent(
      UUID documentId,
      String visibility,
      String previousVisibility,
      String orgId,
      String tenantId) {
    super(orgId, tenantId);
    this.documentId = documentId;
    this.visibility = visibility;
    this.previousVisibility = previousVisibility;
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public String getVisibility() {
    return visibility;
  }

  public String getPreviousVisibility() {
    return previousVisibility;
  }
}
