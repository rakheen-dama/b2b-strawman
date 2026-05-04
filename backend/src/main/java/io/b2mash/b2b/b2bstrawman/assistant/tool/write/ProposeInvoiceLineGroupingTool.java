package io.b2mash.b2b.b2bstrawman.assistant.tool.write;

import io.b2mash.b2b.b2bstrawman.assistant.invocation.AiSpecialistInvocationService;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.InvocationSource;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.BillingGroupingPayload;
import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Records a {@link BillingGroupingPayload} proposal for human review. Does NOT mutate the invoice's
 * lines; the {@code BillingGroupingApplier} performs the restructuring on approval.
 */
@Component
public class ProposeInvoiceLineGroupingTool implements AssistantTool {

  static final String PROMPT_VERSION = "1.0.0";

  private final AiSpecialistInvocationService invocationService;

  public ProposeInvoiceLineGroupingTool(AiSpecialistInvocationService invocationService) {
    this.invocationService = invocationService;
  }

  @Override
  public String name() {
    return "ProposeInvoiceLineGrouping";
  }

  @Override
  public String description() {
    return "Propose a grouping of time entries into invoice line items. Records a "
        + "PENDING_APPROVAL invocation; does NOT mutate invoice_lines.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "properties",
            Map.of(
                "invoiceId", Map.of("type", "string", "format", "uuid"),
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
                                "description", Map.of("type", "string", "maxLength", 500),
                                "hours", Map.of("type", "number", "minimum", 0),
                                "sourceTimeEntryIds",
                                    Map.of(
                                        "type",
                                        "array",
                                        "items",
                                        Map.of("type", "string", "format", "uuid"))),
                            "required",
                            List.of("description", "hours", "sourceTimeEntryIds")))),
        "required", List.of("invoiceId", "groups"));
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
    Object groupsRaw = input.get("groups");
    if (!(groupsRaw instanceof List<?> rawList)) {
      throw new InvalidStateException("Invalid groups", "groups must be an array");
    }

    var groups = new ArrayList<BillingGroupingPayload.LineGroup>(rawList.size());
    for (Object item : rawList) {
      if (!(item instanceof Map<?, ?> g)) {
        throw new InvalidStateException("Invalid group", "Each group must be an object");
      }
      Object descObj = g.get("description");
      if (!(descObj instanceof String description)) {
        throw new InvalidStateException("Invalid group", "description must be a string");
      }
      Object hoursObj = g.get("hours");
      BigDecimal hours;
      if (hoursObj instanceof Number n) {
        hours = new BigDecimal(n.toString());
      } else if (hoursObj instanceof String s) {
        try {
          hours = new BigDecimal(s);
        } catch (NumberFormatException e) {
          throw new InvalidStateException("Invalid group", "hours is not a number: " + s);
        }
      } else {
        throw new InvalidStateException("Invalid group", "hours must be a number");
      }
      Object idsObj = g.get("sourceTimeEntryIds");
      if (!(idsObj instanceof List<?> idsList)) {
        throw new InvalidStateException(
            "Invalid group", "sourceTimeEntryIds must be an array of UUIDs");
      }
      var ids = new ArrayList<UUID>(idsList.size());
      for (Object o : idsList) {
        ids.add(parseUuid(o, "sourceTimeEntryIds[]"));
      }
      groups.add(new BillingGroupingPayload.LineGroup(description, hours, ids));
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
    invocationService.recordProposal(inv.getId(), new BillingGroupingPayload(invoiceId, groups));
    invocationService.markPendingApproval(inv.getId());

    var result = new LinkedHashMap<String, Object>();
    result.put("invocationId", inv.getId().toString());
    result.put("groupCount", groups.size());
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
