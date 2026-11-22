package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "bank_statements")
public class BankStatement {

  /**
   * The terminal status of a replaced or discarded statement (FU-6b, ruling F-646C-1 / F-652C-1).
   * Never deleted: the file and the lines stay for the audit trail; who, when and why are kept on
   * the row ({@code chk_bank_stmt_retired}, V30).
   */
  public static final String RETIRED = "RETIRED";

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "trust_account_id", nullable = false)
  private UUID trustAccountId;

  @Column(name = "period_start", nullable = false)
  private LocalDate periodStart;

  @Column(name = "period_end", nullable = false)
  private LocalDate periodEnd;

  @Column(name = "opening_balance", nullable = false, precision = 15, scale = 2)
  private BigDecimal openingBalance;

  @Column(name = "closing_balance", nullable = false, precision = 15, scale = 2)
  private BigDecimal closingBalance;

  @Column(name = "file_key", nullable = false, length = 500)
  private String fileKey;

  @Column(name = "file_name", nullable = false, length = 200)
  private String fileName;

  @Column(name = "format", nullable = false, length = 20)
  private String format;

  @Column(name = "line_count", nullable = false)
  private int lineCount;

  @Column(name = "matched_count", nullable = false)
  private int matchedCount;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "imported_by", nullable = false)
  private UUID importedBy;

  @Column(name = "retired_at")
  private Instant retiredAt;

  @Column(name = "retired_by")
  private UUID retiredBy;

  @Column(name = "retired_reason", length = 500)
  private String retiredReason;

  @Column(name = "replaced_by_statement_id")
  private UUID replacedByStatementId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected BankStatement() {}

  public BankStatement(
      UUID trustAccountId,
      LocalDate periodStart,
      LocalDate periodEnd,
      BigDecimal openingBalance,
      BigDecimal closingBalance,
      String fileKey,
      String fileName,
      String format,
      int lineCount,
      UUID importedBy) {
    this.trustAccountId = trustAccountId;
    this.periodStart = periodStart;
    this.periodEnd = periodEnd;
    this.openingBalance = openingBalance;
    this.closingBalance = closingBalance;
    this.fileKey = fileKey;
    this.fileName = fileName;
    this.format = format;
    this.lineCount = lineCount;
    this.matchedCount = 0;
    this.status = "IMPORTED";
    this.importedBy = importedBy;
  }

  @PrePersist
  protected void onCreate() {
    var now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getTrustAccountId() {
    return trustAccountId;
  }

  public LocalDate getPeriodStart() {
    return periodStart;
  }

  public LocalDate getPeriodEnd() {
    return periodEnd;
  }

  public BigDecimal getOpeningBalance() {
    return openingBalance;
  }

  public BigDecimal getClosingBalance() {
    return closingBalance;
  }

  public String getFileKey() {
    return fileKey;
  }

  public String getFileName() {
    return fileName;
  }

  public String getFormat() {
    return format;
  }

  public int getLineCount() {
    return lineCount;
  }

  public int getMatchedCount() {
    return matchedCount;
  }

  public void setMatchedCount(int matchedCount) {
    this.matchedCount = matchedCount;
  }

  /** One more line matched or recorded; call on a row locked with {@code findByIdForUpdate}. */
  public void incrementMatched() {
    this.matchedCount++;
  }

  /** One fewer line matched or recorded (never below zero); call on a locked row. */
  public void decrementMatched() {
    this.matchedCount = Math.max(0, this.matchedCount - 1);
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public boolean isRetired() {
    return RETIRED.equals(status);
  }

  /**
   * Retires the statement (FU-6b): replaced by {@code replacedBy}, or discarded when it is null.
   * Call on the row locked with {@code findByIdForUpdate}, once its matches are released.
   */
  public void retire(UUID retiredBy, Instant retiredAt, String reason, UUID replacedBy) {
    this.status = RETIRED;
    this.retiredBy = retiredBy;
    this.retiredAt = retiredAt;
    this.retiredReason = reason;
    this.replacedByStatementId = replacedBy;
  }

  public Instant getRetiredAt() {
    return retiredAt;
  }

  public UUID getRetiredBy() {
    return retiredBy;
  }

  public String getRetiredReason() {
    return retiredReason;
  }

  public UUID getReplacedByStatementId() {
    return replacedByStatementId;
  }

  public UUID getImportedBy() {
    return importedBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
