package io.b2mash.b2b.b2bstrawman.datarequest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processing_activities")
public class ProcessingActivity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "category", nullable = false, length = 100)
  private String category;

  @Column(name = "description", nullable = false, columnDefinition = "TEXT")
  private String description;

  @Column(name = "legal_basis", nullable = false, length = 50)
  private String legalBasis;

  @Column(name = "data_subjects", nullable = false, length = 255)
  private String dataSubjects;

  @Column(name = "retention_period", nullable = false, length = 100)
  private String retentionPeriod;

  @Column(name = "recipients", length = 255)
  private String recipients;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ProcessingActivity() {}

  public ProcessingActivity(
      String category,
      String description,
      String legalBasis,
      String dataSubjects,
      String retentionPeriod,
      String recipients) {
    this.category = category;
    this.description = description;
    this.legalBasis = legalBasis;
    this.dataSubjects = dataSubjects;
    this.retentionPeriod = retentionPeriod;
    this.recipients = recipients;
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public String getCategory() {
    return category;
  }

  public String getDescription() {
    return description;
  }

  public String getLegalBasis() {
    return legalBasis;
  }

  public String getDataSubjects() {
    return dataSubjects;
  }

  public String getRetentionPeriod() {
    return retentionPeriod;
  }

  public String getRecipients() {
    return recipients;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void update(
      String category,
      String description,
      String legalBasis,
      String dataSubjects,
      String retentionPeriod,
      String recipients) {
    this.category = category;
    this.description = description;
    this.legalBasis = legalBasis;
    this.dataSubjects = dataSubjects;
    this.retentionPeriod = retentionPeriod;
    this.recipients = recipients;
    this.updatedAt = Instant.now();
  }
}
