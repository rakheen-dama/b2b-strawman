package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "bank_statement_lines")
public class BankStatementLine {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "bank_statement_id", nullable = false)
  private UUID bankStatementId;

  @Column(name = "line_number", nullable = false)
  private int lineNumber;

  @Column(name = "transaction_date", nullable = false)
  private LocalDate transactionDate;

  @Column(name = "description", nullable = false, length = 500)
  private String description;

  @Column(name = "reference", length = 200)
  private String reference;

  @Column(name = "amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal amount;

  @Column(name = "running_balance", precision = 15, scale = 2)
  private BigDecimal runningBalance;

  @Column(name = "match_status", nullable = false, length = 20)
  private String matchStatus;

  @Column(name = "trust_transaction_id")
  private UUID trustTransactionId;

  @Column(name = "match_confidence", precision = 3, scale = 2)
  private BigDecimal matchConfidence;

  @Column(name = "excluded_reason", length = 200)
  private String excludedReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected BankStatementLine() {}

  public BankStatementLine(
      UUID bankStatementId,
      int lineNumber,
      LocalDate transactionDate,
      String description,
      String reference,
      BigDecimal amount,
      BigDecimal runningBalance) {
    this.bankStatementId = bankStatementId;
    this.lineNumber = lineNumber;
    this.transactionDate = transactionDate;
    this.description = description;
    this.reference = reference;
    this.amount = amount;
    this.runningBalance = runningBalance;
    this.matchStatus = "UNMATCHED";
  }

  @PrePersist
  protected void onCreate() {
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getBankStatementId() {
    return bankStatementId;
  }

  public int getLineNumber() {
    return lineNumber;
  }

  public LocalDate getTransactionDate() {
    return transactionDate;
  }

  public String getDescription() {
    return description;
  }

  public String getReference() {
    return reference;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public BigDecimal getRunningBalance() {
    return runningBalance;
  }

  public String getMatchStatus() {
    return matchStatus;
  }

  public void setMatchStatus(String matchStatus) {
    this.matchStatus = matchStatus;
  }

  public UUID getTrustTransactionId() {
    return trustTransactionId;
  }

  public void setTrustTransactionId(UUID trustTransactionId) {
    this.trustTransactionId = trustTransactionId;
  }

  public BigDecimal getMatchConfidence() {
    return matchConfidence;
  }

  public void setMatchConfidence(BigDecimal matchConfidence) {
    this.matchConfidence = matchConfidence;
  }

  public String getExcludedReason() {
    return excludedReason;
  }

  public void setExcludedReason(String excludedReason) {
    this.excludedReason = excludedReason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
