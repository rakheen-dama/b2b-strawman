package io.b2mash.b2b.b2bstrawman.verticals.legal.closure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Audit trail for every closure attempt on a matter, successful or not (Phase 67, Epic 489A;
 * architecture §67.2.2, ADR-248).
 *
 * <p>Separate from generic {@code AuditEvent} because the closure-gate report is rich structured
 * data that deserves first-class storage — the {@code gate_report} JSONB column records the full
 * per-gate outcome at the moment of close.
 *
 * <p>Audit-immutable fields (id, projectId, closedBy, closedAt, reason, gateReport, overrideUsed,
 * overrideJustification) have no public setters. Reopen-related fields (reopenedAt, reopenedBy,
 * reopenNotes) and closureLetterDocumentId are populated post-hoc via {@link #recordReopen(Instant,
 * UUID, String)} / {@link #setClosureLetterDocumentId(UUID)}.
 */
@Entity
@Table(name = "matter_closure_log")
public class MatterClosureLog {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "closed_by", nullable = false)
  private UUID closedBy;

  @Column(name = "closed_at", nullable = false)
  private Instant closedAt;

  /**
   * Closure reason. Stored as varchar (per ADR-238). Values: CONCLUDED, CLIENT_TERMINATED,
   * REFERRED_OUT, OTHER.
   */
  @Column(name = "reason", nullable = false, length = 40)
  private String reason;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  /** Full per-gate result at the moment of close. JSONB, NOT NULL — written by the service. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "gate_report", columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> gateReport;

  @Column(name = "override_used", nullable = false)
  private boolean overrideUsed;

  @Column(name = "override_justification", columnDefinition = "TEXT")
  private String overrideJustification;

  @Column(name = "closure_letter_document_id")
  private UUID closureLetterDocumentId;

  @Column(name = "reopened_at")
  private Instant reopenedAt;

  @Column(name = "reopened_by")
  private UUID reopenedBy;

  @Column(name = "reopen_notes", columnDefinition = "TEXT")
  private String reopenNotes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** JPA-required no-arg constructor. */
  protected MatterClosureLog() {}

  /**
   * Service-layer constructor for recording a closure. Override-mode callers MUST supply {@code
   * overrideJustification} — the DB CHECK enforces {@code >= 20 chars} when {@code overrideUsed =
   * true}, so service-layer validation should gate earlier.
   */
  public MatterClosureLog(
      UUID projectId,
      UUID closedBy,
      Instant closedAt,
      String reason,
      String notes,
      Map<String, Object> gateReport,
      boolean overrideUsed,
      String overrideJustification) {
    this.projectId = Objects.requireNonNull(projectId, "projectId must not be null");
    this.closedBy = Objects.requireNonNull(closedBy, "closedBy must not be null");
    this.closedAt = Objects.requireNonNull(closedAt, "closedAt must not be null");
    this.reason = Objects.requireNonNull(reason, "reason must not be null");
    this.gateReport = Objects.requireNonNull(gateReport, "gateReport must not be null");
    this.notes = notes;
    this.overrideUsed = overrideUsed;
    this.overrideJustification = overrideJustification;
  }

  @PrePersist
  void onPrePersist() {
    if (this.createdAt == null) {
      this.createdAt = Instant.now();
    }
  }

  // --- Post-hoc mutation helpers ---

  /**
   * Records a reopen on this closure log row. All three reopen fields must be non-null — the DB
   * CHECK {@code ck_matter_closure_log_reopen_consistent} enforces "all-set or all-null".
   */
  public void recordReopen(Instant reopenedAt, UUID reopenedBy, String reopenNotes) {
    this.reopenedAt = Objects.requireNonNull(reopenedAt, "reopenedAt must not be null");
    this.reopenedBy = Objects.requireNonNull(reopenedBy, "reopenedBy must not be null");
    this.reopenNotes = Objects.requireNonNull(reopenNotes, "reopenNotes must not be null");
  }

  public void setClosureLetterDocumentId(UUID closureLetterDocumentId) {
    this.closureLetterDocumentId = closureLetterDocumentId;
  }

  // --- Getters ---

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public UUID getClosedBy() {
    return closedBy;
  }

  public Instant getClosedAt() {
    return closedAt;
  }

  public String getReason() {
    return reason;
  }

  public String getNotes() {
    return notes;
  }

  public Map<String, Object> getGateReport() {
    return gateReport;
  }

  public boolean isOverrideUsed() {
    return overrideUsed;
  }

  public String getOverrideJustification() {
    return overrideJustification;
  }

  public UUID getClosureLetterDocumentId() {
    return closureLetterDocumentId;
  }

  public Instant getReopenedAt() {
    return reopenedAt;
  }

  public UUID getReopenedBy() {
    return reopenedBy;
  }

  public String getReopenNotes() {
    return reopenNotes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
