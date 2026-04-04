package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "trust_accounts")
public class TrustAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "account_name", nullable = false, length = 200)
  private String accountName;

  @Column(name = "bank_name", nullable = false, length = 200)
  private String bankName;

  @Column(name = "branch_code", nullable = false, length = 20)
  private String branchCode;

  @Column(name = "account_number", nullable = false, length = 30)
  private String accountNumber;

  @Column(name = "account_type", nullable = false, length = 20)
  private String accountType;

  @Column(name = "is_primary", nullable = false)
  private boolean isPrimary;

  @Column(name = "require_dual_approval", nullable = false)
  private boolean requireDualApproval;

  @Column(name = "payment_approval_threshold", precision = 15, scale = 2)
  private BigDecimal paymentApprovalThreshold;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "opened_date", nullable = false)
  private LocalDate openedDate;

  @Column(name = "closed_date")
  private LocalDate closedDate;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected TrustAccount() {}

  public TrustAccount(
      String accountName,
      String bankName,
      String branchCode,
      String accountNumber,
      String accountType,
      boolean isPrimary,
      boolean requireDualApproval,
      BigDecimal paymentApprovalThreshold,
      LocalDate openedDate,
      String notes) {
    this.accountName = accountName;
    this.bankName = bankName;
    this.branchCode = branchCode;
    this.accountNumber = accountNumber;
    this.accountType = accountType;
    this.isPrimary = isPrimary;
    this.requireDualApproval = requireDualApproval;
    this.paymentApprovalThreshold = paymentApprovalThreshold;
    this.status = "ACTIVE";
    this.openedDate = openedDate;
    this.notes = notes;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String accountName) {
    this.accountName = accountName;
  }

  public String getBankName() {
    return bankName;
  }

  public void setBankName(String bankName) {
    this.bankName = bankName;
  }

  public String getBranchCode() {
    return branchCode;
  }

  public void setBranchCode(String branchCode) {
    this.branchCode = branchCode;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public void setAccountNumber(String accountNumber) {
    this.accountNumber = accountNumber;
  }

  public String getAccountType() {
    return accountType;
  }

  public boolean getIsPrimary() {
    return isPrimary;
  }

  public void setIsPrimary(boolean isPrimary) {
    this.isPrimary = isPrimary;
  }

  public boolean getRequireDualApproval() {
    return requireDualApproval;
  }

  public void setRequireDualApproval(boolean requireDualApproval) {
    this.requireDualApproval = requireDualApproval;
  }

  public BigDecimal getPaymentApprovalThreshold() {
    return paymentApprovalThreshold;
  }

  public void setPaymentApprovalThreshold(BigDecimal paymentApprovalThreshold) {
    this.paymentApprovalThreshold = paymentApprovalThreshold;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public LocalDate getOpenedDate() {
    return openedDate;
  }

  public LocalDate getClosedDate() {
    return closedDate;
  }

  public void setClosedDate(LocalDate closedDate) {
    this.closedDate = closedDate;
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

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
