package io.b2mash.b2b.b2bstrawman.assistant.tool.write;

import io.b2mash.b2b.b2bstrawman.assistant.invocation.AiSpecialistInvocationService;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.InvocationSource;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.BillingGroupingPayload;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.BillingGroupingPayload.LineGroup;
import io.b2mash.b2b.b2bstrawman.assistant.specialist.SystemPromptBuilder;
import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Write tool that records invoice line-item groupings as a proposal requiring approval.
 *
 * <p>Does NOT mutate the invoice directly — the {@code BillingGroupingApplier} does that on
 * approval.
 */
@Component
public class ProposeInvoiceLineGroupingTool implements AssistantTool {

  private static final String SPECIALIST_ID = "billing-za";

  private final AiSpecialistInvocationService invocationService;
  private final SystemPromptBuilder promptBuilder;

  public ProposeInvoiceLineGroupingTool(
      AiSpecialistInvocationService invocationService, SystemPromptBuilder promptBuilder) {
    this.invocationService = invocationService;
    this.promptBuilder = promptBuilder;
  }

  @Override
  public String name() {
    return "ProposeInvoiceLineGrouping";
  }

  @Override
  public String description() {
    return "Propose grouped line items for an invoice, aggregating multiple time entries into"
        + " descriptive groups. Creates a pending proposal that requires approval.";
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
            "groups",
            Map.of(
                "type",
                "array",
                "items",
                Map.of(
                    "type",
                    "object",
                    "properties",
                    Map.of(
                        "description",
                        Map.of("type", "string", "maxLength", 200),
                        "hours",
                        Map.of("type", "number", "minimum", 0),
                        "sourceTimeEntryIds",
                        Map.of(
                            "type", "array", "items", Map.of("type", "string", "format", "uuid"))),
                    "required",
                    List.of("description", "hours", "sourceTimeEntryIds")))),
        "required",
        List.of("invoiceId", "groups"));
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

    var rawGroups = (List<Map<String, Object>>) input.get("groups");
    if (rawGroups == null || rawGroups.isEmpty()) {
      return Map.of("error", "groups array must not be empty");
    }

    List<LineGroup> groups;
    try {
      groups =
          rawGroups.stream()
              .map(
                  m -> {
                    var desc = (String) m.get("description");
                    var hours = new BigDecimal(m.get("hours").toString());
                    var sourceIds =
                        ((List<String>) m.get("sourceTimeEntryIds"))
                            .stream().map(UUID::fromString).toList();
                    return new LineGroup(desc, hours, sourceIds);
                  })
              .toList();
    } catch (IllegalArgumentException e) {
      return Map.of("error", "Malformed group field: " + e.getMessage());
    }

    var payload = new BillingGroupingPayload(invoiceId, groups);
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
    result.put("groupCount", groups.size());
    return result;
  }
}
