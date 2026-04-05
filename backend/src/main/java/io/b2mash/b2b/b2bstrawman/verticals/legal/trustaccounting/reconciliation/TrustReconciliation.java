package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "trust_reconciliations")
public class TrustReconciliation {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "trust_account_id", nullable = false)
  private UUID trustAccountId;

  @Column(name = "period_end", nullable = false)
  private LocalDate periodEnd;

  @Column(name = "bank_statement_id")
  private UUID bankStatementId;

  @Column(name = "bank_balance", nullable = false, precision = 15, scale = 2)
  private BigDecimal bankBalance;

  @Column(name = "cashbook_balance", nullable = false, precision = 15, scale = 2)
  private BigDecimal cashbookBalance;

  @Column(name = "client_ledger_total", nullable = false, precision = 15, scale = 2)
  private BigDecimal clientLedgerTotal;

  @Column(name = "outstanding_deposits", nullable = false, precision = 15, scale = 2)
  private BigDecimal outstandingDeposits;

  @Column(name = "outstanding_payments", nullable = false, precision = 15, scale = 2)
  private BigDecimal outstandingPayments;

  @Column(name = "adjusted_bank_balance", nullable = false, precision = 15, scale = 2)
  private BigDecimal adjustedBankBalance;

  @Column(name = "is_balanced", nullable = false)
  private boolean isBalanced;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ReconciliationStatus status;

  @Column(name = "completed_by")
  private UUID completedBy;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "notes")
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected TrustReconciliation() {}

  public TrustReconciliation(UUID trustAccountId, LocalDate periodEnd, UUID bankStatementId) {
    this.trustAccountId = trustAccountId;
    this.periodEnd = periodEnd;
    this.bankStatementId = bankStatementId;
    this.bankBalance = BigDecimal.ZERO;
    this.cashbookBalance = BigDecimal.ZERO;
    this.clientLedgerTotal = BigDecimal.ZERO;
    this.outstandingDeposits = BigDecimal.ZERO;
    this.outstandingPayments = BigDecimal.ZERO;
    this.adjustedBankBalance = BigDecimal.ZERO;
    this.isBalanced = false;
    this.status = ReconciliationStatus.DRAFT;
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

  public LocalDate getPeriodEnd() {
    return periodEnd;
  }

  public UUID getBankStatementId() {
    return bankStatementId;
  }

  public BigDecimal getBankBalance() {
    return bankBalance;
  }

  public void setBankBalance(BigDecimal bankBalance) {
    this.bankBalance = bankBalance;
  }

  public BigDecimal getCashbookBalance() {
    return cashbookBalance;
  }

  public void setCashbookBalance(BigDecimal cashbookBalance) {
    this.cashbookBalance = cashbookBalance;
  }

  public BigDecimal getClientLedgerTotal() {
    return clientLedgerTotal;
  }

  public void setClientLedgerTotal(BigDecimal clientLedgerTotal) {
    this.clientLedgerTotal = clientLedgerTotal;
  }

  public BigDecimal getOutstandingDeposits() {
    return outstandingDeposits;
  }

  public void setOutstandingDeposits(BigDecimal outstandingDeposits) {
    this.outstandingDeposits = outstandingDeposits;
  }

  public BigDecimal getOutstandingPayments() {
    return outstandingPayments;
  }

  public void setOutstandingPayments(BigDecimal outstandingPayments) {
    this.outstandingPayments = outstandingPayments;
  }

  public BigDecimal getAdjustedBankBalance() {
    return adjustedBankBalance;
  }

  public void setAdjustedBankBalance(BigDecimal adjustedBankBalance) {
    this.adjustedBankBalance = adjustedBankBalance;
  }

  public boolean isBalanced() {
    return isBalanced;
  }

  public void setBalanced(boolean balanced) {
    this.isBalanced = balanced;
  }

  public ReconciliationStatus getStatus() {
    return status;
  }

  public void setStatus(ReconciliationStatus status) {
    this.status = status;
  }

  public UUID getCompletedBy() {
    return completedBy;
  }

  public void setCompletedBy(UUID completedBy) {
    this.completedBy = completedBy;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
