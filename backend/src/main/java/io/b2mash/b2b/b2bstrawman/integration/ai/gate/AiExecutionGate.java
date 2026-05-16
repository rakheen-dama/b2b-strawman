package io.b2mash.b2b.b2bstrawman.integration.ai.gate;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_execution_gates")
public class AiExecutionGate {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "execution_id", nullable = false)
  private AiExecution execution;

  @Column(name = "gate_type", nullable = false, length = 40)
  private String gateType;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "proposed_action", columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> proposedAction;

  @Column(name = "ai_reasoning", nullable = false, columnDefinition = "TEXT")
  private String aiReasoning;

  @Column(name = "reviewed_by")
  private UUID reviewedBy;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "review_notes", columnDefinition = "TEXT")
  private String reviewNotes;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected AiExecutionGate() {}

  public AiExecutionGate(
      AiExecution execution,
      String gateType,
      Map<String, Object> proposedAction,
      String aiReasoning,
      Instant expiresAt) {
    this.execution = execution;
    this.gateType = gateType;
    this.proposedAction = proposedAction;
    this.aiReasoning = aiReasoning;
    this.expiresAt = expiresAt;
    this.status = "PENDING";
    this.createdAt = Instant.now();
  }

  public void requirePendingStatus() {
    if (!"PENDING".equals(this.status)) {
      throw new InvalidStateException(
          "Invalid gate status", "Gate must be PENDING but was " + this.status);
    }
  }

  public void approve(UUID reviewerId, String notes) {
    requirePendingStatus();
    this.status = "APPROVED";
    this.reviewedBy = reviewerId;
    this.reviewedAt = Instant.now();
    this.reviewNotes = notes;
  }

  public void reject(UUID reviewerId, String notes) {
    requirePendingStatus();
    this.status = "REJECTED";
    this.reviewedBy = reviewerId;
    this.reviewedAt = Instant.now();
    this.reviewNotes = notes;
  }

  public void expire() {
    requirePendingStatus();
    this.status = "EXPIRED";
  }

  // ── Getters ───────────────────────────────────────────────────────────────

  public UUID getId() {
    return id;
  }

  public AiExecution getExecution() {
    return execution;
  }

  public String getGateType() {
    return gateType;
  }

  public String getStatus() {
    return status;
  }

  public Map<String, Object> getProposedAction() {
    return proposedAction;
  }

  public String getAiReasoning() {
    return aiReasoning;
  }

  public UUID getReviewedBy() {
    return reviewedBy;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public String getReviewNotes() {
    return reviewNotes;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
