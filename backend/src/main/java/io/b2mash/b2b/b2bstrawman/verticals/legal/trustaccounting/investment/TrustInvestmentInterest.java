package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One section 86(4) interest credit on an investment (Phase 92 648B, ADR-379, architecture
 * §16.2.8): the gross, the Legal Practitioners' Fidelity Fund's 5% and the client's 95%, the date
 * the bank credited it and the client's tax reference where held — the per-investment record a firm
 * needs for IT3(b) (A3; Kazi does not file). The rows it posted are {@link #getNetTransactionId()}
 * ({@code INV_INTEREST}) and, for {@link InterestPresentation#GROSS}, {@link
 * #getFundShareTransactionId()} ({@code FUND_INV_SHARE}) — {@code chk_invest_interest_fund_txn}.
 * The 5% is a service rule, not a CHECK (OL-5). {@code POSTED} until the entry is reversed (its
 * rows reversed by contra) — then {@code REVERSED}; a row is never deleted.
 */
@Entity
@Table(name = "trust_investment_interest")
public class TrustInvestmentInterest {

  public static final String POSTED = "POSTED";
  public static final String REVERSED = "REVERSED";

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "investment_id", nullable = false)
  private UUID investmentId;

  @Column(name = "entry_date", nullable = false)
  private LocalDate entryDate;

  @Column(name = "gross_amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal grossAmount;

  @Column(name = "fund_share_amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal fundShareAmount;

  @Column(name = "net_amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal netAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "presentation", nullable = false, length = 10)
  private InterestPresentation presentation;

  @Column(name = "net_transaction_id", nullable = false)
  private UUID netTransactionId;

  @Column(name = "fund_share_transaction_id")
  private UUID fundShareTransactionId;

  /** The statement line it was recorded from; null for a bank advice. */
  @Column(name = "bank_statement_line_id")
  private UUID bankStatementLineId;

  /** A snapshot of {@code customers.tax_number} at entry — a later edit never rewrites it. */
  @Column(name = "client_tax_reference", length = 100)
  private String clientTaxReference;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "recorded_by", nullable = false)
  private UUID recordedBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected TrustInvestmentInterest() {}

  public TrustInvestmentInterest(
      UUID investmentId,
      LocalDate entryDate,
      BigDecimal grossAmount,
      BigDecimal fundShareAmount,
      BigDecimal netAmount,
      InterestPresentation presentation,
      UUID netTransactionId,
      UUID fundShareTransactionId,
      UUID bankStatementLineId,
      String clientTaxReference,
      UUID recordedBy) {
    this.investmentId = investmentId;
    this.entryDate = entryDate;
    this.grossAmount = grossAmount;
    this.fundShareAmount = fundShareAmount;
    this.netAmount = netAmount;
    this.presentation = presentation;
    this.netTransactionId = netTransactionId;
    this.fundShareTransactionId = fundShareTransactionId;
    this.bankStatementLineId = bankStatementLineId;
    this.clientTaxReference = clientTaxReference;
    this.recordedBy = recordedBy;
    this.status = POSTED;
  }

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
  }

  /** Its rows have been reversed by contra (bank advice: its own screen; a line: by unmatch). */
  public void markReversed() {
    this.status = REVERSED;
  }

  public boolean isPosted() {
    return POSTED.equals(status);
  }

  public UUID getId() {
    return id;
  }

  public UUID getInvestmentId() {
    return investmentId;
  }

  public LocalDate getEntryDate() {
    return entryDate;
  }

  public BigDecimal getGrossAmount() {
    return grossAmount;
  }

  public BigDecimal getFundShareAmount() {
    return fundShareAmount;
  }

  public BigDecimal getNetAmount() {
    return netAmount;
  }

  public InterestPresentation getPresentation() {
    return presentation;
  }

  public UUID getNetTransactionId() {
    return netTransactionId;
  }

  public UUID getFundShareTransactionId() {
    return fundShareTransactionId;
  }

  public UUID getBankStatementLineId() {
    return bankStatementLineId;
  }

  public String getClientTaxReference() {
    return clientTaxReference;
  }

  public String getStatus() {
    return status;
  }

  public UUID getRecordedBy() {
    return recordedBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
