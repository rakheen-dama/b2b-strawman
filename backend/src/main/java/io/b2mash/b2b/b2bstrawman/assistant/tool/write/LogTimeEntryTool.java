package io.b2mash.b2b.b2bstrawman.assistant.tool.write;

import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
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
public class LogTimeEntryTool implements AssistantTool {

  private final TimeEntryService timeEntryService;

  public LogTimeEntryTool(TimeEntryService timeEntryService) {
    this.timeEntryService = timeEntryService;
  }

  @Override
  public String name() {
    return "log_time_entry";
  }

  @Override
  public String description() {
    return "Log a time entry against a task for the current user.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "properties",
            Map.of(
                "taskId", Map.of("type", "string", "description", "UUID of the task"),
                "hours", Map.of("type", "number", "description", "Number of hours (e.g. 1.5)"),
                "date", Map.of("type", "string", "description", "Date in YYYY-MM-DD format"),
                "description",
                    Map.of("type", "string", "description", "Description of the work done"),
                "billable",
                    Map.of(
                        "type",
                        "boolean",
                        "description",
                        "Whether the time entry is billable (defaults to true)")),
        "required", List.of("taskId", "hours", "date"));
  }

  @Override
  public boolean requiresConfirmation() {
    return true;
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of();
  }

  @Override
  public Object execute(Map<String, Object> input, TenantToolContext context) {
    var taskIdStr = (String) input.get("taskId");
    UUID taskId;
    try {
      taskId = UUID.fromString(taskIdStr);
    } catch (IllegalArgumentException e) {
      return Map.of("error", "Invalid taskId format: " + taskIdStr);
    }

    var hoursObj = (Number) input.get("hours");
    if (hoursObj == null) {
      return Map.of("error", "hours is required");
    }
    int durationMinutes = (int) Math.round(hoursObj.doubleValue() * 60);

    var dateStr = (String) input.get("date");
    LocalDate date;
    try {
      date = LocalDate.parse(dateStr);
    } catch (DateTimeParseException e) {
      return Map.of("error", "Invalid date format (expected YYYY-MM-DD): " + dateStr);
    }

    var description = (String) input.get("description");
    var billableObj = input.get("billable");
    boolean billable = billableObj == null || Boolean.TRUE.equals(billableObj);

    var actor = new ActorContext(context.memberId(), context.orgRole());
    var createResult =
        timeEntryService.createTimeEntry(
            taskId, date, durationMinutes, billable, null, description, actor);
    var entry = createResult.entry();

    var result = new LinkedHashMap<String, Object>();
    result.put("id", entry.getId().toString());
    result.put("hours", hoursObj);
    result.put("date", entry.getDate().toString());
    result.put("billable", entry.isBillable());
    return result;
  }
}
