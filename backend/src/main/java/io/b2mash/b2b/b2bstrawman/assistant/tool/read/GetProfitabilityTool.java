package io.b2mash.b2b.b2bstrawman.assistant.tool.read;

import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.report.ReportController.CurrencyBreakdown;
import io.b2mash.b2b.b2bstrawman.report.ReportService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GetProfitabilityTool implements AssistantTool {

  private final ReportService reportService;

  public GetProfitabilityTool(ReportService reportService) {
    this.reportService = reportService;
  }

  @Override
  public String name() {
    return "get_profitability";
  }

  @Override
  public String description() {
    return "Get profitability summary (revenue, cost, margin) for a project or customer.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "properties",
            Map.of(
                "projectId",
                    Map.of("type", "string", "description", "UUID of the project to analyse"),
                "customerId",
                    Map.of("type", "string", "description", "UUID of the customer to analyse")),
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
    var projectIdStr = (String) input.get("projectId");
    var customerIdStr = (String) input.get("customerId");

    if ((projectIdStr == null || projectIdStr.isBlank())
        && (customerIdStr == null || customerIdStr.isBlank())) {
      return Map.of("error", "Either projectId or customerId is required");
    }

    var actor = new ActorContext(context.memberId(), context.orgRole());

    try {
      if (projectIdStr != null && !projectIdStr.isBlank()) {
        UUID projectId;
        try {
          projectId = UUID.fromString(projectIdStr);
        } catch (IllegalArgumentException e) {
          return Map.of("error", "Invalid projectId format: " + projectIdStr);
        }

        var response = reportService.getProjectProfitability(projectId, null, null, actor, false);

        var result = new LinkedHashMap<String, Object>();
        result.put("type", "project");
        result.put("id", response.projectId().toString());
        result.put("name", response.projectName());
        result.put("currencies", mapCurrencies(response.currencies()));
        return result;
      }

      // Customer scope
      UUID customerId;
      try {
        customerId = UUID.fromString(customerIdStr);
      } catch (IllegalArgumentException e) {
        return Map.of("error", "Invalid customerId format: " + customerIdStr);
      }

      var response = reportService.getCustomerProfitability(customerId, null, null, actor, false);

      var result = new LinkedHashMap<String, Object>();
      result.put("type", "customer");
      result.put("id", response.customerId().toString());
      result.put("name", response.customerName());
      result.put("currencies", mapCurrencies(response.currencies()));
      return result;
    } catch (ResourceNotFoundException e) {
      return Map.of("error", e.getMessage());
    } catch (ForbiddenException e) {
      return Map.of("error", "Insufficient permissions to view profitability");
    }
  }

  private List<Map<String, Object>> mapCurrencies(List<CurrencyBreakdown> currencies) {
    return currencies.stream()
        .<Map<String, Object>>map(
            cb -> {
              var map = new LinkedHashMap<String, Object>();
              map.put("currency", cb.currency());
              map.put("totalBillableHours", cb.totalBillableHours());
              map.put("totalNonBillableHours", cb.totalNonBillableHours());
              map.put("totalHours", cb.totalHours());
              map.put("billableValue", cb.billableValue());
              map.put("costValue", cb.costValue());
              map.put("margin", cb.margin());
              map.put("marginPercent", cb.marginPercent());
              return map;
            })
        .toList();
  }
}
