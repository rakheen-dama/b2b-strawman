package io.b2mash.b2b.b2bstrawman.integration.ai.skill;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiCompletionRequest;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiCompletionResponse;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiImageInput;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiProvider;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiVisionRequest;
import io.b2mash.b2b.b2bstrawman.integration.ai.cost.AiCostService;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecutionRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGate;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGateRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfile;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfileService;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiSkillExecutionService {

  private static final Logger log = LoggerFactory.getLogger(AiSkillExecutionService.class);

  private final AiFirmProfileService firmProfileService;
  private final AiCostService costService;
  private final IntegrationRegistry integrationRegistry;
  private final Map<String, AiSkill> skillMap;
  private final AiExecutionRepository executionRepository;
  private final AiExecutionGateRepository gateRepository;
  private final NotificationService notificationService;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;

  public AiSkillExecutionService(
      AiFirmProfileService firmProfileService,
      AiCostService costService,
      IntegrationRegistry integrationRegistry,
      List<AiSkill> skills,
      AiExecutionRepository executionRepository,
      AiExecutionGateRepository gateRepository,
      NotificationService notificationService,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher) {
    this.firmProfileService = firmProfileService;
    this.costService = costService;
    this.integrationRegistry = integrationRegistry;
    this.skillMap = skills.stream().collect(Collectors.toMap(AiSkill::skillId, s -> s));
    this.executionRepository = executionRepository;
    this.gateRepository = gateRepository;
    this.notificationService = notificationService;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
  }

  /**
   * Resolve a skill by ID and execute it. Throws ResourceNotFoundException if the skill ID is
   * unknown.
   */
  @Transactional
  public SkillExecutionResult executeSkill(
      String skillId, SkillContext context, UUID invokedBy, List<AiImageInput> images) {
    AiSkill skill = skillMap.get(skillId);
    if (skill == null) {
      throw new ResourceNotFoundException("AiSkill", skillId);
    }
    return executeSkill(new SkillExecutionRequest(skill, context, invokedBy, images));
  }

  @Transactional
  public SkillExecutionResult executeSkill(SkillExecutionRequest request) {
    // 1. Pre-flight: load firm profile, check budget, resolve AiProvider
    AiFirmProfile profile = firmProfileService.getOrCreateProfile();
    costService.checkBudget(profile);
    AiProvider provider = resolveProvider();

    // 2. Create execution record (IN_PROGRESS)
    var execution =
        new AiExecution(
            request.skill().skillId(),
            request.context().entityType(),
            request.context().entityId(),
            request.invokedBy(),
            profile.getPreferredModel(),
            profile.getProfileVersion());
    execution.setInputSummary(request.context().description());
    execution = executionRepository.save(execution);

    // 3. Validate vision requirement
    if (request.skill().requiresVision() && !request.hasImages()) {
      throw new InvalidStateException(
          "Skill requires images",
          "Skill '" + request.skill().skillId() + "' requires vision input but no images provided");
    }

    // 4. Assemble prompts and invoke AI provider
    AiCompletionResponse response;
    long startMs = System.currentTimeMillis();
    try {
      String systemPrompt = request.skill().assembleSystemPrompt(profile);
      String userPrompt = request.skill().assembleUserPrompt(request.context());

      if (request.skill().requiresVision() && request.hasImages()) {
        response =
            provider.completeWithVision(
                new AiVisionRequest(
                    systemPrompt,
                    userPrompt,
                    profile.getPreferredModel(),
                    4096,
                    0.2,
                    Map.of("skill-id", request.skill().skillId()),
                    request.images()));
      } else {
        response =
            provider.complete(
                new AiCompletionRequest(
                    systemPrompt,
                    userPrompt,
                    profile.getPreferredModel(),
                    4096,
                    0.2,
                    Map.of("skill-id", request.skill().skillId())));
      }
    } catch (Exception e) {
      // On exception: record failed execution
      long durationMs = System.currentTimeMillis() - startMs;
      execution.markFailed(e.getMessage(), durationMs);
      execution = executionRepository.save(execution);
      log.warn(
          "AI skill {} failed for entity {}: {}",
          request.skill().skillId(),
          request.context().entityId(),
          e.getMessage());
      emitAuditEvent(execution);
      eventPublisher.publishEvent(toEvent(execution));
      return new SkillExecutionResult(execution, List.of());
    }

    // 5. On success: calculate cost, mark execution completed
    long costCents = costService.calculateCostCents(response);
    execution.markCompleted(response, costCents);
    execution = executionRepository.save(execution);

    // 6. Parse output and create gates via skill interface
    List<AiExecutionGate> gates =
        request.skill().createGates(execution, response.content(), request.context());
    if (!gates.isEmpty()) {
      gateRepository.saveAll(gates);
    }

    // 7. Send notifications for pending gates
    for (AiExecutionGate gate : gates) {
      sendGateNotification(gate, request.invokedBy());
    }

    // 8. Emit audit event
    emitAuditEvent(execution);
    eventPublisher.publishEvent(toEvent(execution));

    return new SkillExecutionResult(execution, gates);
  }

  /**
   * Resolve the AI provider for the current tenant. Uses the IntegrationRegistry which returns the
   * configured provider (e.g. AnthropicAiProvider) or NoOpAiProvider when no integration is set up.
   */
  private AiProvider resolveProvider() {
    return integrationRegistry.resolve(IntegrationDomain.AI, AiProvider.class);
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

  private void emitAuditEvent(AiExecution execution) {
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
