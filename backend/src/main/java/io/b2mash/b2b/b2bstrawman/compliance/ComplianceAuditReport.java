package io.b2mash.b2b.b2bstrawman.compliance;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
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
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "compliance_audit_reports")
public class ComplianceAuditReport {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "execution_id", nullable = false)
  private AiExecution execution;

  @Column(name = "overall_grade", nullable = false, length = 5)
  private String overallGrade;

  @Column(name = "overall_assessment", nullable = false, columnDefinition = "TEXT")
  private String overallAssessment;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "category_scores", columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> categoryScores;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "published_by")
  private UUID publishedBy;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "updated_by", nullable = false)
  private UUID updatedBy;

  protected ComplianceAuditReport() {}

  public ComplianceAuditReport(
      AiExecution execution,
      String overallGrade,
      String overallAssessment,
      Map<String, Object> categoryScores,
      UUID createdBy) {
    this.execution = execution;
    this.overallGrade = overallGrade;
    this.overallAssessment = overallAssessment;
    this.categoryScores = categoryScores;
    this.status = ReportStatus.DRAFT.name();
    this.createdBy = createdBy;
    this.updatedBy = createdBy;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void publish(UUID publisherId) {
    this.status = ReportStatus.PUBLISHED.name();
    this.publishedBy = publisherId;
    this.publishedAt = Instant.now();
    this.updatedBy = publisherId;
    this.updatedAt = Instant.now();
  }

  public void archive(UUID archivedBy) {
    if (!ReportStatus.PUBLISHED.name().equals(this.status)) {
      throw new InvalidStateException(
          "Invalid report status transition",
          "Only PUBLISHED reports can be archived, but current status is " + this.status);
    }
    this.status = ReportStatus.ARCHIVED.name();
    this.updatedBy = archivedBy;
    this.updatedAt = Instant.now();
  }

  // -- Getters --

  public UUID getId() {
    return id;
  }

  public AiExecution getExecution() {
    return execution;
  }

  public String getOverallGrade() {
    return overallGrade;
  }

  public String getOverallAssessment() {
    return overallAssessment;
  }

  public Map<String, Object> getCategoryScores() {
    return categoryScores;
  }

  public String getStatus() {
    return status;
  }

  public UUID getPublishedBy() {
    return publishedBy;
  }

  public Instant getPublishedAt() {
    return publishedAt;
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
