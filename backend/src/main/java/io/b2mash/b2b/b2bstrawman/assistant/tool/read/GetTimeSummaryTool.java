package io.b2mash.b2b.b2bstrawman.assistant.tool.read;

import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GetTimeSummaryTool implements AssistantTool {

  private final TimeEntryRepository timeEntryRepository;

  public GetTimeSummaryTool(TimeEntryRepository timeEntryRepository) {
    this.timeEntryRepository = timeEntryRepository;
  }

  @Override
  public String name() {
    return "get_time_summary";
  }

  @Override
  public String description() {
    return "Get time entry hours breakdown for a project or the entire organization.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "properties",
            Map.of(
                "projectId",
                    Map.of(
                        "type",
                        "string",
                        "description",
                        "UUID of the project to summarize (if omitted, returns org-level summary)"),
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
        "required", List.of());
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
    var startDateStr = (String) input.get("startDate");
    var endDateStr = (String) input.get("endDate");

    var startDate =
        (startDateStr != null && !startDateStr.isBlank()) ? LocalDate.parse(startDateStr) : null;
    var endDate =
        (endDateStr != null && !endDateStr.isBlank()) ? LocalDate.parse(endDateStr) : null;

    if (projectIdStr != null && !projectIdStr.isBlank()) {
      var projectId = UUID.fromString(projectIdStr);
      return buildProjectSummary(projectId, startDate, endDate);
    }

    return buildOrgSummary(startDate, endDate);
  }

  private Map<String, Object> buildProjectSummary(
      UUID projectId, LocalDate startDate, LocalDate endDate) {
    var summary = timeEntryRepository.projectTimeSummary(projectId, startDate, endDate);
    var byMember = timeEntryRepository.projectTimeSummaryByMember(projectId, startDate, endDate);

    var result = new LinkedHashMap<String, Object>();
    result.put("projectId", projectId.toString());
    result.put("billableMinutes", summary.getBillableMinutes());
    result.put("nonBillableMinutes", summary.getNonBillableMinutes());
    result.put("totalMinutes", summary.getTotalMinutes());
    result.put("contributorCount", summary.getContributorCount());
    result.put("entryCount", summary.getEntryCount());
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
    return result;
  }

  private Map<String, Object> buildOrgSummary(LocalDate startDate, LocalDate endDate) {
    var entries = timeEntryRepository.findByFilters(null, null, startDate, endDate);
    long billableMinutes = 0;
    long nonBillableMinutes = 0;
    for (var entry : entries) {
      if (entry.isBillable()) {
        billableMinutes += entry.getDurationMinutes();
      } else {
        nonBillableMinutes += entry.getDurationMinutes();
      }
    }

    var result = new LinkedHashMap<String, Object>();
    result.put("billableMinutes", billableMinutes);
    result.put("nonBillableMinutes", nonBillableMinutes);
    result.put("totalMinutes", billableMinutes + nonBillableMinutes);
    result.put("entryCount", entries.size());
    return result;
  }
}
