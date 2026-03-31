package io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "conflict_checks")
public class ConflictCheck {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "checked_name", nullable = false, length = 300)
  private String checkedName;

  @Column(name = "checked_id_number", length = 20)
  private String checkedIdNumber;

  @Column(name = "checked_registration_number", length = 30)
  private String checkedRegistrationNumber;

  @Column(name = "check_type", nullable = false, length = 20)
  private String checkType;

  @Column(name = "result", nullable = false, length = 20)
  private String result;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "conflicts_found", columnDefinition = "jsonb")
  private String conflictsFound;

  @Column(name = "resolution", length = 30)
  private String resolution;

  @Column(name = "resolution_notes", columnDefinition = "TEXT")
  private String resolutionNotes;

  @Column(name = "waiver_document_id")
  private UUID waiverDocumentId;

  @Column(name = "checked_by", nullable = false)
  private UUID checkedBy;

  @Column(name = "resolved_by")
  private UUID resolvedBy;

  @Column(name = "checked_at", nullable = false, updatable = false)
  private Instant checkedAt;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "customer_id")
  private UUID customerId;

  @Column(name = "project_id")
  private UUID projectId;

  protected ConflictCheck() {}

  public ConflictCheck(
      String checkedName,
      String checkedIdNumber,
      String checkedRegistrationNumber,
      String checkType,
      String result,
      String conflictsFound,
      UUID checkedBy,
      UUID customerId,
      UUID projectId) {
    this.checkedName = checkedName;
    this.checkedIdNumber = checkedIdNumber;
    this.checkedRegistrationNumber = checkedRegistrationNumber;
    this.checkType = checkType;
    this.result = result;
    this.conflictsFound = conflictsFound;
    this.checkedBy = checkedBy;
    this.customerId = customerId;
    this.projectId = projectId;
    this.checkedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getCheckedName() {
    return checkedName;
  }

  public String getCheckedIdNumber() {
    return checkedIdNumber;
  }

  public String getCheckedRegistrationNumber() {
    return checkedRegistrationNumber;
  }

  public String getCheckType() {
    return checkType;
  }

  public String getResult() {
    return result;
  }

  public String getConflictsFound() {
    return conflictsFound;
  }

  public String getResolution() {
    return resolution;
  }

  public void setResolution(String resolution) {
    this.resolution = resolution;
  }

  public String getResolutionNotes() {
    return resolutionNotes;
  }

  public void setResolutionNotes(String resolutionNotes) {
    this.resolutionNotes = resolutionNotes;
  }

  public UUID getWaiverDocumentId() {
    return waiverDocumentId;
  }

  public void setWaiverDocumentId(UUID waiverDocumentId) {
    this.waiverDocumentId = waiverDocumentId;
  }

  public UUID getCheckedBy() {
    return checkedBy;
  }

  public UUID getResolvedBy() {
    return resolvedBy;
  }

  public void setResolvedBy(UUID resolvedBy) {
    this.resolvedBy = resolvedBy;
  }

  public Instant getCheckedAt() {
    return checkedAt;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public void setResolvedAt(Instant resolvedAt) {
    this.resolvedAt = resolvedAt;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public UUID getProjectId() {
    return projectId;
  }
}
