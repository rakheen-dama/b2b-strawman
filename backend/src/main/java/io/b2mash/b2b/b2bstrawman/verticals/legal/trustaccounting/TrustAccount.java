package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

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

  @Enumerated(EnumType.STRING)
  @Column(name = "account_type", nullable = false, length = 20)
  private TrustAccountType accountType;

  /** ADR-379: non-null exactly on INVESTMENT accounts (chk_trust_account_investment_basis). */
  @Enumerated(EnumType.STRING)
  @Column(name = "investment_basis", length = 20)
  private InvestmentBasis investmentBasis;

  @Column(name = "is_primary", nullable = false)
  private boolean primary;

  @Column(name = "require_dual_approval", nullable = false)
  private boolean requireDualApproval;

  @Column(name = "payment_approval_threshold", precision = 15, scale = 2)
  private BigDecimal paymentApprovalThreshold;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private TrustAccountStatus status;

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

  /** A non-investment account (GENERAL, SECTION_86): no investment basis. */
  public TrustAccount(
      String accountName,
      String bankName,
      String branchCode,
      String accountNumber,
      TrustAccountType accountType,
      boolean primary,
      boolean requireDualApproval,
      BigDecimal paymentApprovalThreshold,
      LocalDate openedDate,
      String notes) {
    this(
        accountName,
        bankName,
        branchCode,
        accountNumber,
        accountType,
        primary,
        requireDualApproval,
        paymentApprovalThreshold,
        openedDate,
        notes,
        null);
  }

  /** An account with its investment basis — required for INVESTMENT, null for any other type. */
  public TrustAccount(
      String accountName,
      String bankName,
      String branchCode,
      String accountNumber,
      TrustAccountType accountType,
      boolean primary,
      boolean requireDualApproval,
      BigDecimal paymentApprovalThreshold,
      LocalDate openedDate,
      String notes,
      InvestmentBasis investmentBasis) {
    this.accountName = accountName;
    this.bankName = bankName;
    this.branchCode = branchCode;
    this.accountNumber = accountNumber;
    this.accountType = accountType;
    this.primary = primary;
    this.requireDualApproval = requireDualApproval;
    this.paymentApprovalThreshold = paymentApprovalThreshold;
    this.status = TrustAccountStatus.ACTIVE;
    this.openedDate = openedDate;
    this.notes = notes;
    this.investmentBasis = investmentBasis;
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

  public TrustAccountType getAccountType() {
    return accountType;
  }

  public InvestmentBasis getInvestmentBasis() {
    return investmentBasis;
  }

  public void setInvestmentBasis(InvestmentBasis investmentBasis) {
    this.investmentBasis = investmentBasis;
  }

  /** The section the account reports under (derived; OL-1 lives in {@link TrustAccountSection}). */
  public TrustAccountSection getSection() {
    return TrustAccountSection.of(accountType, investmentBasis);
  }

  public boolean isPrimary() {
    return primary;
  }

  public void setPrimary(boolean primary) {
    this.primary = primary;
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

  public TrustAccountStatus getStatus() {
    return status;
  }

  public void setStatus(TrustAccountStatus status) {
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
}
