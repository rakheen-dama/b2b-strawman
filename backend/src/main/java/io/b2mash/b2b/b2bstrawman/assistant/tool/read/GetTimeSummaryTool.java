package io.b2mash.b2b.b2bstrawman.assistant.tool.read;

import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryService;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GetTimeSummaryTool implements AssistantTool {

  private final TimeEntryService timeEntryService;

  public GetTimeSummaryTool(TimeEntryService timeEntryService) {
    this.timeEntryService = timeEntryService;
  }

  @Override
  public String name() {
    return "get_time_summary";
  }

  @Override
  public String description() {
    return "Get time entry hours breakdown for a project.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "properties",
            Map.of(
                "projectId",
                    Map.of("type", "string", "description", "UUID of the project to summarize"),
                "startDate",
                    Map.of(
                        "type",
                        "string",
                        "description",
                        "Start date for range filter (ISO format: YYYY-MM-DD)"),
                "endDate",
                    Map.of(
                        "type",
                        "string",
                        "description",
                        "End date for range filter (ISO format: YYYY-MM-DD)")),
        "required", List.of("projectId"));
  }

  @Override
  public boolean requiresConfirmation() {
    return false;
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of();
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

    var startDateStr = (String) input.get("startDate");
    var endDateStr = (String) input.get("endDate");

    LocalDate startDate;
    LocalDate endDate;
    try {
      startDate =
          (startDateStr != null && !startDateStr.isBlank()) ? LocalDate.parse(startDateStr) : null;
      endDate = (endDateStr != null && !endDateStr.isBlank()) ? LocalDate.parse(endDateStr) : null;
    } catch (DateTimeParseException e) {
      return Map.of("error", "Invalid date format (expected YYYY-MM-DD): " + e.getMessage());
    }

    var actor = new ActorContext(context.memberId(), context.orgRole());

    var summary = timeEntryService.getProjectTimeSummary(projectId, actor, startDate, endDate);

    var result = new LinkedHashMap<String, Object>();
    result.put("projectId", projectId.toString());
    result.put("billableMinutes", summary.getBillableMinutes());
    result.put("nonBillableMinutes", summary.getNonBillableMinutes());
    result.put("totalMinutes", summary.getTotalMinutes());
    result.put("contributorCount", summary.getContributorCount());
    result.put("entryCount", summary.getEntryCount());

    // Per-member breakdown is restricted to project leads/admins/owners
    try {
      var byMember =
          timeEntryService.getProjectTimeSummaryByMember(projectId, actor, startDate, endDate);
      result.put(
          "byMember",
          byMember.stream()
              .map(
                  m -> {
                    var map = new LinkedHashMap<String, Object>();
                    map.put("memberId", m.getMemberId().toString());
                    map.put("memberName", m.getMemberName());
                    map.put("billableMinutes", m.getBillableMinutes());
                    map.put("nonBillableMinutes", m.getNonBillableMinutes());
                    map.put("totalMinutes", m.getTotalMinutes());
                    return map;
                  })
              .toList());
    } catch (ForbiddenException e) {
      // User lacks permission for per-member breakdown — omit the field
    }

    return result;
  }
}
