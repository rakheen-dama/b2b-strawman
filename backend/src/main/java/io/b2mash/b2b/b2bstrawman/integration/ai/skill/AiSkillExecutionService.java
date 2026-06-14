package io.b2mash.b2b.b2bstrawman.integration.ai.skill;

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
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfile;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfileService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates AI skill execution across three phases with the LLM HTTP call deliberately performed
 * <em>outside</em> any database transaction (AIVERIFY-002):
 *
 * <ol>
 *   <li>pre-flight + IN_PROGRESS persist + prompt assembly — short transactions in {@link
 *       AiExecutionPersistenceService};
 *   <li>the provider {@code complete(...)} / {@code completeWithVision(...)} call — no transaction,
 *       no JDBC connection held;
 *   <li>cost metering, COMPLETED persist, output parse + gate creation — a short transaction, with
 *       a parse failure recorded as FAILED-with-cost rather than rolling back the metered spend
 *       (AIVERIFY-001).
 * </ol>
 *
 * <p>This class is intentionally NOT {@code @Transactional}: holding one transaction across the
 * multi-second LLM call is exactly the connection-leak / catastrophic-rollback defect being fixed.
 */
@Service
public class AiSkillExecutionService {

  private static final Logger log = LoggerFactory.getLogger(AiSkillExecutionService.class);

  // Max output tokens for a skill call. 4096 was too low: live runs showed matter-intake (~3960)
  // and fica (~3441) brushing the ceiling and contract-review hitting it exactly (4096) → the JSON
  // was truncated mid-object and failed to parse (AIVERIFY-005). These skills emit verbose
  // structured reports; 16384 gives comfortable headroom while staying well under the model cap.
  private static final int MAX_OUTPUT_TOKENS = 16384;

  private final AiFirmProfileService firmProfileService;
  private final AiCostService costService;
  private final IntegrationRegistry integrationRegistry;
  private final Map<String, AiSkill> skillMap;
  private final AiExecutionPersistenceService persistenceService;

  public AiSkillExecutionService(
      AiFirmProfileService firmProfileService,
      AiCostService costService,
      IntegrationRegistry integrationRegistry,
      List<AiSkill> skills,
      AiExecutionPersistenceService persistenceService) {
    this.firmProfileService = firmProfileService;
    this.costService = costService;
    this.integrationRegistry = integrationRegistry;
    this.skillMap = skills.stream().collect(Collectors.toMap(AiSkill::skillId, s -> s));
    this.persistenceService = persistenceService;
  }

  /**
   * Resolve a skill by ID and execute it. Throws ResourceNotFoundException if the skill ID is
   * unknown.
   */
  public SkillExecutionResult executeSkill(
      String skillId, SkillContext context, UUID invokedBy, List<AiImageInput> images) {
    AiSkill skill = skillMap.get(skillId);
    if (skill == null) {
      throw new ResourceNotFoundException("AiSkill", skillId);
    }
    return executeSkill(new SkillExecutionRequest(skill, context, invokedBy, images));
  }

  public SkillExecutionResult executeSkill(SkillExecutionRequest request) {
    // 1. Pre-flight: load firm profile, check budget, resolve AiProvider (each self-transactional).
    AiFirmProfile profile = firmProfileService.getOrCreateProfile();
    costService.checkBudget(profile);
    AiProvider provider = resolveProvider();

    // 2. Validate vision requirement before recording an execution.
    if (request.skill().requiresVision() && !request.hasImages()) {
      throw new InvalidStateException(
          "Skill requires images",
          "Skill '" + request.skill().skillId() + "' requires vision input but no images provided");
    }

    // 3. Persist IN_PROGRESS (short write tx).
    UUID executionId = persistenceService.startExecution(request, profile);

    // 4. Assemble prompts (short read tx — needs an open session for lazy-loaded data). Any
    //    skill-specific pre-flight failure here (description too short, customer not found, etc.)
    // is
    //    recorded as a FAILED execution and returned, mirroring the pre-refactor broad catch — not
    //    propagated as an HTTP error.
    AiExecutionPersistenceService.AssembledPrompts prompts;
    try {
      prompts = persistenceService.assemblePrompts(request, profile);
    } catch (RuntimeException e) {
      log.warn(
          "AI skill {} prompt assembly failed for entity {}: {}",
          request.skill().skillId(),
          request.context().entityId(),
          e.getMessage());
      return persistenceService.failExecution(executionId, e.getMessage(), 0L);
    }

    // 5. LLM call — NO transaction, NO DB connection held across the multi-second HTTP round-trip.
    AiCompletionResponse response;
    long startMs = System.currentTimeMillis();
    try {
      if (request.skill().requiresVision() && request.hasImages()) {
        response =
            provider.completeWithVision(
                new AiVisionRequest(
                    prompts.systemPrompt(),
                    prompts.userPrompt(),
                    profile.getPreferredModel(),
                    MAX_OUTPUT_TOKENS,
                    0.2,
                    Map.of("skill-id", request.skill().skillId()),
                    request.images()));
      } else {
        response =
            provider.complete(
                new AiCompletionRequest(
                    prompts.systemPrompt(),
                    prompts.userPrompt(),
                    profile.getPreferredModel(),
                    MAX_OUTPUT_TOKENS,
                    0.2,
                    Map.of("skill-id", request.skill().skillId())));
      }
    } catch (Exception e) {
      long durationMs = System.currentTimeMillis() - startMs;
      log.warn(
          "AI skill {} provider call failed for entity {}: {}",
          request.skill().skillId(),
          request.context().entityId(),
          e.getMessage());
      return persistenceService.failExecution(executionId, e.getMessage(), durationMs);
    }

    // 6. Persist results: meter cost, mark COMPLETED, parse output + create gates (short write tx).
    //    A parse failure is recorded as FAILED-with-cost inside completeExecution — never a
    // rollback.
    return persistenceService.completeExecution(executionId, response, request);
  }

  // ── Typed convenience methods for controller one-liners ──────────────────

  private static final UUID FIRM_SENTINEL_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000000");

  public SkillExecutionResult executeFicaVerification(UUID customerId, UUID memberId) {
    var context =
        new SkillContext(
            customerId, "CUSTOMER", "FICA verification for customer " + customerId, Map.of());
    return executeSkill("fica-verification", context, memberId, List.of());
  }

  public SkillExecutionResult executeMatterIntake(
      UUID customerId, String description, UUID memberId) {
    var context =
        new SkillContext(customerId, "CUSTOMER", description, Map.of("description", description));
    return executeSkill("matter-intake", context, memberId, List.of());
  }

  public SkillExecutionResult executeContractReview(
      UUID documentId, UUID projectId, UUID memberId) {
    var context =
        new SkillContext(
            documentId,
            "DOCUMENT",
            "Contract review for document " + documentId,
            Map.of("projectId", projectId));
    return executeSkill("contract-review", context, memberId, List.of());
  }

  public SkillExecutionResult executeDrafting(UUID templateId, UUID projectId, UUID memberId) {
    var context =
        new SkillContext(
            projectId,
            "PROJECT",
            "AI drafting for project " + projectId,
            Map.of("templateId", templateId));
    return executeSkill("drafting", context, memberId, List.of());
  }

  public SkillExecutionResult executeComplianceAudit(UUID memberId) {
    var context = new SkillContext(FIRM_SENTINEL_ID, "FIRM", "Compliance audit for firm", Map.of());
    return executeSkill("compliance-audit", context, memberId, List.of());
  }

  /**
   * Resolve the AI provider for the current tenant. Uses the IntegrationRegistry which returns the
   * configured provider (e.g. AnthropicAiProvider) or NoOpAiProvider when no integration is set up.
   */
  private AiProvider resolveProvider() {
    return integrationRegistry.resolve(IntegrationDomain.AI, AiProvider.class);
  }
}
