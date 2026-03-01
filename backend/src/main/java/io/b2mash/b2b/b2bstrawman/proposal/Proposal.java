package io.b2mash.b2b.b2bstrawman.proposal;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A proposal sent to a customer for engagement (ADR-124).
 *
 * <p>Lifecycle: DRAFT → SENT → ACCEPTED | DECLINED | EXPIRED. Only DRAFT proposals are editable.
 * Content is stored as Tiptap JSON in a JSONB column.
 */
@Entity
@Table(name = "proposals")
public class Proposal {

  private static final Set<ProposalStatus> TERMINAL_STATUSES =
      Set.of(ProposalStatus.ACCEPTED, ProposalStatus.DECLINED, ProposalStatus.EXPIRED);

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "proposal_number", nullable = false, length = 20)
  private String proposalNumber;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "portal_contact_id")
  private UUID portalContactId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ProposalStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "fee_model", nullable = false, length = 20)
  private FeeModel feeModel;

  // --- Fee configuration (varies by feeModel) ---

  @Column(name = "fixed_fee_amount", precision = 12, scale = 2)
  private BigDecimal fixedFeeAmount;

  @Column(name = "fixed_fee_currency", length = 3)
  private String fixedFeeCurrency;

  @Column(name = "hourly_rate_note", length = 500)
  private String hourlyRateNote;

  @Column(name = "retainer_amount", precision = 12, scale = 2)
  private BigDecimal retainerAmount;

  @Column(name = "retainer_currency", length = 3)
  private String retainerCurrency;

  @Column(name = "retainer_hours_included", precision = 6, scale = 1)
  private BigDecimal retainerHoursIncluded;

  // --- Document content (Tiptap JSON) ---

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "content_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> contentJson = Map.of();

  // --- Orchestration references ---

  @Column(name = "project_template_id")
  private UUID projectTemplateId;

  // --- Lifecycle timestamps ---

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @Column(name = "declined_at")
  private Instant declinedAt;

  @Column(name = "decline_reason", length = 500)
  private String declineReason;

  // --- Result references (set after acceptance orchestration) ---

  @Column(name = "created_project_id")
  private UUID createdProjectId;

  @Column(name = "created_retainer_id")
  private UUID createdRetainerId;

  // --- Metadata ---

  @Column(name = "created_by_id", nullable = false)
  private UUID createdById;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** JPA-required no-arg constructor. */
  protected Proposal() {}

  public Proposal(
      String proposalNumber, String title, UUID customerId, FeeModel feeModel, UUID createdById) {
    this.proposalNumber = Objects.requireNonNull(proposalNumber, "proposalNumber must not be null");
    this.title = Objects.requireNonNull(title, "title must not be null");
    this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
    this.feeModel = Objects.requireNonNull(feeModel, "feeModel must not be null");
    this.createdById = Objects.requireNonNull(createdById, "createdById must not be null");
    this.status = ProposalStatus.DRAFT;
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

  // --- Lifecycle methods ---

  /** Marks the proposal as sent to a portal contact. Only valid from DRAFT. */
  public void markSent(UUID portalContactId) {
    requireStatus(Set.of(ProposalStatus.DRAFT), "mark as sent");
    this.portalContactId =
        Objects.requireNonNull(portalContactId, "portalContactId must not be null");
    this.status = ProposalStatus.SENT;
    this.sentAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /** Marks the proposal as accepted by the customer. Only valid from SENT. */
  public void markAccepted() {
    requireStatus(Set.of(ProposalStatus.SENT), "mark as accepted");
    this.status = ProposalStatus.ACCEPTED;
    this.acceptedAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /** Marks the proposal as declined by the customer. Only valid from SENT. */
  public void markDeclined(String reason) {
    requireStatus(Set.of(ProposalStatus.SENT), "mark as declined");
    this.status = ProposalStatus.DECLINED;
    this.declineReason = reason;
    this.declinedAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /** Marks the proposal as expired (past its expiresAt deadline). Only valid from SENT. */
  public void markExpired() {
    requireStatus(Set.of(ProposalStatus.SENT), "mark as expired");
    this.status = ProposalStatus.EXPIRED;
    this.updatedAt = Instant.now();
  }

  // --- Guards ---

  /** Returns true if the proposal is in DRAFT status and can be edited. */
  public boolean isEditable() {
    return this.status == ProposalStatus.DRAFT;
  }

  /** Returns true if the proposal is in a terminal status (ACCEPTED, DECLINED, EXPIRED). */
  public boolean isTerminal() {
    return TERMINAL_STATUSES.contains(this.status);
  }

  /** Throws InvalidStateException if the proposal is not in DRAFT status. */
  public void requireEditable() {
    if (!isEditable()) {
      throw new InvalidStateException(
          "Invalid proposal state", "Cannot edit proposal in status " + this.status);
    }
  }

  // --- Guarded setters for mutable fields ---

  public void setTitle(String title) {
    requireEditable();
    this.title = Objects.requireNonNull(title, "title must not be null");
    this.updatedAt = Instant.now();
  }

  public void setContentJson(Map<String, Object> contentJson) {
    requireEditable();
    this.contentJson = Objects.requireNonNull(contentJson, "contentJson must not be null");
    this.updatedAt = Instant.now();
  }

  public void setFeeModel(FeeModel feeModel) {
    requireEditable();
    this.feeModel = Objects.requireNonNull(feeModel, "feeModel must not be null");
    this.updatedAt = Instant.now();
  }

  public void setFixedFeeAmount(BigDecimal fixedFeeAmount) {
    requireEditable();
    this.fixedFeeAmount = fixedFeeAmount;
    this.updatedAt = Instant.now();
  }

  public void setFixedFeeCurrency(String fixedFeeCurrency) {
    requireEditable();
    this.fixedFeeCurrency = fixedFeeCurrency;
    this.updatedAt = Instant.now();
  }

  public void setHourlyRateNote(String hourlyRateNote) {
    requireEditable();
    this.hourlyRateNote = hourlyRateNote;
    this.updatedAt = Instant.now();
  }

  public void setRetainerAmount(BigDecimal retainerAmount) {
    requireEditable();
    this.retainerAmount = retainerAmount;
    this.updatedAt = Instant.now();
  }

  public void setRetainerCurrency(String retainerCurrency) {
    requireEditable();
    this.retainerCurrency = retainerCurrency;
    this.updatedAt = Instant.now();
  }

  public void setRetainerHoursIncluded(BigDecimal retainerHoursIncluded) {
    requireEditable();
    this.retainerHoursIncluded = retainerHoursIncluded;
    this.updatedAt = Instant.now();
  }

  public void setProjectTemplateId(UUID projectTemplateId) {
    requireEditable();
    this.projectTemplateId = projectTemplateId;
    this.updatedAt = Instant.now();
  }

  public void setExpiresAt(Instant expiresAt) {
    requireEditable();
    this.expiresAt = expiresAt;
    this.updatedAt = Instant.now();
  }

  public void setPortalContactId(UUID portalContactId) {
    requireEditable();
    this.portalContactId = portalContactId;
    this.updatedAt = Instant.now();
  }

  /** Sets the project created from acceptance orchestration. Not guarded — set post-acceptance. */
  public void setCreatedProjectId(UUID createdProjectId) {
    this.createdProjectId = createdProjectId;
    this.updatedAt = Instant.now();
  }

  /** Sets the retainer created from acceptance orchestration. Not guarded — set post-acceptance. */
  public void setCreatedRetainerId(UUID createdRetainerId) {
    this.createdRetainerId = createdRetainerId;
    this.updatedAt = Instant.now();
  }

  // --- Getters ---

  public UUID getId() {
    return id;
  }

  public String getProposalNumber() {
    return proposalNumber;
  }

  public String getTitle() {
    return title;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public UUID getPortalContactId() {
    return portalContactId;
  }

  public ProposalStatus getStatus() {
    return status;
  }

  public FeeModel getFeeModel() {
    return feeModel;
  }

  public BigDecimal getFixedFeeAmount() {
    return fixedFeeAmount;
  }

  public String getFixedFeeCurrency() {
    return fixedFeeCurrency;
  }

  public String getHourlyRateNote() {
    return hourlyRateNote;
  }

  public BigDecimal getRetainerAmount() {
    return retainerAmount;
  }

  public String getRetainerCurrency() {
    return retainerCurrency;
  }

  public BigDecimal getRetainerHoursIncluded() {
    return retainerHoursIncluded;
  }

  public Map<String, Object> getContentJson() {
    return contentJson;
  }

  public UUID getProjectTemplateId() {
    return projectTemplateId;
  }

  public Instant getSentAt() {
    return sentAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getAcceptedAt() {
    return acceptedAt;
  }

  public Instant getDeclinedAt() {
    return declinedAt;
  }

  public String getDeclineReason() {
    return declineReason;
  }

  public UUID getCreatedProjectId() {
    return createdProjectId;
  }

  public UUID getCreatedRetainerId() {
    return createdRetainerId;
  }

  public UUID getCreatedById() {
    return createdById;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  // --- Private helpers ---

  private void requireStatus(Set<ProposalStatus> allowedStatuses, String action) {
    if (!allowedStatuses.contains(this.status)) {
      throw new InvalidStateException(
          "Invalid proposal state", "Cannot " + action + " proposal in status " + this.status);
    }
  }
}
