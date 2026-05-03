package io.b2mash.b2b.b2bstrawman.assistant.invocation;

import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.OutputPayload;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Single-table representation of an AI specialist invocation, per ADR-270. JSONB payload columns
 * are typed via the sealed {@link OutputPayload} hierarchy with Jackson polymorphism.
 *
 * <p>Optimistic locking via {@link Version} guards against concurrent approve/reject/retry races —
 * Hibernate raises {@code ObjectOptimisticLockingFailureException} on stale writes; the service
 * layer rethrows as {@code ResourceConflictException} (→ 409).
 */
@Entity
@Table(name = "ai_specialist_invocations")
public class AiSpecialistInvocation {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "specialist_id", nullable = false, length = 40)
  private String specialistId;

  @Enumerated(EnumType.STRING)
  @Column(name = "invoked_by", nullable = false, length = 20)
  private InvocationSource invokedBy;

  @Column(name = "actor_id", nullable = false)
  private UUID actorId;

  @Column(name = "automation_action_execution_id")
  private UUID automationActionExecutionId;

  @Column(name = "context_entity_type", nullable = false, length = 50)
  private String contextEntityType;

  @Column(name = "context_entity_id", nullable = false)
  private UUID contextEntityId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private InvocationStatus status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "proposed_output", columnDefinition = "jsonb")
  private OutputPayload proposedOutput;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "applied_output", columnDefinition = "jsonb")
  private OutputPayload appliedOutput;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "reviewed_by_id")
  private UUID reviewedById;

  @Column(name = "reject_reason", columnDefinition = "text")
  private String rejectReason;

  @Column(name = "error_message", length = 2000)
  private String errorMessage;

  @Column(name = "prompt_version", length = 40)
  private String promptVersion;

  @Version
  @Column(name = "version", nullable = false)
  private int version;

  protected AiSpecialistInvocation() {}

  public AiSpecialistInvocation(
      String specialistId,
      InvocationSource invokedBy,
      UUID actorId,
      UUID automationActionExecutionId,
      String contextEntityType,
      UUID contextEntityId,
      String promptVersion) {
    this.specialistId = specialistId;
    this.invokedBy = invokedBy;
    this.actorId = actorId;
    this.automationActionExecutionId = automationActionExecutionId;
    this.contextEntityType = contextEntityType;
    this.contextEntityId = contextEntityId;
    this.promptVersion = promptVersion;
    this.status = InvocationStatus.RUNNING;
    this.createdAt = Instant.now();
  }

  public void requireStatus(InvocationStatus expected) {
    if (this.status != expected) {
      throw new InvalidStateException(
          "Invalid invocation status", "Expected status " + expected + " but was " + this.status);
    }
  }

  public void recordProposal(OutputPayload proposed) {
    requireStatus(InvocationStatus.RUNNING);
    this.proposedOutput = proposed;
  }

  public void markPendingApproval() {
    requireStatus(InvocationStatus.RUNNING);
    this.status = InvocationStatus.PENDING_APPROVAL;
  }

  public void markApproved(UUID memberId, OutputPayload edited) {
    requireStatus(InvocationStatus.PENDING_APPROVAL);
    this.status = InvocationStatus.APPROVED;
    this.reviewedAt = Instant.now();
    this.reviewedById = memberId;
    this.appliedOutput = edited;
  }

  public void markRejected(UUID memberId, String reason) {
    requireStatus(InvocationStatus.PENDING_APPROVAL);
    this.status = InvocationStatus.REJECTED;
    this.reviewedAt = Instant.now();
    this.reviewedById = memberId;
    this.rejectReason = reason;
  }

  public void markAutoApplied(OutputPayload payload) {
    requireStatus(InvocationStatus.RUNNING);
    this.status = InvocationStatus.AUTO_APPLIED;
    this.appliedOutput = payload;
    this.reviewedAt = Instant.now();
  }

  public void markFailed(String message) {
    requireStatus(InvocationStatus.RUNNING);
    this.status = InvocationStatus.FAILED;
    this.errorMessage = message;
  }

  public void markExpired() {
    requireStatus(InvocationStatus.PENDING_APPROVAL);
    this.status = InvocationStatus.EXPIRED;
  }

  /** FAILED → RUNNING reset for 515C re-enqueue. Clears the previous error message. */
  public void resetToRunning() {
    if (this.status != InvocationStatus.FAILED) {
      throw new InvalidStateException(
          "Invalid invocation status", "Retry only allowed from FAILED, but was " + this.status);
    }
    this.status = InvocationStatus.RUNNING;
    this.errorMessage = null;
    // Clear any stale outputs from the failed attempt so a retry starts clean and cannot
    // approve previously-generated proposals.
    this.proposedOutput = null;
    this.appliedOutput = null;
  }

  public UUID getId() {
    return id;
  }

  public String getSpecialistId() {
    return specialistId;
  }

  public InvocationSource getInvokedBy() {
    return invokedBy;
  }

  public UUID getActorId() {
    return actorId;
  }

  public UUID getAutomationActionExecutionId() {
    return automationActionExecutionId;
  }

  public String getContextEntityType() {
    return contextEntityType;
  }

  public UUID getContextEntityId() {
    return contextEntityId;
  }

  public InvocationStatus getStatus() {
    return status;
  }

  public OutputPayload getProposedOutput() {
    return proposedOutput;
  }

  public OutputPayload getAppliedOutput() {
    return appliedOutput;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public UUID getReviewedById() {
    return reviewedById;
  }

  public String getRejectReason() {
    return rejectReason;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public String getPromptVersion() {
    return promptVersion;
  }

  public int getVersion() {
    return version;
  }
}
