package io.b2mash.b2b.b2bstrawman.automation;

import io.b2mash.b2b.b2bstrawman.automation.config.AutomationConfigDeserializer;
import io.b2mash.b2b.b2bstrawman.automation.config.BudgetThresholdTriggerConfig;
import io.b2mash.b2b.b2bstrawman.automation.config.EmptyTriggerConfig;
import io.b2mash.b2b.b2bstrawman.automation.config.StatusChangeTriggerConfig;
import io.b2mash.b2b.b2bstrawman.automation.config.TriggerConfig;
import io.b2mash.b2b.b2bstrawman.event.DomainEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoicePaidEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceSentEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceVoidedEvent;
import io.b2mash.b2b.b2bstrawman.event.ProjectArchivedEvent;
import io.b2mash.b2b.b2bstrawman.event.ProjectCompletedEvent;
import io.b2mash.b2b.b2bstrawman.event.ProjectReopenedEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskStatusChangedEvent;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Validates an {@link AutomationRule}'s trigger configuration against the data carried by a {@link
 * DomainEvent}. Returns {@code true} if the rule's trigger config matches the event, meaning the
 * rule should proceed to condition evaluation.
 */
@Component
public class TriggerConfigMatcher {

  private final AutomationConfigDeserializer configDeserializer;

  public TriggerConfigMatcher(AutomationConfigDeserializer configDeserializer) {
    this.configDeserializer = configDeserializer;
  }

  /**
   * Returns {@code true} if the rule's trigger config matches the given event.
   *
   * @param rule the automation rule whose trigger config to check
   * @param event the domain event to match against
   * @return true if the trigger config matches, false otherwise
   */
  public boolean matches(AutomationRule rule, DomainEvent event) {
    TriggerConfig config =
        configDeserializer.deserializeTriggerConfig(rule.getTriggerType(), rule.getTriggerConfig());

    return switch (config) {
      case StatusChangeTriggerConfig sc -> matchesStatusChange(sc, event);
      case BudgetThresholdTriggerConfig bt -> matchesBudgetThreshold(bt, event);
      case EmptyTriggerConfig _ -> true;
    };
  }

  private boolean matchesStatusChange(StatusChangeTriggerConfig config, DomainEvent event) {
    String newStatus = deriveNewStatus(event);
    String oldStatus = deriveOldStatus(event);

    if (config.toStatus() != null && !config.toStatus().equals(newStatus)) {
      return false;
    }
    if (config.fromStatus() != null && !config.fromStatus().equals(oldStatus)) {
      return false;
    }
    return true;
  }

  private String deriveNewStatus(DomainEvent event) {
    return switch (event) {
      case TaskStatusChangedEvent e -> e.newStatus();
      case ProjectCompletedEvent _ -> "COMPLETED";
      case ProjectArchivedEvent _ -> "ARCHIVED";
      case ProjectReopenedEvent _ -> "ACTIVE";
      case InvoiceSentEvent _ -> "SENT";
      case InvoicePaidEvent _ -> "PAID";
      case InvoiceVoidedEvent _ -> "VOIDED";
      default -> null;
    };
  }

  private String deriveOldStatus(DomainEvent event) {
    return switch (event) {
      case TaskStatusChangedEvent e -> e.oldStatus();
      case ProjectReopenedEvent e -> e.previousStatus();
      case ProjectCompletedEvent _ -> detailOrNull(event.details(), "previous_status");
      case ProjectArchivedEvent _ -> detailOrNull(event.details(), "previous_status");
      default -> null;
    };
  }

  private boolean matchesBudgetThreshold(BudgetThresholdTriggerConfig config, DomainEvent event) {
    Object consumedPctObj = event.details().get("consumed_pct");
    if (consumedPctObj == null) {
      return false;
    }
    double consumedPct =
        consumedPctObj instanceof Number n
            ? n.doubleValue()
            : Double.parseDouble(consumedPctObj.toString());
    return consumedPct >= config.thresholdPercent();
  }

  private static String detailOrNull(Map<String, Object> details, String key) {
    if (details == null) {
      return null;
    }
    Object value = details.get(key);
    return value != null ? value.toString() : null;
  }
}
