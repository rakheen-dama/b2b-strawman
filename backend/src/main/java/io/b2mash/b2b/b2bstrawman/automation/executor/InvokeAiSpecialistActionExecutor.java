package io.b2mash.b2b.b2bstrawman.automation.executor;

import io.b2mash.b2b.b2bstrawman.assistant.invocation.AiSpecialistInvocationService;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.InvocationSource;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.OutputPayload;
import io.b2mash.b2b.b2bstrawman.assistant.specialist.NonInteractiveSpecialistRunner;
import io.b2mash.b2b.b2bstrawman.assistant.specialist.SpecialistRegistry;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.automation.ActionType;
import io.b2mash.b2b.b2bstrawman.automation.VariableResolver;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionConfig;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionFailure;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionResult;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionSuccess;
import io.b2mash.b2b.b2bstrawman.automation.config.InvokeAiSpecialistActionConfig;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Executor for {@link ActionType#INVOKE_AI_SPECIALIST} automation actions. Resolves the specialist,
 * runs it non-interactively, and records the invocation result (PENDING_APPROVAL for REVIEW mode,
 * AUTO_APPLIED for DIRECT mode on INBOX only).
 */
@Component
public class InvokeAiSpecialistActionExecutor implements ActionExecutor {

  private static final Logger log = LoggerFactory.getLogger(InvokeAiSpecialistActionExecutor.class);

  private final SpecialistRegistry specialistRegistry;
  private final VariableResolver variableResolver;
  private final AiSpecialistInvocationService invocationService;
  private final NonInteractiveSpecialistRunner runner;
  private final AuditService auditService;

  public InvokeAiSpecialistActionExecutor(
      SpecialistRegistry specialistRegistry,
      VariableResolver variableResolver,
      AiSpecialistInvocationService invocationService,
      NonInteractiveSpecialistRunner runner,
      AuditService auditService) {
    this.specialistRegistry = specialistRegistry;
    this.variableResolver = variableResolver;
    this.invocationService = invocationService;
    this.runner = runner;
    this.auditService = auditService;
  }

  @Override
  public ActionType supportedType() {
    return ActionType.INVOKE_AI_SPECIALIST;
  }

  @Override
  public ActionResult execute(
      ActionConfig config, Map<String, Map<String, Object>> context, UUID automationExecutionId) {
    if (!(config instanceof InvokeAiSpecialistActionConfig aiConfig)) {
      return new ActionFailure(
          "Invalid config type for INVOKE_AI_SPECIALIST", config.getClass().getSimpleName());
    }

    try {
      // Resolve specialist
      var specialist = specialistRegistry.requireById(aiConfig.specialistId());

      // Reject non-automation-capable specialist
      if (!specialist.automationCapable()) {
        return new ActionFailure(
            "Specialist '" + specialist.id() + "' is not automation-capable", null);
      }

      // DIRECT-mode validation guard (ADR-267): only inbox specialist allowed
      String mode = aiConfig.mode() != null ? aiConfig.mode().toUpperCase() : "REVIEW";
      if ("DIRECT".equals(mode) && !specialist.id().startsWith("inbox")) {
        return new ActionFailure(
            "DIRECT mode is only permitted for INBOX specialist (ADR-267)", specialist.id());
      }

      // Resolve variables in contextRef
      String contextEntityType = null;
      UUID contextEntityId = null;
      if (aiConfig.contextRef() != null) {
        String entityTypeTemplate = aiConfig.contextRef().get("entityType");
        String entityIdTemplate = aiConfig.contextRef().get("entityId");
        contextEntityType =
            entityTypeTemplate != null
                ? variableResolver.resolve(entityTypeTemplate, context)
                : null;
        String resolvedEntityId =
            entityIdTemplate != null ? variableResolver.resolve(entityIdTemplate, context) : null;
        if (resolvedEntityId != null) {
          try {
            contextEntityId = UUID.fromString(resolvedEntityId);
          } catch (IllegalArgumentException e) {
            return new ActionFailure(
                "Invalid entityId after variable resolution: " + resolvedEntityId, null);
          }
        }
      }

      // Resolve actor for synthetic ActorContext
      UUID actorId = VariableResolver.resolveUuid(context, "actor", "id");
      if (actorId == null) {
        // Fallback: use the rule creator
        actorId = VariableResolver.resolveUuid(context, "rule", "createdBy");
      }

      if ("DIRECT".equals(mode)) {
        return executeDirect(
            aiConfig, specialist.id(), actorId, contextEntityType, contextEntityId, context);
      } else {
        return executeReview(
            aiConfig,
            specialist.id(),
            actorId,
            automationExecutionId,
            contextEntityType,
            contextEntityId,
            context);
      }
    } catch (Exception e) {
      log.error("Failed to execute INVOKE_AI_SPECIALIST action: {}", e.getMessage(), e);
      return new ActionFailure("Failed to invoke AI specialist: " + e.getMessage(), e.toString());
    }
  }

  private ActionResult executeReview(
      InvokeAiSpecialistActionConfig aiConfig,
      String specialistId,
      UUID actorId,
      UUID automationExecutionId,
      String contextEntityType,
      UUID contextEntityId,
      Map<String, Map<String, Object>> context) {

    // Record RUNNING invocation (actionExecutionId is null — the FK references action_executions,
    // not automation_executions, and we don't have the action execution ID from within the
    // executor)
    var invocation =
        invocationService.recordRunning(
            specialistId,
            InvocationSource.AUTOMATION,
            actorId,
            null,
            contextEntityType != null ? contextEntityType : "unknown",
            contextEntityId != null ? contextEntityId : UUID.randomUUID(),
            runner.promptVersion(specialistId));

    try {
      // Run specialist non-interactively
      OutputPayload output =
          runner.run(specialistId, contextEntityType, contextEntityId, actorId, context);

      // Record proposal and mark pending approval
      invocationService.recordProposal(invocation.getId(), output);
      invocationService.markPendingApproval(invocation.getId());

      emitAuditEvent(specialistId, contextEntityType, contextEntityId, invocation.getId());

      log.info(
          "AI specialist {} invocation {} queued PENDING_APPROVAL",
          specialistId,
          invocation.getId());
      return new ActionSuccess(Map.of("invocationId", invocation.getId().toString()));
    } catch (Exception e) {
      log.error(
          "AI specialist {} invocation {} failed: {}",
          specialistId,
          invocation.getId(),
          e.getMessage(),
          e);
      invocationService.markFailed(invocation.getId(), e.getMessage());
      return new ActionFailure("AI specialist invocation failed: " + e.getMessage(), e.toString());
    }
  }

  private ActionResult executeDirect(
      InvokeAiSpecialistActionConfig aiConfig,
      String specialistId,
      UUID actorId,
      String contextEntityType,
      UUID contextEntityId,
      Map<String, Map<String, Object>> context) {

    try {
      // Run specialist non-interactively
      OutputPayload output =
          runner.run(specialistId, contextEntityType, contextEntityId, actorId, context);

      // Atomically record + auto-apply
      var invocation =
          invocationService.recordAndAutoApply(
              specialistId,
              InvocationSource.AUTOMATION,
              actorId,
              contextEntityType != null ? contextEntityType : "unknown",
              contextEntityId != null ? contextEntityId : UUID.randomUUID(),
              runner.promptVersion(specialistId),
              output);

      emitAuditEvent(specialistId, contextEntityType, contextEntityId, invocation.getId());

      log.info(
          "AI specialist {} invocation {} AUTO_APPLIED (DIRECT mode)",
          specialistId,
          invocation.getId());
      return new ActionSuccess(Map.of("invocationId", invocation.getId().toString()));
    } catch (Exception e) {
      log.error("AI specialist {} DIRECT invocation failed: {}", specialistId, e.getMessage(), e);
      return new ActionFailure(
          "AI specialist DIRECT invocation failed: " + e.getMessage(), e.toString());
    }
  }

  private void emitAuditEvent(
      String specialistId, String contextEntityType, UUID contextEntityId, UUID invocationId) {
    try {
      auditService.log(
          AuditEventBuilder.builder()
              .eventType("ai.specialist.invoked")
              .entityType(contextEntityType != null ? contextEntityType : "unknown")
              .entityId(contextEntityId != null ? contextEntityId : invocationId)
              .source("AUTOMATION")
              .actorType("SYSTEM")
              .details(
                  Map.of("specialistId", specialistId, "invocationId", invocationId.toString()))
              .build());
    } catch (Exception e) {
      log.warn("Failed to emit ai.specialist.invoked audit event: {}", e.getMessage(), e);
    }
  }
}
