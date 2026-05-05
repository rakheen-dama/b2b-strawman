package io.b2mash.b2b.b2bstrawman.assistant.tool.write;

import io.b2mash.b2b.b2bstrawman.assistant.invocation.AiSpecialistInvocationService;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.InvocationSource;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.IntakeExtractionPayload;
import io.b2mash.b2b.b2bstrawman.assistant.specialist.SystemPromptBuilder;
import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Proposes extracted customer fields as a pending invocation. Does NOT mutate the customer — the
 * applier ({@link io.b2mash.b2b.b2bstrawman.assistant.invocation.applier.IntakeExtractionApplier})
 * does on approval.
 */
@Component
public class ProposeCustomerFieldExtractionTool implements AssistantTool {

  private static final String SPECIALIST_ID = "intake-za";

  private final AiSpecialistInvocationService invocationService;
  private final SystemPromptBuilder promptBuilder;

  public ProposeCustomerFieldExtractionTool(
      AiSpecialistInvocationService invocationService, SystemPromptBuilder promptBuilder) {
    this.invocationService = invocationService;
    this.promptBuilder = promptBuilder;
  }

  @Override
  public String name() {
    return "ProposeCustomerFieldExtraction";
  }

  @Override
  public String description() {
    return "Propose extracted customer fields. Records a PENDING_APPROVAL invocation;"
        + " does NOT mutate the customer.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of(
            "contextEntityType",
            Map.of("type", "string", "enum", List.of("customer", "informationRequest")),
            "contextEntityId",
            Map.of("type", "string", "format", "uuid"),
            "proposedFields",
            Map.of("type", "object", "additionalProperties", true),
            "extractionPath",
            Map.of("type", "string", "enum", List.of("TEXT", "VISION")),
            "popiaFlaggedFields",
            Map.of("type", "array", "items", Map.of("type", "string")),
            "validationFlags",
            Map.of("type", "array", "items", Map.of("type", "string"))),
        "required",
        List.of("contextEntityType", "contextEntityId", "proposedFields", "extractionPath"));
  }

  @Override
  public boolean requiresConfirmation() {
    return false; // Proposal-based — approval is the confirmation gate
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of("CUSTOMER_EDIT");
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object execute(Map<String, Object> input, TenantToolContext context) {
    var contextEntityType = (String) input.get("contextEntityType");
    var contextEntityIdStr = (String) input.get("contextEntityId");

    UUID contextEntityId;
    try {
      contextEntityId = UUID.fromString(contextEntityIdStr);
    } catch (IllegalArgumentException e) {
      return Map.of("error", "Invalid contextEntityId format: " + contextEntityIdStr);
    }

    var proposedFields = (Map<String, Object>) input.get("proposedFields");
    if (proposedFields == null || proposedFields.isEmpty()) {
      return Map.of("error", "proposedFields must not be empty");
    }

    var extractionPath = (String) input.get("extractionPath");
    var popiaFlaggedFields = (List<String>) input.get("popiaFlaggedFields");
    var validationFlags = (List<String>) input.get("validationFlags");

    var payload =
        new IntakeExtractionPayload(
            contextEntityType,
            contextEntityId,
            proposedFields,
            extractionPath,
            popiaFlaggedFields,
            validationFlags);

    var promptVersion = promptBuilder.promptVersion(SPECIALIST_ID);

    var inv =
        invocationService.recordRunning(
            SPECIALIST_ID,
            InvocationSource.MEMBER,
            context.memberId(),
            null,
            contextEntityType,
            contextEntityId,
            promptVersion);
    invocationService.recordProposal(inv.getId(), payload);
    invocationService.markPendingApproval(inv.getId());

    var result = new LinkedHashMap<String, Object>();
    result.put("invocationId", inv.getId().toString());
    result.put("fieldCount", proposedFields.size());
    return result;
  }
}
