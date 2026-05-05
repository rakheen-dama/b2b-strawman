package io.b2mash.b2b.b2bstrawman.assistant.tool.write;

import io.b2mash.b2b.b2bstrawman.assistant.invocation.AiSpecialistInvocationService;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.InvocationSource;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.BillingPolishPayload;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.BillingPolishPayload.PolishEdit;
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
 * Write tool that records polished time-entry descriptions as a proposal requiring approval.
 *
 * <p>Does NOT mutate time entries directly — the {@code BillingPolishApplier} does that on
 * approval.
 */
@Component
public class ProposeTimeEntryPolishTool implements AssistantTool {

  private static final String SPECIALIST_ID = "billing-za";

  private final AiSpecialistInvocationService invocationService;
  private final SystemPromptBuilder promptBuilder;

  public ProposeTimeEntryPolishTool(
      AiSpecialistInvocationService invocationService, SystemPromptBuilder promptBuilder) {
    this.invocationService = invocationService;
    this.promptBuilder = promptBuilder;
  }

  @Override
  public String name() {
    return "ProposeTimeEntryPolish";
  }

  @Override
  public String description() {
    return "Propose polished (SA English, LSSA-vocabulary) descriptions for time entries on an"
        + " invoice. Creates a pending proposal that requires approval before changes are applied.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of(
            "invoiceId",
            Map.of("type", "string", "format", "uuid"),
            "edits",
            Map.of(
                "type",
                "array",
                "items",
                Map.of(
                    "type",
                    "object",
                    "properties",
                    Map.of(
                        "timeEntryId",
                        Map.of("type", "string", "format", "uuid"),
                        "polishedDescription",
                        Map.of("type", "string", "maxLength", 1000)),
                    "required",
                    List.of("timeEntryId", "polishedDescription")))),
        "required",
        List.of("invoiceId", "edits"));
  }

  @Override
  public boolean requiresConfirmation() {
    return false; // Proposal-based — approval is the confirmation gate
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of("INVOICING");
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object execute(Map<String, Object> input, TenantToolContext context) {
    var invoiceIdStr = (String) input.get("invoiceId");
    UUID invoiceId;
    try {
      invoiceId = UUID.fromString(invoiceIdStr);
    } catch (IllegalArgumentException e) {
      return Map.of("error", "Invalid invoiceId format: " + invoiceIdStr);
    }

    var rawEdits = (List<Map<String, Object>>) input.get("edits");
    if (rawEdits == null || rawEdits.isEmpty()) {
      return Map.of("error", "edits array must not be empty");
    }

    List<PolishEdit> edits;
    try {
      edits =
          rawEdits.stream()
              .map(
                  m -> {
                    var teId = UUID.fromString((String) m.get("timeEntryId"));
                    var polished = (String) m.get("polishedDescription");
                    return new PolishEdit(teId, null, polished);
                  })
              .toList();
    } catch (IllegalArgumentException e) {
      return Map.of("error", "Malformed edit field: " + e.getMessage());
    }

    var payload = new BillingPolishPayload(invoiceId, edits);
    var promptVersion = promptBuilder.promptVersion(SPECIALIST_ID);

    var inv =
        invocationService.recordRunning(
            SPECIALIST_ID,
            InvocationSource.MEMBER,
            context.memberId(),
            null,
            "invoice",
            invoiceId,
            promptVersion);
    invocationService.recordProposal(inv.getId(), payload);
    invocationService.markPendingApproval(inv.getId());

    var result = new LinkedHashMap<String, Object>();
    result.put("invocationId", inv.getId().toString());
    result.put("editCount", edits.size());
    return result;
  }
}
