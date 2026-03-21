package io.b2mash.b2b.b2bstrawman.assistant.tool.read;

import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.budget.ProjectBudgetService;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GetProjectBudgetTool implements AssistantTool {

  private final ProjectBudgetService projectBudgetService;

  public GetProjectBudgetTool(ProjectBudgetService projectBudgetService) {
    this.projectBudgetService = projectBudgetService;
  }

  @Override
  public String name() {
    return "get_project_budget";
  }

  @Override
  public String description() {
    return "Get the budget status for a project, including hours/amount consumed, remaining, and alert status.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "properties",
            Map.of(
                "projectId",
                Map.of("type", "string", "description", "UUID of the project to check budget")),
        "required", List.of("projectId"));
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
    var projectIdStr = (String) input.get("projectId");
    if (projectIdStr == null || projectIdStr.isBlank()) {
      return Map.of("error", "projectId is required");
    }

    UUID projectId;
    try {
      projectId = UUID.fromString(projectIdStr);
    } catch (IllegalArgumentException e) {
      return Map.of("error", "Invalid projectId format: " + projectIdStr);
    }

    var actor = new ActorContext(context.memberId(), context.orgRole());

    try {
      var status = projectBudgetService.getBudgetWithStatus(projectId, actor);

      var result = new LinkedHashMap<String, Object>();
      result.put("projectId", status.projectId().toString());
      result.put("budgetHours", status.budgetHours());
      result.put("budgetAmount", status.budgetAmount());
      result.put("budgetCurrency", status.budgetCurrency());
      result.put("alertThresholdPct", status.alertThresholdPct());
      result.put("hoursConsumed", status.hoursConsumed());
      result.put("hoursRemaining", status.hoursRemaining());
      result.put("hoursConsumedPct", status.hoursConsumedPct());
      result.put("amountConsumed", status.amountConsumed());
      result.put("amountRemaining", status.amountRemaining());
      result.put("amountConsumedPct", status.amountConsumedPct());
      result.put("hoursStatus", status.hoursStatus() != null ? status.hoursStatus().name() : null);
      result.put(
          "amountStatus", status.amountStatus() != null ? status.amountStatus().name() : null);
      result.put(
          "overallStatus", status.overallStatus() != null ? status.overallStatus().name() : null);
      result.put("notes", status.notes());
      return result;
    } catch (ResourceNotFoundException e) {
      return Map.of("error", "Budget not found for project " + projectId);
    }
  }
}
