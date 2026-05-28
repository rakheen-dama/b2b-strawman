package io.b2mash.b2b.b2bstrawman.automation;

import com.fasterxml.jackson.databind.JsonNode;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobHandler;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Job handler for scheduled automation trigger cron pass. Evaluates all enabled SCHEDULED rules for
 * the current tenant, fires those whose next cron fire time has arrived, and updates their
 * lastRunAt.
 *
 * <p>Extracted from {@link AutomationScheduler#pollScheduledTriggers()}.
 */
@Component
public class AutomationPollTriggersHandler implements JobHandler {

  private static final Logger log = LoggerFactory.getLogger(AutomationPollTriggersHandler.class);

  private final AutomationRuleRepository ruleRepository;
  private final AutomationEventListener automationEventListener;
  private final TransactionTemplate transactionTemplate;

  public AutomationPollTriggersHandler(
      AutomationRuleRepository ruleRepository,
      AutomationEventListener automationEventListener,
      TransactionTemplate transactionTemplate) {
    this.ruleRepository = ruleRepository;
    this.automationEventListener = automationEventListener;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public String jobType() {
    return "automation_poll_triggers";
  }

  @Override
  public void execute(@Nullable JsonNode payload) {
    int fired = processScheduledTenant();
    if (fired > 0) {
      log.info("AutomationPollTriggersHandler: fired {} scheduled rules", fired);
    }
  }

  private int processScheduledTenant() {
    record DueRule(AutomationRule rule, Instant now) {}
    List<DueRule> dueRules;
    {
      var result =
          transactionTemplate.execute(
              tx -> {
                var rules = ruleRepository.findByEnabledAndTriggerType(true, TriggerType.SCHEDULED);
                if (rules.isEmpty()) return List.<DueRule>of();

                Instant now = Instant.now();
                var due = new java.util.ArrayList<DueRule>();

                for (var rule : rules) {
                  try {
                    if (shouldFire(rule, now)) {
                      rule.setLastRunAt(now);
                      ruleRepository.save(rule);
                      due.add(new DueRule(rule, now));
                    }
                  } catch (Exception e) {
                    log.error(
                        "Failed to evaluate scheduled rule {} ({}): {}",
                        rule.getId(),
                        rule.getName(),
                        e.getMessage(),
                        e);
                  }
                }
                return due;
              });
      dueRules = result != null ? result : List.of();
    }

    int fired = 0;
    for (var due : dueRules) {
      try {
        fireScheduledRule(due.rule(), due.now());
        fired++;
      } catch (Exception e) {
        log.error(
            "Failed to fire scheduled rule {} ({}): {}",
            due.rule().getId(),
            due.rule().getName(),
            e.getMessage(),
            e);
      }
    }
    return fired;
  }

  private boolean shouldFire(AutomationRule rule, Instant now) {
    Map<String, Object> triggerConfig = rule.getTriggerConfig();
    if (triggerConfig == null) return false;

    Object cronObj = triggerConfig.get("cronExpression");
    if (cronObj == null) return false;
    String cronExpr = cronObj.toString();

    try {
      var cron = CronExpression.parse(cronExpr);
      Instant baseTime = rule.getLastRunAt() != null ? rule.getLastRunAt() : rule.getCreatedAt();
      var next = cron.next(baseTime.atZone(java.time.ZoneOffset.UTC).toLocalDateTime());
      if (next == null) return false;
      Instant nextFire = next.toInstant(java.time.ZoneOffset.UTC);
      return !nextFire.isAfter(now);
    } catch (IllegalArgumentException e) {
      log.warn(
          "Invalid cron expression '{}' for rule {} ({}): {}",
          cronExpr,
          rule.getId(),
          rule.getName(),
          e.getMessage());
      return false;
    }
  }

  private void fireScheduledRule(AutomationRule rule, Instant now) {
    Map<String, Map<String, Object>> context = new LinkedHashMap<>();
    context.put(
        "rule",
        Map.of(
            "id", rule.getId().toString(),
            "name", rule.getName(),
            "createdBy", rule.getCreatedBy().toString()));

    automationEventListener.fireRule(rule, context);
    log.info("Fired scheduled rule {} ({}) at {}", rule.getId(), rule.getName(), now);
  }
}
