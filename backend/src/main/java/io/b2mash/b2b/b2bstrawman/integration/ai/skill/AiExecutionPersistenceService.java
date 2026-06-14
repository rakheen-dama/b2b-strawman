package io.b2mash.b2b.b2bstrawman.integration.ai.skill;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiCompletionResponse;
import io.b2mash.b2b.b2bstrawman.integration.ai.cost.AiCostService;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecutionRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGate;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGateRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfile;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the short, focused database transactions for an AI skill execution. Extracted as a separate
 * collaborator (rather than self-invoking transactional methods on {@link AiSkillExecutionService})
 * so the {@code @Transactional} proxy actually applies — and, critically, so the multi-second LLM
 * HTTP round-trip happens in {@link AiSkillExecutionService} <em>between</em> these transactions
 * and never holds a JDBC connection (AIVERIFY-002).
 *
 * <p>Each public method is its own short transaction:
 *
 * <ul>
 *   <li>{@link #startExecution} — persist the IN_PROGRESS row before the LLM call.
 *   <li>{@link #completeExecution} — after the LLM call: meter cost, persist results, parse output
 *       and build gates. A parse failure transitions the same row to FAILED <em>with cost
 *       preserved</em> instead of rolling back the metered spend (AIVERIFY-001).
 *   <li>{@link #failExecution} — record a provider-level failure (LLM call itself threw).
 * </ul>
 */
@Service
public class AiExecutionPersistenceService {

  private static final Logger log = LoggerFactory.getLogger(AiExecutionPersistenceService.class);

  private final AiCostService costService;
  private final AiExecutionRepository executionRepository;
  private final AiExecutionGateRepository gateRepository;
  private final NotificationService notificationService;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;

  public AiExecutionPersistenceService(
      AiCostService costService,
      AiExecutionRepository executionRepository,
      AiExecutionGateRepository gateRepository,
      NotificationService notificationService,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher) {
    this.costService = costService;
    this.executionRepository = executionRepository;
    this.gateRepository = gateRepository;
    this.notificationService = notificationService;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
  }

  /** Persist a new IN_PROGRESS execution row. Short write transaction, before the LLM call. */
  @Transactional
  public UUID startExecution(SkillExecutionRequest request, AiFirmProfile profile) {
    var execution =
        new AiExecution(
            request.skill().skillId(),
            request.context().entityType(),
            request.context().entityId(),
            request.invokedBy(),
            profile.getPreferredModel(),
            profile.getProfileVersion());
    execution.setInputSummary(request.context().description());
    return executionRepository.save(execution).getId();
  }

  /**
   * Assemble the skill's prompts inside a short read transaction. Prompt assembly performs
   * lazy-loading repository reads (e.g. tariff-schedule items) that require an open Hibernate
   * session, so it cannot run in the connection-free LLM phase. It is intentionally a read/write
   * transaction (not {@code readOnly}) because some skills take a pessimistic write lock during
   * assembly — the compliance-audit idempotency guard does {@code SELECT … FOR NO KEY UPDATE},
   * which Postgres forbids in a read-only transaction. May throw a runtime exception for
   * skill-specific pre-flight failures (e.g. description too short, customer not found); the
   * orchestrator translates that into a FAILED execution via {@link #failExecution}.
   *
   * <p>{@code noRollbackFor} the expected pre-flight exception types is deliberate: when this
   * method joins a caller's transaction (e.g. a test wrapping the whole flow) an expected
   * pre-flight failure must not mark that transaction rollback-only — the orchestrator still needs
   * to persist the FAILED execution afterward. Unexpected exceptions still roll back normally.
   */
  @Transactional(
      noRollbackFor = {
        InvalidStateException.class,
        ResourceNotFoundException.class,
        ResourceConflictException.class
      })
  public AssembledPrompts assemblePrompts(SkillExecutionRequest request, AiFirmProfile profile) {
    String systemPrompt = request.skill().assembleSystemPrompt(profile);
    String userPrompt = request.skill().assembleUserPrompt(request.context());
    return new AssembledPrompts(systemPrompt, userPrompt);
  }

  /** Carrier for {@link #assemblePrompts}: the assembled system/user prompts. */
  public record AssembledPrompts(String systemPrompt, String userPrompt) {}

  /**
   * After a successful LLM call: re-load the execution, meter cost, mark it COMPLETED, then parse
   * the output and create gates. If parsing fails, the same row is transitioned to FAILED with the
   * already-known cost/token usage preserved (never a full rollback), and {@code
   * ai.specialist.failed} is emitted. Short write transaction.
   */
  @Transactional
  public SkillExecutionResult completeExecution(
      UUID executionId, AiCompletionResponse response, SkillExecutionRequest request) {
    AiExecution execution = requireExecution(executionId);

    long costCents = costService.calculateCostCents(response);
    execution.markCompleted(response, costCents);
    execution = executionRepository.save(execution);

    // Parse output + build gates. ANY failure here must NOT roll back the metered execution — the
    // LLM was already billed. Record FAILED-with-cost and emit the failure audit event instead of
    // unwinding the transaction. We catch RuntimeException broadly because createGates does more
    // than parse the response: it can throw ResourceNotFoundException (a referenced entity was
    // deleted) or NPE (a null id) while resolving gate context. All of those mean "billed but no
    // gate produced", which is the FAILED-with-cost case — never a silent rollback.
    List<AiExecutionGate> gates;
    try {
      gates = request.skill().createGates(execution, response.content(), request.context());
    } catch (RuntimeException e) {
      execution.markFailedAfterCompletion(e.getMessage());
      execution = executionRepository.save(execution);
      log.warn(
          "AI skill {} failed to build gates for entity {} (cost {}c metered, FAILED): {}",
          execution.getSkillId(),
          execution.getEntityId(),
          execution.getCostCents(),
          e.getMessage());
      emitInvokedAuditEvent(execution);
      emitSpecialistFailedAuditEvent(execution, e.getMessage());
      eventPublisher.publishEvent(toEvent(execution));
      return new SkillExecutionResult(execution, List.of());
    }

    if (!gates.isEmpty()) {
      gateRepository.saveAll(gates);
    }
    for (AiExecutionGate gate : gates) {
      sendGateNotification(gate, request.invokedBy());
    }

    emitInvokedAuditEvent(execution);
    eventPublisher.publishEvent(toEvent(execution));
    return new SkillExecutionResult(execution, gates);
  }

  /** Record a provider-level failure (the LLM call itself threw). Short write transaction. */
  @Transactional
  public SkillExecutionResult failExecution(
      UUID executionId, String errorMessage, long durationMs) {
    AiExecution execution = requireExecution(executionId);
    execution.markFailed(errorMessage, durationMs);
    execution = executionRepository.save(execution);
    emitInvokedAuditEvent(execution);
    eventPublisher.publishEvent(toEvent(execution));
    return new SkillExecutionResult(execution, List.of());
  }

  private AiExecution requireExecution(UUID executionId) {
    return executionRepository
        .findById(executionId)
        .orElseThrow(() -> new ResourceNotFoundException("AiExecution", executionId));
  }

  private void sendGateNotification(AiExecutionGate gate, UUID invokedBy) {
    try {
      notificationService.createNotification(
          invokedBy,
          "ai.gate.pending",
          "AI verification requires your review",
          "Gate type: " + gate.getGateType() + " — " + gate.getAiReasoning(),
          "ai_execution_gate",
          gate.getId(),
          null);
    } catch (Exception e) {
      log.warn("Failed to send gate notification for gate {}: {}", gate.getId(), e.getMessage());
    }
  }

  private void emitInvokedAuditEvent(AiExecution execution) {
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("ai.skill.invoked")
            .entityType(execution.getEntityType())
            .entityId(execution.getEntityId())
            .details(
                Map.of(
                    "executionId", execution.getId().toString(),
                    "skillId", execution.getSkillId(),
                    "model", execution.getModel(),
                    "inputTokens", String.valueOf(execution.getInputTokens()),
                    "outputTokens", String.valueOf(execution.getOutputTokens()),
                    "costCents", String.valueOf(execution.getCostCents()),
                    "status", execution.getStatus()))
            .build());
  }

  private void emitSpecialistFailedAuditEvent(AiExecution execution, String errorMessage) {
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("ai.specialist.failed")
            .entityType(execution.getEntityType())
            .entityId(execution.getEntityId())
            .details(
                Map.of(
                    "executionId", execution.getId().toString(),
                    "skillId", execution.getSkillId(),
                    "model", execution.getModel(),
                    "costCents", String.valueOf(execution.getCostCents()),
                    "errorMessage", errorMessage == null ? "" : errorMessage))
            .build());
  }

  private AiSkillInvokedEvent toEvent(AiExecution execution) {
    return new AiSkillInvokedEvent(
        execution.getId(),
        execution.getSkillId(),
        execution.getEntityType(),
        execution.getEntityId(),
        execution.getModel(),
        execution.getInputTokens(),
        execution.getOutputTokens(),
        execution.getCostCents(),
        execution.getStatus());
  }
}
