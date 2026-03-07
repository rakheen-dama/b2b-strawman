package io.b2mash.b2b.b2bstrawman.automation.config;

import io.b2mash.b2b.b2bstrawman.automation.ActionType;
import io.b2mash.b2b.b2bstrawman.automation.TriggerType;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class AutomationConfigDeserializer {

  private final ObjectMapper objectMapper;

  public AutomationConfigDeserializer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public TriggerConfig deserializeTriggerConfig(TriggerType type, Map<String, Object> raw) {
    return switch (type) {
      case TASK_STATUS_CHANGED,
          PROJECT_STATUS_CHANGED,
          CUSTOMER_STATUS_CHANGED,
          INVOICE_STATUS_CHANGED ->
          objectMapper.convertValue(raw, StatusChangeTriggerConfig.class);
      case BUDGET_THRESHOLD_REACHED ->
          objectMapper.convertValue(raw, BudgetThresholdTriggerConfig.class);
      case TIME_ENTRY_CREATED, DOCUMENT_ACCEPTED, INFORMATION_REQUEST_COMPLETED ->
          new EmptyTriggerConfig();
    };
  }

  public ActionConfig deserializeActionConfig(ActionType type, Map<String, Object> raw) {
    return switch (type) {
      case CREATE_TASK -> objectMapper.convertValue(raw, CreateTaskActionConfig.class);
      case SEND_NOTIFICATION -> objectMapper.convertValue(raw, SendNotificationActionConfig.class);
      case SEND_EMAIL -> objectMapper.convertValue(raw, SendEmailActionConfig.class);
      case UPDATE_STATUS -> objectMapper.convertValue(raw, UpdateStatusActionConfig.class);
      case CREATE_PROJECT -> objectMapper.convertValue(raw, CreateProjectActionConfig.class);
      case ASSIGN_MEMBER -> objectMapper.convertValue(raw, AssignMemberActionConfig.class);
    };
  }
}
