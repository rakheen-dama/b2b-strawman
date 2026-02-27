package io.b2mash.b2b.b2bstrawman.template;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "generated_documents")
public class GeneratedDocument {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "template_id", nullable = false)
  private UUID templateId;

  @Enumerated(EnumType.STRING)
  @Column(name = "primary_entity_type", nullable = false, length = 20)
  private TemplateEntityType primaryEntityType;

  @Column(name = "primary_entity_id", nullable = false)
  private UUID primaryEntityId;

  @Column(name = "document_id")
  private UUID documentId;

  @Column(name = "file_name", nullable = false, length = 500)
  private String fileName;

  @Column(name = "s3_key", nullable = false, length = 1000)
  private String s3Key;

  @Column(name = "file_size", nullable = false)
  private long fileSize;

  @Column(name = "generated_by", nullable = false)
  private UUID generatedBy;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "context_snapshot", columnDefinition = "jsonb")
  private Map<String, Object> contextSnapshot;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "warnings", columnDefinition = "jsonb")
  private List<Map<String, Object>> warnings;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "clause_snapshots", columnDefinition = "jsonb")
  private List<Map<String, Object>> clauseSnapshots;

  @Column(name = "generated_at", nullable = false, updatable = false)
  private Instant generatedAt;

  protected GeneratedDocument() {}

  public GeneratedDocument(
      UUID templateId,
      TemplateEntityType primaryEntityType,
      UUID primaryEntityId,
      String fileName,
      String s3Key,
      long fileSize,
      UUID generatedBy) {
    this.templateId = templateId;
    this.primaryEntityType = primaryEntityType;
    this.primaryEntityId = primaryEntityId;
    this.fileName = fileName;
    this.s3Key = s3Key;
    this.fileSize = fileSize;
    this.generatedBy = generatedBy;
    this.generatedAt = Instant.now();
  }

  public void linkToDocument(UUID documentId) {
    this.documentId = documentId;
  }

  // --- Getters ---

  public UUID getId() {
    return id;
  }

  public UUID getTemplateId() {
    return templateId;
  }

  public TemplateEntityType getPrimaryEntityType() {
    return primaryEntityType;
  }

  public UUID getPrimaryEntityId() {
    return primaryEntityId;
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public String getFileName() {
    return fileName;
  }

  public String getS3Key() {
    return s3Key;
  }

  public long getFileSize() {
    return fileSize;
  }

  public UUID getGeneratedBy() {
    return generatedBy;
  }

  public Map<String, Object> getContextSnapshot() {
    return contextSnapshot;
  }

  public void setContextSnapshot(Map<String, Object> contextSnapshot) {
    this.contextSnapshot = contextSnapshot;
  }

  public List<Map<String, Object>> getWarnings() {
    return warnings;
  }

  public void setWarnings(List<Map<String, Object>> warnings) {
    this.warnings = warnings;
  }

  public List<Map<String, Object>> getClauseSnapshots() {
    return clauseSnapshots;
  }

  public void setClauseSnapshots(List<Map<String, Object>> clauseSnapshots) {
    this.clauseSnapshots = clauseSnapshots;
  }

  public Instant getGeneratedAt() {
    return generatedAt;
  }
}
