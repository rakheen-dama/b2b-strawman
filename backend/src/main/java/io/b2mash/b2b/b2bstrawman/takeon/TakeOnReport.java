package io.b2mash.b2b.b2bstrawman.takeon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The take-on report of one committed trust take-on (Phase 93 657C, §17.2.7, §17.3.12, ADR-391,
 * FD-15): an immutable snapshot of the position as posted, built inside the commit transaction
 * after the self-check, with the SHA-256 of its canonical JSON. Written once and never updated or
 * deleted (the {@code TrustMonthlyList} shape): {@code @Immutable}, every column {@code updatable =
 * false}, no setters. A client's later anonymisation does not touch it — it is a trust record kept
 * under Rule 54.9.1 (§17.10 X4, D-192/R-4). Rendered files ({@code take_on_report_files}) are
 * 657D's.
 */
@Entity
@Immutable
@Table(name = "take_on_reports")
public class TakeOnReport {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "batch_id", nullable = false, updatable = false)
  private UUID batchId;

  @Column(name = "trust_account_id", nullable = false, updatable = false)
  private UUID trustAccountId;

  @Column(name = "cutover_date", nullable = false, updatable = false)
  private LocalDate cutoverDate;

  /** {@link TakeOnReportSnapshot#toMap()}: ISO dates and two-decimal strings only (R-8). */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "snapshot", columnDefinition = "jsonb", nullable = false, updatable = false)
  private Map<String, Object> snapshot;

  @Column(name = "snapshot_sha256", nullable = false, length = 64, updatable = false)
  private String snapshotSha256;

  /** The member who confirmed the commit. */
  @Column(name = "generated_by", nullable = false, updatable = false)
  private UUID generatedBy;

  @Column(name = "generated_at", nullable = false, updatable = false)
  private Instant generatedAt;

  protected TakeOnReport() {}

  public TakeOnReport(
      UUID batchId,
      UUID trustAccountId,
      LocalDate cutoverDate,
      Map<String, Object> snapshot,
      UUID generatedBy,
      Instant generatedAt) {
    this.batchId = Objects.requireNonNull(batchId, "batchId");
    this.trustAccountId = Objects.requireNonNull(trustAccountId, "trustAccountId");
    this.cutoverDate = Objects.requireNonNull(cutoverDate, "cutoverDate");
    this.snapshot = new LinkedHashMap<>(Objects.requireNonNull(snapshot, "snapshot"));
    this.snapshotSha256 = TakeOnReportSnapshot.sha256(this.snapshot);
    this.generatedBy = Objects.requireNonNull(generatedBy, "generatedBy");
    this.generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
  }

  public UUID getId() {
    return id;
  }

  public UUID getBatchId() {
    return batchId;
  }

  public UUID getTrustAccountId() {
    return trustAccountId;
  }

  public LocalDate getCutoverDate() {
    return cutoverDate;
  }

  public Map<String, Object> getSnapshot() {
    return snapshot;
  }

  public String getSnapshotSha256() {
    return snapshotSha256;
  }

  public UUID getGeneratedBy() {
    return generatedBy;
  }

  public Instant getGeneratedAt() {
    return generatedAt;
  }
}
