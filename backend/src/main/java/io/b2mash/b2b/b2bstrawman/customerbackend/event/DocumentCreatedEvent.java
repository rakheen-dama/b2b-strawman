package io.b2mash.b2b.b2bstrawman.customerbackend.event;

import java.util.UUID;

/** Published when a document upload is confirmed. */
public final class DocumentCreatedEvent extends PortalDomainEvent {

  private final UUID documentId;
  private final UUID projectId;
  private final UUID customerId;
  private final String fileName;
  private final String scope;
  private final String visibility;
  private final String s3Key;
  private final Long size;
  private final String contentType;

  public DocumentCreatedEvent(
      UUID documentId,
      UUID projectId,
      UUID customerId,
      String fileName,
      String scope,
      String visibility,
      String s3Key,
      Long size,
      String contentType,
      String orgId,
      String tenantId) {
    super(orgId, tenantId);
    this.documentId = documentId;
    this.projectId = projectId;
    this.customerId = customerId;
    this.fileName = fileName;
    this.scope = scope;
    this.visibility = visibility;
    this.s3Key = s3Key;
    this.size = size;
    this.contentType = contentType;
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getFileName() {
    return fileName;
  }

  public String getScope() {
    return scope;
  }

  public String getVisibility() {
    return visibility;
  }

  public String getS3Key() {
    return s3Key;
  }

  public Long getSize() {
    return size;
  }

  public String getContentType() {
    return contentType;
  }
}
