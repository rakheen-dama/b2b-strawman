package io.b2mash.b2b.b2bstrawman.assistant.tool.read;

import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GetUnbilledTimeTool implements AssistantTool {

  private final InvoiceService invoiceService;
  private final TimeEntryService timeEntryService;

  public GetUnbilledTimeTool(InvoiceService invoiceService, TimeEntryService timeEntryService) {
    this.invoiceService = invoiceService;
    this.timeEntryService = timeEntryService;
  }

  @Override
  public String name() {
    return "get_unbilled_time";
  }

  @Override
  public String description() {
    return "Get unbilled time entries for a customer or project, showing entry count and total hours.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "properties",
            Map.of(
                "customerId",
                    Map.of(
                        "type",
                        "string",
                        "description",
                        "UUID of a customer — returns unbilled time for all linked projects"),
                "projectId",
                    Map.of(
                        "type",
                        "string",
                        "description",
                        "UUID of a project — returns billable time summary for that project")),
        "required", List.of());
  }

  @Override
  public boolean requiresConfirmation() {
    return false;
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of("FINANCIAL_VISIBILITY");
  }

  @Override
  public Object execute(Map<String, Object> input, TenantToolContext context) {
    var customerIdStr = (String) input.get("customerId");
    var projectIdStr = (String) input.get("projectId");

    if ((customerIdStr == null || customerIdStr.isBlank())
        && (projectIdStr == null || projectIdStr.isBlank())) {
      return Map.of("error", "Either customerId or projectId is required");
    }

    if (customerIdStr != null && !customerIdStr.isBlank()) {
      UUID customerId;
      try {
        customerId = UUID.fromString(customerIdStr);
      } catch (IllegalArgumentException e) {
        return Map.of("error", "Invalid customerId format: " + customerIdStr);
      }

      var response = invoiceService.getUnbilledTime(customerId, null, null);

      int totalEntries = 0;
      double totalHours = 0;
      for (var group : response.projects()) {
        totalEntries += group.entries().size();
        for (var entry : group.entries()) {
          totalHours += entry.durationMinutes() / 60.0;
        }
      }

      var result = new LinkedHashMap<String, Object>();
      result.put("scope", "customer");
      result.put("customerId", customerId.toString());
      result.put("customerName", response.customerName());
      result.put("entryCount", totalEntries);
      result.put("totalHours", Math.round(totalHours * 100.0) / 100.0);
      result.put("projectCount", response.projects().size());
      return result;
    }

    // Project scope
    UUID projectId;
    try {
      projectId = UUID.fromString(projectIdStr);
    } catch (IllegalArgumentException e) {
      return Map.of("error", "Invalid projectId format: " + projectIdStr);
    }

    var actor = new ActorContext(context.memberId(), context.orgRole());
    var summary = timeEntryService.getProjectTimeSummary(projectId, actor, null, null);

    var result = new LinkedHashMap<String, Object>();
    result.put("scope", "project");
    result.put("projectId", projectId.toString());
    result.put("entryCount", summary.getEntryCount());
    result.put("billableMinutes", summary.getBillableMinutes());
    result.put("totalHours", Math.round(summary.getTotalMinutes() / 60.0 * 100.0) / 100.0);
    result.put(
        "note", "Shows total billable time for the project; some entries may already be invoiced");
    return result;
  }
}
