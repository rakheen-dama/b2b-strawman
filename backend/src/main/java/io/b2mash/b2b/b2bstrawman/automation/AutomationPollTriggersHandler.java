package io.b2mash.b2b.b2bstrawman.automation;

import com.fasterxml.jackson.databind.JsonNode;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobHandler;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectStatus;
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
  private final AutomationActionRepository actionRepository;
  private final AutomationEventListener automationEventListener;
  private final ConditionEvaluator conditionEvaluator;
  private final ProjectRepository projectRepository;
  private final TransactionTemplate transactionTemplate;

  public AutomationPollTriggersHandler(
      AutomationRuleRepository ruleRepository,
      AutomationActionRepository actionRepository,
      AutomationEventListener automationEventListener,
      ConditionEvaluator conditionEvaluator,
      ProjectRepository projectRepository,
      TransactionTemplate transactionTemplate) {
    this.ruleRepository = ruleRepository;
    this.actionRepository = actionRepository;
    this.automationEventListener = automationEventListener;
    this.conditionEvaluator = conditionEvaluator;
    this.projectRepository = projectRepository;
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

  /**
   * Package-visible entry point shared with {@link AutomationScheduler} so the scheduled cron pass
   * has a single source of truth (OBS-505 — eliminated the duplicate dead path in the scheduler).
   */
  int processScheduledTenant() {
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
    // Project-scoped scheduled rules (e.g. weekly per-matter AI summaries) must fan out: one
    // execution per active project, each with a {{project.*}} context so contextRefs and conditions
    // resolve (OBS-505). Rules with no project-scoped action (e.g. SEND_NOTIFICATION) fire once.
    if (requiresProjectContext(rule)) {
      fireScheduledRulePerActiveProject(rule, now);
      return;
    }

    // No project context needed — fire once with the minimal rule+actor context (no regression).
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

  /**
   * Fans out a project-scoped scheduled rule across all active projects in the tenant. Conditions
   * are evaluated per project (the scheduled path otherwise bypasses {@link ConditionEvaluator}),
   * so a {@code project.status EQUALS ACTIVE} condition — or any future per-project condition — is
   * honored. Scale note: only invoked for due rules that actually need project context, and bounded
   * to the tenant's active projects (60s tick × due rules × active projects).
   *
   * <p><b>Fan-out limitation (OBS-505):</b> fan-out is decided per <i>rule</i>, not per
   * <i>action</i>. If a SCHEDULED rule is classified project-scoped (see {@link
   * #requiresProjectContext}) because <i>any</i> of its actions targets a project contextRef, then
   * <b>all</b> of that rule's actions are re-fired once per active project. A rule that mixes a
   * project-scoped action (e.g. {@code INVOKE_AI_SPECIALIST}, {@code entityType=project}) with a
   * non-project action (e.g. {@code SEND_NOTIFICATION}) would therefore fire the non-project action
   * N times — once per active project — instead of once. No shipped AI-specialist pack template
   * does this today (each SCHEDULED template carries a single project-scoped action), and {@code
   * AiSpecialistAutomationPackValidityTest.noShippedScheduledTemplateMixesProjectAndNonProjectActions}
   * guards against a future template silently introducing the mix — adding one trips CI and forces
   * a deliberate per-action design decision rather than a silent multi-fire.
   */
  private void fireScheduledRulePerActiveProject(AutomationRule rule, Instant now) {
    List<Project> activeProjects = projectRepository.findByStatus(ProjectStatus.ACTIVE);
    int fanned = 0;
    for (Project project : activeProjects) {
      Map<String, Map<String, Object>> context =
          AutomationContext.buildScheduledProjectContext(rule, project);
      if (!conditionEvaluator.evaluate(rule.getConditions(), context)) {
        continue;
      }
      automationEventListener.fireRule(rule, context);
      fanned++;
    }
    log.info(
        "Fired scheduled rule {} ({}) at {} across {} active project(s)",
        rule.getId(),
        rule.getName(),
        now,
        fanned);
  }

  /**
   * Returns {@code true} if any of the rule's actions targets a project-scoped context — either an
   * explicit {@code entityType=="project"} or an {@code entityId} template referencing {@code
   * {{project.}}}. Such rules need per-active-project fan-out; everything else fires once.
   */
  private boolean requiresProjectContext(AutomationRule rule) {
    // TODO(OBS-505): minor N+1 — one findByRuleIdOrderBySortOrder per due rule on each 60s tick.
    // Bounded today since SCHEDULED rules are rare; if SCHEDULED-rule volume grows this could batch
    // via the existing actionRepository.findByRuleIdInOrderBySortOrder(dueRuleIds). Not worth the
    // refactor at current scale.
    return actionRepository.findByRuleIdOrderBySortOrder(rule.getId()).stream()
        .anyMatch(action -> actionTargetsProject(action.getActionConfig()));
  }

  @SuppressWarnings("unchecked")
  private boolean actionTargetsProject(Map<String, Object> actionConfig) {
    if (actionConfig == null) {
      return false;
    }
    Object contextRefObj = actionConfig.get("contextRef");
    if (!(contextRefObj instanceof Map<?, ?> contextRef)) {
      return false;
    }
    Object entityType = ((Map<String, Object>) contextRef).get("entityType");
    if ("project".equals(entityType)) {
      return true;
    }
    Object entityId = ((Map<String, Object>) contextRef).get("entityId");
    return entityId instanceof String idTemplate && idTemplate.contains("{{project.");
  }
}
