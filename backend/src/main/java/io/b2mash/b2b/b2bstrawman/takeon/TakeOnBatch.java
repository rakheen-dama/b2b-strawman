package io.b2mash.b2b.b2bstrawman.takeon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One attempt to take on one dataset from the firm's previous system (Phase 93, §17.2.1, ADR-385):
 * the unit of validation, attestation/sign-off, commit and audit.
 *
 * <p>The {@code BillingRun} shape: guarded transition methods, no public status setter, instants
 * stamped here. A transition from the wrong status throws {@link IllegalStateException} with a
 * user-facing message; services convert it to a 409 ProblemDetail (the entity never builds HTTP
 * errors). The trust position inputs ({@code bank_balance} … {@code mid_month_acknowledged}) are
 * mapped read-only here; slice 657B adds their setters with the form.
 */
@Entity
@Table(name = "take_on_batches")
public class TakeOnBatch {

  private static final int LAST_ERROR_MAX = 1000;

  /** Validation roll-ups written in the validating transaction (§17.10 X2). */
  public record Counts(int total, int valid, int error, int duplicate, int blocks) {}

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "dataset", nullable = false, length = 30)
  private TakeOnDataset dataset;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private TakeOnBatchStatus status;

  @Column(name = "name", length = 200)
  private String name;

  @Column(name = "cutover_date")
  private LocalDate cutoverDate;

  @Column(name = "trust_account_id")
  private UUID trustAccountId;

  @Column(name = "bank_balance", precision = 15, scale = 2)
  private BigDecimal bankBalance;

  @Column(name = "outstanding_deposits_total", precision = 15, scale = 2)
  private BigDecimal outstandingDepositsTotal;

  @Column(name = "outstanding_payments_total", precision = 15, scale = 2)
  private BigDecimal outstandingPaymentsTotal;

  @Column(name = "cashbook_balance", precision = 15, scale = 2)
  private BigDecimal cashbookBalance;

  @Column(name = "fund_ledger_balance", nullable = false, precision = 15, scale = 2)
  private BigDecimal fundLedgerBalance;

  @Column(name = "invested_total", nullable = false, precision = 15, scale = 2)
  private BigDecimal investedTotal;

  @Column(name = "mid_month_acknowledged", nullable = false)
  private boolean midMonthAcknowledged;

  @Column(name = "validation_hash", length = 64)
  private String validationHash;

  @Column(name = "validated_at")
  private Instant validatedAt;

  @Column(name = "total_rows", nullable = false)
  private int totalRows;

  @Column(name = "valid_rows", nullable = false)
  private int validRows;

  @Column(name = "error_rows", nullable = false)
  private int errorRows;

  @Column(name = "duplicate_rows", nullable = false)
  private int duplicateRows;

  @Column(name = "block_count", nullable = false)
  private int blockCount;

  @Column(name = "created_rows", nullable = false)
  private int createdRows;

  @Column(name = "skipped_rows", nullable = false)
  private int skippedRows;

  @Column(name = "failed_rows", nullable = false)
  private int failedRows;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "summary", columnDefinition = "jsonb")
  private Map<String, Object> summary;

  @Column(name = "last_error", length = LAST_ERROR_MAX)
  private String lastError;

  @Column(name = "created_by", nullable = false, updatable = false)
  private UUID createdBy;

  @Column(name = "commit_requested_by")
  private UUID commitRequestedBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "commit_started_at")
  private Instant commitStartedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "cancelled_by")
  private UUID cancelledBy;

  protected TakeOnBatch() {}

  public TakeOnBatch(TakeOnDataset dataset, UUID trustAccountId, String name, UUID createdBy) {
    this.dataset = Objects.requireNonNull(dataset, "dataset");
    this.trustAccountId = trustAccountId;
    this.name = name;
    this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
    this.status = TakeOnBatchStatus.UPLOADED;
    this.fundLedgerBalance = BigDecimal.ZERO.setScale(2);
    this.investedTotal = BigDecimal.ZERO.setScale(2);
    this.midMonthAcknowledged = false;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  // --- Transitions ---

  /**
   * Every present source is mapped. From {@code UPLOADED}; a no-op from {@code MAPPED}; from {@code
   * VALIDATED} the mapping or position inputs changed, so the batch needs validating again.
   */
  public void markMapped() {
    switch (status) {
      case UPLOADED, VALIDATED -> {
        this.status = TakeOnBatchStatus.MAPPED;
        this.updatedAt = Instant.now();
      }
      case MAPPED -> {
        // no-op: already mapped
      }
      default ->
          throw new IllegalStateException(
              "Only an uploaded, mapped or validated take-on batch can be mapped.");
    }
  }

  /** A source was added, replaced, re-read or removed: the batch needs mapping again. */
  public void returnToUploaded() {
    requireOpen("Only an open take-on batch can take a new or changed file.");
    this.status = TakeOnBatchStatus.UPLOADED;
    this.updatedAt = Instant.now();
  }

  /**
   * Guard for the dry run: the batch is {@code MAPPED} or {@code VALIDATED} and, for a trust or
   * debtor take-on, carries its cutover date (CHECK {@code chk_take_on_batch_cutover}).
   */
  public void requireValidatable() {
    if (status != TakeOnBatchStatus.MAPPED && status != TakeOnBatchStatus.VALIDATED) {
      throw new IllegalStateException("Only a mapped or validated take-on batch can be validated.");
    }
  }

  /** The dataset needs a cutover date and none is set (checked by the dry run, C-14). */
  public boolean missingCutoverDate() {
    return dataset.requiresCutoverDate() && cutoverDate == null;
  }

  /** The dry run finished: record the hash every later sign-off and commit binds to. */
  public void markValidated(String hash, Counts counts, Map<String, Object> summary) {
    requireValidatable();
    if (missingCutoverDate()) {
      throw new IllegalStateException("Enter the cutover date before validating.");
    }
    Objects.requireNonNull(hash, "hash");
    Objects.requireNonNull(counts, "counts");
    this.validationHash = hash;
    this.totalRows = counts.total();
    this.validRows = counts.valid();
    this.errorRows = counts.error();
    this.duplicateRows = counts.duplicate();
    this.blockCount = counts.blocks();
    this.summary = summary;
    this.status = TakeOnBatchStatus.VALIDATED;
    this.validatedAt = Instant.now();
    this.updatedAt = this.validatedAt;
  }

  /**
   * Sets the take-on "as at" date. Only while the batch is open; on a validated batch a change
   * moves it back to {@code MAPPED} (the position inputs are part of the validation hash).
   */
  public void setCutoverDate(LocalDate date) {
    requireOpen("The cutover date can only be changed on an open take-on batch.");
    if (Objects.equals(this.cutoverDate, date)) {
      return;
    }
    this.cutoverDate = date;
    if (status == TakeOnBatchStatus.VALIDATED) {
      this.status = TakeOnBatchStatus.MAPPED;
    }
    this.updatedAt = Instant.now();
  }

  /**
   * Claims the commit (first called in 655A). The batch must be {@code VALIDATED}, and both the
   * batch's hash and the live sign-off's hash must equal {@code hash}. Datasets without a sign-off
   * pass {@link TakeOnSignOff#notRequired(String)}.
   */
  public void claimCommit(String hash, TakeOnSignOff liveSignOff, UUID requestedBy) {
    if (status != TakeOnBatchStatus.VALIDATED) {
      throw new IllegalStateException("Only a validated take-on batch can be committed.");
    }
    Objects.requireNonNull(liveSignOff, "liveSignOff");
    if (!liveSignOff.isLive()) {
      throw new IllegalStateException("The sign-off for this take-on batch has been withdrawn.");
    }
    if (hash == null
        || !Objects.equals(validationHash, hash)
        || !hash.equals(liveSignOff.getValidationHash())) {
      throw new IllegalStateException(
          "This take-on batch changed after it was validated or signed off. Validate it again.");
    }
    this.status = TakeOnBatchStatus.COMMITTING;
    this.commitRequestedBy = requestedBy;
    this.commitStartedAt = Instant.now();
    this.updatedAt = this.commitStartedAt;
  }

  /** The commit finished: every row is terminal / the trust transaction committed. */
  public void complete(int created, int skipped, int failed) {
    if (status != TakeOnBatchStatus.COMMITTING) {
      throw new IllegalStateException("Only a committing take-on batch can be completed.");
    }
    this.createdRows = created;
    this.skippedRows = skipped;
    this.failedRows = failed;
    this.status = TakeOnBatchStatus.COMPLETED;
    this.completedAt = Instant.now();
    this.updatedAt = this.completedAt;
  }

  /** An all-or-nothing commit failed: back to {@code VALIDATED} with the reason. */
  public void returnToValidated(String error) {
    if (status != TakeOnBatchStatus.COMMITTING) {
      throw new IllegalStateException(
          "Only a committing take-on batch can return to validated after a failure.");
    }
    this.lastError =
        error != null && error.length() > LAST_ERROR_MAX
            ? error.substring(0, LAST_ERROR_MAX)
            : error;
    this.status = TakeOnBatchStatus.VALIDATED;
    this.updatedAt = Instant.now();
  }

  /**
   * Cancels an open batch. {@code cancelledBy} is null when the abandoned-batch purge job (which
   * has no member) cancels it.
   */
  public void cancel(UUID cancelledBy) {
    requireOpen("Only an open take-on batch can be cancelled.");
    this.status = TakeOnBatchStatus.CANCELLED;
    this.cancelledBy = cancelledBy;
    this.cancelledAt = Instant.now();
    this.updatedAt = this.cancelledAt;
  }

  private void requireOpen(String message) {
    if (!status.isOpen()) {
      throw new IllegalStateException(message);
    }
  }

  // --- Getters ---

  public UUID getId() {
    return id;
  }

  public TakeOnDataset getDataset() {
    return dataset;
  }

  public TakeOnBatchStatus getStatus() {
    return status;
  }

  public String getName() {
    return name;
  }

  public LocalDate getCutoverDate() {
    return cutoverDate;
  }

  public UUID getTrustAccountId() {
    return trustAccountId;
  }

  public BigDecimal getBankBalance() {
    return bankBalance;
  }

  public BigDecimal getOutstandingDepositsTotal() {
    return outstandingDepositsTotal;
  }

  public BigDecimal getOutstandingPaymentsTotal() {
    return outstandingPaymentsTotal;
  }

  public BigDecimal getCashbookBalance() {
    return cashbookBalance;
  }

  public BigDecimal getFundLedgerBalance() {
    return fundLedgerBalance;
  }

  public BigDecimal getInvestedTotal() {
    return investedTotal;
  }

  public boolean isMidMonthAcknowledged() {
    return midMonthAcknowledged;
  }

  public String getValidationHash() {
    return validationHash;
  }

  public Instant getValidatedAt() {
    return validatedAt;
  }

  public int getTotalRows() {
    return totalRows;
  }

  public int getValidRows() {
    return validRows;
  }

  public int getErrorRows() {
    return errorRows;
  }

  public int getDuplicateRows() {
    return duplicateRows;
  }

  public int getBlockCount() {
    return blockCount;
  }

  public int getCreatedRows() {
    return createdRows;
  }

  public int getSkippedRows() {
    return skippedRows;
  }

  public int getFailedRows() {
    return failedRows;
  }

  public Map<String, Object> getSummary() {
    return summary;
  }

  public String getLastError() {
    return lastError;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public UUID getCommitRequestedBy() {
    return commitRequestedBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getCommitStartedAt() {
    return commitStartedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public Instant getCancelledAt() {
    return cancelledAt;
  }

  public UUID getCancelledBy() {
    return cancelledBy;
  }
}
