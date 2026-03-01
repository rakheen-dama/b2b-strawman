package io.b2mash.b2b.b2bstrawman.proposal;

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
import java.util.Objects;
import java.util.UUID;

/** A milestone in a fixed-fee proposal's billing schedule. */
@Entity
@Table(name = "proposal_milestones")
public class ProposalMilestone {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "proposal_id", nullable = false)
  private UUID proposalId;

  @Column(name = "description", nullable = false, length = 200)
  private String description;

  @Column(name = "percentage", nullable = false, precision = 5, scale = 2)
  private BigDecimal percentage;

  @Column(name = "relative_due_days", nullable = false)
  private int relativeDueDays;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "invoice_id")
  private UUID invoiceId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** JPA-required no-arg constructor. */
  protected ProposalMilestone() {}

  public ProposalMilestone(
      UUID proposalId,
      String description,
      BigDecimal percentage,
      int relativeDueDays,
      int sortOrder) {
    this.proposalId = Objects.requireNonNull(proposalId, "proposalId must not be null");
    this.description = Objects.requireNonNull(description, "description must not be null");
    this.percentage = Objects.requireNonNull(percentage, "percentage must not be null");
    this.relativeDueDays = relativeDueDays;
    this.sortOrder = sortOrder;
  }

  @PrePersist
  void onPrePersist() {
    var now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onPreUpdate() {
    this.updatedAt = Instant.now();
  }

  // --- Getters ---

  public UUID getId() {
    return id;
  }

  public UUID getProposalId() {
    return proposalId;
  }

  public String getDescription() {
    return description;
  }

  public BigDecimal getPercentage() {
    return percentage;
  }

  public int getRelativeDueDays() {
    return relativeDueDays;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  // --- Setters ---

  public void setDescription(String description) {
    this.description = Objects.requireNonNull(description, "description must not be null");
  }

  public void setPercentage(BigDecimal percentage) {
    this.percentage = Objects.requireNonNull(percentage, "percentage must not be null");
  }

  public void setRelativeDueDays(int relativeDueDays) {
    this.relativeDueDays = relativeDueDays;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }

  public void setInvoiceId(UUID invoiceId) {
    this.invoiceId = invoiceId;
  }
}
