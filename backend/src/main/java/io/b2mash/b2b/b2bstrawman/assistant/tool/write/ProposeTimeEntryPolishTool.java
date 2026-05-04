package io.b2mash.b2b.b2bstrawman.assistant.tool.write;

import io.b2mash.b2b.b2bstrawman.assistant.invocation.AiSpecialistInvocationService;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.InvocationSource;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.BillingPolishPayload;
import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Records a {@link BillingPolishPayload} proposal for human review. Does NOT mutate {@code
 * time_entries} — the {@code BillingPolishApplier} performs the rewrite on approval.
 *
 * <p>Per ADR-267 every Billing output requires explicit human approval; this tool's contract is
 * record-only.
 */
@Component
public class ProposeTimeEntryPolishTool implements AssistantTool {

  static final String PROMPT_VERSION = "1.0.0";

  private final AiSpecialistInvocationService invocationService;
  private final TimeEntryRepository timeEntryRepository;

  public ProposeTimeEntryPolishTool(
      AiSpecialistInvocationService invocationService, TimeEntryRepository timeEntryRepository) {
    this.invocationService = invocationService;
    this.timeEntryRepository = timeEntryRepository;
  }

  @Override
  public String name() {
    return "ProposeTimeEntryPolish";
  }

  @Override
  public String description() {
    return "Propose polished descriptions for time entries on an invoice. Records a "
        + "PENDING_APPROVAL invocation; does NOT mutate time_entries.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "properties",
            Map.of(
                "invoiceId", Map.of("type", "string", "format", "uuid"),
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
                                "timeEntryId", Map.of("type", "string", "format", "uuid"),
                                "polishedDescription", Map.of("type", "string", "maxLength", 1000)),
                            "required",
                            List.of("timeEntryId", "polishedDescription")))),
        "required", List.of("invoiceId", "edits"));
  }

  @Override
  public boolean requiresConfirmation() {
    return true;
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of("INVOICE_EDIT");
  }

  @Override
  public Object execute(Map<String, Object> input, TenantToolContext context) {
    UUID invoiceId = parseUuid(input.get("invoiceId"), "invoiceId");
    Object editsRaw = input.get("edits");
    if (!(editsRaw instanceof List<?> rawList)) {
      throw new InvalidStateException("Invalid edits", "edits must be an array");
    }

    var edits = new ArrayList<BillingPolishPayload.PolishEdit>(rawList.size());
    for (Object item : rawList) {
      if (!(item instanceof Map<?, ?> editMap)) {
        throw new InvalidStateException("Invalid edit", "Each edit must be an object");
      }
      UUID timeEntryId = parseUuid(editMap.get("timeEntryId"), "timeEntryId");
      Object polished = editMap.get("polishedDescription");
      if (!(polished instanceof String afterText)) {
        throw new InvalidStateException(
            "Invalid edit", "polishedDescription must be a non-null string");
      }
      var entry =
          timeEntryRepository
              .findById(timeEntryId)
              .orElseThrow(() -> new ResourceNotFoundException("TimeEntry", timeEntryId));
      String beforeText = entry.getDescription();
      edits.add(new BillingPolishPayload.PolishEdit(timeEntryId, beforeText, afterText));
    }

    var inv =
        invocationService.recordRunning(
            "billing-za",
            InvocationSource.MEMBER,
            context.memberId(),
            null,
            "invoice",
            invoiceId,
            PROMPT_VERSION);
    invocationService.recordProposal(inv.getId(), new BillingPolishPayload(invoiceId, edits));
    invocationService.markPendingApproval(inv.getId());

    var result = new LinkedHashMap<String, Object>();
    result.put("invocationId", inv.getId().toString());
    result.put("editCount", edits.size());
    return result;
  }

  private static UUID parseUuid(Object value, String field) {
    if (!(value instanceof String s)) {
      throw new InvalidStateException("Invalid " + field, field + " must be a UUID string");
    }
    try {
      return UUID.fromString(s);
    } catch (IllegalArgumentException e) {
      throw new InvalidStateException("Invalid " + field, field + " is not a valid UUID: " + s);
    }
  }
}
