package io.b2mash.b2b.b2bstrawman.compliance;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "compliance_audit_findings")
public class ComplianceAuditFinding {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "report_id", nullable = false)
  private ComplianceAuditReport report;

  @Column(name = "finding_id", nullable = false, length = 10)
  private String findingId;

  @Column(name = "severity", nullable = false, length = 10)
  private String severity;

  @Column(name = "category", nullable = false, length = 30)
  private String category;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "description", nullable = false, columnDefinition = "TEXT")
  private String description;

  @Column(name = "regulatory_basis", columnDefinition = "TEXT")
  private String regulatoryBasis;

  @Column(name = "remediation", columnDefinition = "TEXT")
  private String remediation;

  @Column(name = "entity_type", length = 30)
  private String entityType;

  @Column(name = "entity_id")
  private UUID entityId;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "resolved_by")
  private UUID resolvedBy;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "resolution_notes", columnDefinition = "TEXT")
  private String resolutionNotes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "updated_by", nullable = false)
  private UUID updatedBy;

  protected ComplianceAuditFinding() {}

  public ComplianceAuditFinding(
      ComplianceAuditReport report,
      String findingId,
      String severity,
      String category,
      String title,
      String description,
      String regulatoryBasis,
      String remediation,
      String entityType,
      UUID entityId,
      UUID createdBy) {
    this.report = report;
    this.findingId = findingId;
    this.severity = severity;
    this.category = category;
    this.title = title;
    this.description = description;
    this.regulatoryBasis = regulatoryBasis;
    this.remediation = remediation;
    this.entityType = entityType;
    this.entityId = entityId;
    this.status = FindingStatus.OPEN.name();
    this.createdBy = createdBy;
    this.updatedBy = createdBy;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void acknowledge(UUID memberId) {
    requireStatus(FindingStatus.OPEN, FindingStatus.ACKNOWLEDGED);
    this.status = FindingStatus.ACKNOWLEDGED.name();
    this.updatedBy = memberId;
    this.updatedAt = Instant.now();
  }

  public void startProgress(UUID memberId) {
    requireStatus(FindingStatus.ACKNOWLEDGED, FindingStatus.IN_PROGRESS);
    this.status = FindingStatus.IN_PROGRESS.name();
    this.updatedBy = memberId;
    this.updatedAt = Instant.now();
  }

  public void resolve(UUID memberId, String notes) {
    requireStatus(FindingStatus.IN_PROGRESS, FindingStatus.RESOLVED);
    this.status = FindingStatus.RESOLVED.name();
    this.resolvedBy = memberId;
    this.resolvedAt = Instant.now();
    this.resolutionNotes = notes;
    this.updatedBy = memberId;
    this.updatedAt = Instant.now();
  }

  public void markFalsePositive(UUID memberId, String notes) {
    requireStatus(FindingStatus.IN_PROGRESS, FindingStatus.FALSE_POSITIVE);
    this.status = FindingStatus.FALSE_POSITIVE.name();
    this.resolvedBy = memberId;
    this.resolvedAt = Instant.now();
    this.resolutionNotes = notes;
    this.updatedBy = memberId;
    this.updatedAt = Instant.now();
  }

  private void requireStatus(FindingStatus expected, FindingStatus target) {
    if (!expected.name().equals(this.status)) {
      throw new InvalidStateException(
          "Invalid finding status transition",
          "Cannot transition from "
              + this.status
              + " to "
              + target.name()
              + "; expected current status "
              + expected.name());
    }
  }

  // -- Getters --

  public UUID getId() {
    return id;
  }

  public ComplianceAuditReport getReport() {
    return report;
  }

  public String getFindingId() {
    return findingId;
  }

  public String getSeverity() {
    return severity;
  }

  public String getCategory() {
    return category;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public String getRegulatoryBasis() {
    return regulatoryBasis;
  }

  public String getRemediation() {
    return remediation;
  }

  public String getEntityType() {
    return entityType;
  }

  public UUID getEntityId() {
    return entityId;
  }

  public String getStatus() {
    return status;
  }

  public UUID getResolvedBy() {
    return resolvedBy;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public String getResolutionNotes() {
    return resolutionNotes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public UUID getUpdatedBy() {
    return updatedBy;
  }
}
