package io.b2mash.b2b.b2bstrawman.integration.ai.execution;

import io.b2mash.b2b.b2bstrawman.integration.ai.AiCompletionResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_executions")
public class AiExecution {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "skill_id", nullable = false, length = 40)
  private String skillId;

  @Column(name = "entity_type", nullable = false, length = 30)
  private String entityType;

  @Column(name = "entity_id", nullable = false)
  private UUID entityId;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "input_summary")
  private String inputSummary;

  @Column(name = "output_content", columnDefinition = "TEXT")
  private String outputContent;

  @Column(name = "model", nullable = false, length = 40)
  private String model;

  @Column(name = "input_tokens", nullable = false)
  private int inputTokens;

  @Column(name = "output_tokens", nullable = false)
  private int outputTokens;

  @Column(name = "cache_read_input_tokens", nullable = false)
  private int cacheReadInputTokens;

  @Column(name = "cache_creation_input_tokens", nullable = false)
  private int cacheCreationInputTokens;

  @Column(name = "cost_cents", nullable = false)
  private long costCents;

  @Column(name = "duration_ms")
  private Long durationMs;

  // Nullable since V133 (Phase 83): system-invoked skills (collections_scan / cash_digest job
  // context) record no member — a null invokedBy means "system". V133 dropped the column's NOT
  // NULL; this annotation must match or Hibernate's flush-time nullability check rejects the
  // system invocation with a PropertyValueException.
  @Column(name = "invoked_by")
  private UUID invokedBy;

  @Column(name = "firm_profile_version")
  private Integer firmProfileVersion;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected AiExecution() {}

  public AiExecution(
      String skillId,
      String entityType,
      UUID entityId,
      UUID invokedBy,
      String model,
      Integer firmProfileVersion) {
    this.skillId = skillId;
    this.entityType = entityType;
    this.entityId = entityId;
    this.invokedBy = invokedBy;
    this.model = model;
    this.firmProfileVersion = firmProfileVersion;
    this.status = "IN_PROGRESS";
    this.createdAt = Instant.now();
  }

  /**
   * Builds a synthetic, zero-cost {@link AiExecution} for a Bring-Your-Own-Claude (BYOC) MCP
   * proposal (Epic 585, ADR-322). Kazi made no provider call — the reasoning happened entirely in
   * the firm's own Claude over the MCP server — so there is no model invocation, no tokens and no
   * cost to meter. This synthetic row preserves the {@code execution_id NOT NULL} foreign key on
   * {@link io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGate} (every gate must point at
   * an execution) while a {@code cost_cents = 0} row is the cost-model signal that the work was
   * done externally. The "provider = MCP / source = BYOC" semantics from the ADR are encoded into
   * the existing columns: {@code skill_id = "mcp_propose_task"}, {@code model = "byoc"} (the column
   * is NOT NULL so it cannot be left blank), with the terminal {@code EXTERNALLY_EXECUTED} status.
   * All token/cost fields stay at their primitive {@code 0} defaults.
   */
  public static AiExecution syntheticMcpProposal(UUID memberId, UUID entityId) {
    var execution =
        new AiExecution("mcp_propose_task", "correspondence", entityId, memberId, "byoc", null);
    execution.status = "EXTERNALLY_EXECUTED";
    return execution;
  }

  public void markCompleted(AiCompletionResponse response, long costCents) {
    this.status = "COMPLETED";
    this.outputContent = response.content();
    this.inputTokens = response.inputTokens();
    this.outputTokens = response.outputTokens();
    this.cacheReadInputTokens = response.cacheReadInputTokens();
    this.cacheCreationInputTokens = response.cacheCreationInputTokens();
    this.durationMs = response.durationMs();
    this.costCents = costCents;
  }

  public void markFailed(String errorMessage, long durationMs) {
    this.status = "FAILED";
    this.errorMessage = errorMessage;
    this.durationMs = durationMs;
  }

  /**
   * Transition a previously-COMPLETED execution to FAILED while preserving the cost, token usage
   * and duration already recorded by {@link #markCompleted}. Used when the LLM call succeeded (and
   * was billed) but its output could not be parsed — the real spend must stay metered rather than
   * being rolled back, distinguishing "cost incurred, parse failed" from a provider-level failure.
   */
  public void markFailedAfterCompletion(String errorMessage) {
    this.status = "FAILED";
    this.errorMessage = errorMessage;
    // costCents, inputTokens, outputTokens, cache tokens, outputContent and durationMs are left
    // intact — they were set by markCompleted and represent real, already-incurred spend.
  }

  public void setInputSummary(String inputSummary) {
    this.inputSummary = inputSummary;
  }

  // ── Getters ───────────────────────────────────────────────────────────────

  public UUID getId() {
    return id;
  }

  public String getSkillId() {
    return skillId;
  }

  public String getEntityType() {
    return entityType;
  }

  public UUID getEntityId() {
    return entityId;
  }

  public String getStatus() {
    return status;
  }

  public String getInputSummary() {
    return inputSummary;
  }

  public String getOutputContent() {
    return outputContent;
  }

  public String getModel() {
    return model;
  }

  public int getInputTokens() {
    return inputTokens;
  }

  public int getOutputTokens() {
    return outputTokens;
  }

  public int getCacheReadInputTokens() {
    return cacheReadInputTokens;
  }

  public int getCacheCreationInputTokens() {
    return cacheCreationInputTokens;
  }

  public long getCostCents() {
    return costCents;
  }

  public Long getDurationMs() {
    return durationMs;
  }

  public UUID getInvokedBy() {
    return invokedBy;
  }

  public Integer getFirmProfileVersion() {
    return firmProfileVersion;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
