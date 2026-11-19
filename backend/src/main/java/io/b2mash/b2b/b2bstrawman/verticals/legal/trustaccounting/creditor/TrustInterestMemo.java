package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor;

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
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * The Rule 54.28 figures of one trust account for one month that never reached the account (Phase
 * 92 650B, ADR-384, §16.2.9): gross interest the bank swept to the Fund at source, gross charges
 * levied at source and the VAT included in them, with where the figures came from. A memo is not a
 * posting — it has no effect on any term of the equation, the reconciliation or the monthly list —
 * and is edited in place (one per account and month); every save is audited.
 */
@Entity
@Table(name = "trust_interest_memos")
public class TrustInterestMemo {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "trust_account_id", nullable = false, updatable = false)
  private UUID trustAccountId;

  /** The first day of the month (chk_trust_interest_memo_month). */
  @Column(name = "period_month", nullable = false, updatable = false)
  private LocalDate periodMonth;

  @Column(name = "gross_interest", nullable = false, precision = 15, scale = 2)
  private BigDecimal grossInterest;

  @Column(name = "gross_charges", nullable = false, precision = 15, scale = 2)
  private BigDecimal grossCharges;

  /** VAT included in {@link #grossCharges} (never more than it). */
  @Column(name = "charges_vat", nullable = false, precision = 15, scale = 2)
  private BigDecimal chargesVat;

  @Column(name = "source_note", nullable = false, length = 500)
  private String sourceNote;

  /** The member who saved the memo last; who saved what before is in the audit trail. */
  @Column(name = "recorded_by", nullable = false)
  private UUID recordedBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected TrustInterestMemo() {}

  public TrustInterestMemo(
      UUID trustAccountId,
      YearMonth month,
      BigDecimal grossInterest,
      BigDecimal grossCharges,
      BigDecimal chargesVat,
      String sourceNote,
      UUID recordedBy) {
    this.trustAccountId = trustAccountId;
    this.periodMonth = month.atDay(1);
    correct(grossInterest, grossCharges, chargesVat, sourceNote, recordedBy);
  }

  /** Replaces the figures and the source (an upsert's update). */
  public void correct(
      BigDecimal grossInterest,
      BigDecimal grossCharges,
      BigDecimal chargesVat,
      String sourceNote,
      UUID recordedBy) {
    this.grossInterest = grossInterest;
    this.grossCharges = grossCharges;
    this.chargesVat = chargesVat;
    this.sourceNote = sourceNote;
    this.recordedBy = recordedBy;
  }

  // Truncated to the column's precision (timestamptz = microseconds): on Linux Instant.now()
  // carries
  // nanoseconds, and the save response must return exactly what is stored.
  @PrePersist
  protected void onCreate() {
    this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    this.updatedAt = this.createdAt;
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
  }

  public UUID getId() {
    return id;
  }

  public UUID getTrustAccountId() {
    return trustAccountId;
  }

  public LocalDate getPeriodMonth() {
    return periodMonth;
  }

  public BigDecimal getGrossInterest() {
    return grossInterest;
  }

  public BigDecimal getGrossCharges() {
    return grossCharges;
  }

  public BigDecimal getChargesVat() {
    return chargesVat;
  }

  public String getSourceNote() {
    return sourceNote;
  }

  public UUID getRecordedBy() {
    return recordedBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
