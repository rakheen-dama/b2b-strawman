package io.b2mash.b2b.b2bstrawman.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class Document {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "file_name", nullable = false, length = 500)
  private String fileName;

  @Column(name = "content_type", length = 100)
  private String contentType;

  @Column(name = "size", nullable = false)
  private long size;

  @Column(name = "s3_key", nullable = false, length = 1000)
  private String s3Key;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 50)
  private Status status;

  @Column(name = "uploaded_by", nullable = false)
  private UUID uploadedBy;

  @Column(name = "uploaded_at")
  private Instant uploadedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Document() {}

  public Document(UUID projectId, String fileName, String contentType, long size, UUID uploadedBy) {
    this.projectId = projectId;
    this.fileName = fileName;
    this.contentType = contentType;
    this.size = size;
    this.s3Key = "pending";
    this.status = Status.PENDING;
    this.uploadedBy = uploadedBy;
    this.createdAt = Instant.now();
  }

  public void assignS3Key(String s3Key) {
    this.s3Key = s3Key;
  }

  public void confirmUpload() {
    this.status = Status.UPLOADED;
    this.uploadedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public String getFileName() {
    return fileName;
  }

  public String getContentType() {
    return contentType;
  }

  public long getSize() {
    return size;
  }

  public String getS3Key() {
    return s3Key;
  }

  public Status getStatus() {
    return status;
  }

  public UUID getUploadedBy() {
    return uploadedBy;
  }

  public Instant getUploadedAt() {
    return uploadedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public enum Status {
    PENDING,
    UPLOADED,
    FAILED
  }
}
