package io.b2mash.b2b.b2bstrawman.assistant.invocation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-Anthropic-call telemetry row, child of {@link AiSpecialistInvocation}. Write-once at
 * runner-step completion (515C); read-only from app code thereafter.
 */
@Entity
@Table(name = "ai_llm_calls")
public class AiLlmCall {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "invocation_id", nullable = false)
  private UUID invocationId;

  @Column(name = "model", nullable = false, length = 80)
  private String model;

  @Column(name = "prompt_version", length = 40)
  private String promptVersion;

  @Column(name = "input_tokens", nullable = false)
  private int inputTokens;

  @Column(name = "output_tokens", nullable = false)
  private int outputTokens;

  @Column(name = "cache_read_input_tokens", nullable = false)
  private int cacheReadInputTokens;

  @Column(name = "cache_creation_input_tokens", nullable = false)
  private int cacheCreationInputTokens;

  @Column(name = "request_id", length = 100)
  private String requestId;

  @Column(name = "stop_reason", length = 40)
  private String stopReason;

  @Column(name = "latency_ms")
  private Integer latencyMs;

  @Column(name = "was_vision", nullable = false)
  private boolean wasVision;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected AiLlmCall() {}

  public AiLlmCall(
      UUID invocationId,
      String model,
      String promptVersion,
      int inputTokens,
      int outputTokens,
      int cacheReadInputTokens,
      int cacheCreationInputTokens,
      String requestId,
      String stopReason,
      Integer latencyMs,
      boolean wasVision) {
    this.invocationId = invocationId;
    this.model = model;
    this.promptVersion = promptVersion;
    this.inputTokens = inputTokens;
    this.outputTokens = outputTokens;
    this.cacheReadInputTokens = cacheReadInputTokens;
    this.cacheCreationInputTokens = cacheCreationInputTokens;
    this.requestId = requestId;
    this.stopReason = stopReason;
    this.latencyMs = latencyMs;
    this.wasVision = wasVision;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getInvocationId() {
    return invocationId;
  }

  public String getModel() {
    return model;
  }

  public String getPromptVersion() {
    return promptVersion;
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

  public String getRequestId() {
    return requestId;
  }

  public String getStopReason() {
    return stopReason;
  }

  public Integer getLatencyMs() {
    return latencyMs;
  }

  public boolean isWasVision() {
    return wasVision;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
