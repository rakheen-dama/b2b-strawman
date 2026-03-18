package io.b2mash.b2b.b2bstrawman.automation;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.event.BudgetThresholdEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskStatusChangedEvent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link ConditionEvaluator} and {@link AutomationContext}. No Spring context
 * required — these are plain logic tests.
 */
class ConditionEvaluatorTest {

  private final ConditionEvaluator evaluator = new ConditionEvaluator();

  // --- Operator Tests ---

  @Test
  void equals_matchingValue_returnsTrue() {
    var context = contextWith("project", "status", "ACTIVE");
    var conditions = List.of(condition("project.status", "EQUALS", "ACTIVE"));

    assertThat(evaluator.evaluate(conditions, context)).isTrue();
  }

  @Test
  void equals_nonMatchingValue_returnsFalse() {
    var context = contextWith("project", "status", "ARCHIVED");
    var conditions = List.of(condition("project.status", "EQUALS", "ACTIVE"));

    assertThat(evaluator.evaluate(conditions, context)).isFalse();
  }

  @Test
  void notEquals_differentValue_returnsTrue() {
    var context = contextWith("task", "status", "OPEN");
    var conditions = List.of(condition("task.status", "NOT_EQUALS", "DONE"));

    assertThat(evaluator.evaluate(conditions, context)).isTrue();
  }

  @Test
  void in_valueInList_returnsTrue() {
    var context = contextWith("task", "status", "IN_PROGRESS");
    var conditions =
        List.of(condition("task.status", "IN", List.of("OPEN", "IN_PROGRESS", "REVIEW")));

    assertThat(evaluator.evaluate(conditions, context)).isTrue();
  }

  @Test
  void notIn_valueNotInList_returnsTrue() {
    var context = contextWith("task", "status", "DONE");
    var conditions = List.of(condition("task.status", "NOT_IN", List.of("OPEN", "IN_PROGRESS")));

    assertThat(evaluator.evaluate(conditions, context)).isTrue();
  }

  @Test
  void greaterThan_numericComparison_returnsTrue() {
    var context = contextWith("budget", "consumedPercent", 85.0);
    var conditions = List.of(condition("budget.consumedPercent", "GREATER_THAN", 80));

    assertThat(evaluator.evaluate(conditions, context)).isTrue();
  }

  @Test
  void lessThan_numericComparison_returnsTrue() {
    var context = contextWith("budget", "consumedPercent", 50.0);
    var conditions = List.of(condition("budget.consumedPercent", "LESS_THAN", 80));

    assertThat(evaluator.evaluate(conditions, context)).isTrue();
  }

  @Test
  void contains_substringMatch_returnsTrue() {
    var context = contextWith("task", "name", "Review client proposal");
    var conditions = List.of(condition("task.name", "CONTAINS", "client"));

    assertThat(evaluator.evaluate(conditions, context)).isTrue();
  }

  @Test
  void isNull_nullField_returnsTrue() {
    var context = contextWith("task", "assigneeId", null);
    var conditions = List.of(condition("task.assigneeId", "IS_NULL", null));

    assertThat(evaluator.evaluate(conditions, context)).isTrue();
  }

  @Test
  void isNotNull_nonNullField_returnsTrue() {
    var context = contextWith("task", "assigneeId", UUID.randomUUID().toString());
    var conditions = List.of(condition("task.assigneeId", "IS_NOT_NULL", null));

    assertThat(evaluator.evaluate(conditions, context)).isTrue();
  }

  // --- Fail-Safe Tests ---

  @Test
  void unknownField_returnsFalse() {
    var context = contextWith("task", "status", "OPEN");
    var conditions = List.of(condition("nonexistent.field", "EQUALS", "anything"));

    assertThat(evaluator.evaluate(conditions, context)).isFalse();
  }

  @Test
  void unknownField_isNull_returnsTrue() {
    var context = contextWith("task", "status", "OPEN");
    var conditions = List.of(condition("nonexistent.field", "IS_NULL", null));

    assertThat(evaluator.evaluate(conditions, context)).isTrue();
  }

  @Test
  void notIn_nullResolved_returnsTrue() {
    var context = contextWith("task", "assigneeId", null);
    var conditions =
        List.of(condition("task.assigneeId", "NOT_IN", List.of("member-1", "member-2")));

    assertThat(evaluator.evaluate(conditions, context)).isTrue();
  }

  @Test
  void nullOperator_returnsFalse() {
    var context = contextWith("task", "status", "OPEN");
    var conditions = List.of(condition("task.status", null, "OPEN"));

    assertThat(evaluator.evaluate(conditions, context)).isFalse();
  }

  @Test
  void greaterThan_nonNumericValue_returnsFalse() {
    var context = contextWith("task", "status", "OPEN");
    var conditions = List.of(condition("task.status", "GREATER_THAN", 10));

    assertThat(evaluator.evaluate(conditions, context)).isFalse();
  }

  // --- AND Logic Tests ---

  @Test
  void emptyConditions_returnsTrue() {
    var context = contextWith("task", "status", "OPEN");

    assertThat(evaluator.evaluate(List.of(), context)).isTrue();
    assertThat(evaluator.evaluate(null, context)).isTrue();
  }

  @Test
  void multipleConditions_allTrue_returnsTrue() {
    var context = new LinkedHashMap<String, Map<String, Object>>();
    context.put("task", Map.of("status", "DONE", "assigneeId", "member-1"));
    context.put("project", Map.of("status", "ACTIVE"));

    var conditions =
        List.of(
            condition("task.status", "EQUALS", "DONE"),
            condition("project.status", "EQUALS", "ACTIVE"),
            condition("task.assigneeId", "IS_NOT_NULL", null));

    assertThat(evaluator.evaluate(conditions, context)).isTrue();
  }

  @Test
  void multipleConditions_oneFalse_returnsFalse() {
    var context = new LinkedHashMap<String, Map<String, Object>>();
    context.put("task", Map.of("status", "DONE", "assigneeId", "member-1"));
    context.put("project", Map.of("status", "ARCHIVED"));

    var conditions =
        List.of(
            condition("task.status", "EQUALS", "DONE"),
            condition("project.status", "EQUALS", "ACTIVE")); // This fails

    assertThat(evaluator.evaluate(conditions, context)).isFalse();
  }

  // --- AutomationContext Builder Tests ---

  @Test
  void contextBuilder_taskStatusChanged_populatesAllSections() {
    UUID taskId = UUID.randomUUID();
    UUID projectId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    UUID assigneeId = UUID.randomUUID();

    var details = new LinkedHashMap<String, Object>();
    details.put("project_name", "Test Project");
    details.put("customer_id", UUID.randomUUID().toString());
    details.put("customer_name", "Acme Corp");

    var event =
        new TaskStatusChangedEvent(
            "TASK_STATUS_CHANGED",
            "Task",
            taskId,
            projectId,
            actorId,
            "Alice",
            "tenant_test",
            "org_test",
            Instant.now(),
            details,
            "OPEN",
            "DONE",
            assigneeId,
            "Fix the widget",
            null);

    var rule = createTestRule("Auto-close task", TriggerType.TASK_STATUS_CHANGED);

    var context = AutomationContext.build(TriggerType.TASK_STATUS_CHANGED, event, rule);

    // Verify task section
    assertThat(context.get("task")).containsEntry("id", taskId.toString());
    assertThat(context.get("task")).containsEntry("name", "Fix the widget");
    assertThat(context.get("task")).containsEntry("status", "DONE");
    assertThat(context.get("task")).containsEntry("previousStatus", "OPEN");
    assertThat(context.get("task")).containsEntry("assigneeId", assigneeId.toString());
    assertThat(context.get("task")).containsEntry("projectId", projectId.toString());

    // Verify project section
    assertThat(context.get("project")).containsEntry("id", projectId.toString());
    assertThat(context.get("project")).containsEntry("name", "Test Project");

    // Verify customer section
    assertThat(context.get("customer")).containsEntry("name", "Acme Corp");

    // Verify actor section
    assertThat(context.get("actor")).containsEntry("id", actorId.toString());
    assertThat(context.get("actor")).containsEntry("name", "Alice");

    // Verify rule section (ID is null for unpersisted rules — JPA generates on save)
    assertThat(context.get("rule")).containsKey("id");
    assertThat(context.get("rule")).containsEntry("name", "Auto-close task");
  }

  @Test
  void contextBuilder_budgetThresholdReached_populatesAllSections() {
    UUID projectId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();

    var details = new LinkedHashMap<String, Object>();
    details.put("project_name", "Budget Project");
    details.put("consumed_pct", 90);
    details.put("threshold_pct", 80);
    details.put("dimension", "HOURS");

    var event =
        new BudgetThresholdEvent(
            "BUDGET_THRESHOLD_REACHED",
            "ProjectBudget",
            UUID.randomUUID(),
            projectId,
            actorId,
            "System",
            "tenant_test",
            "org_test",
            Instant.now(),
            details);

    var rule = createTestRule("Budget alert", TriggerType.BUDGET_THRESHOLD_REACHED);

    var context = AutomationContext.build(TriggerType.BUDGET_THRESHOLD_REACHED, event, rule);

    // Verify budget section
    assertThat(context.get("budget")).containsEntry("projectId", projectId.toString());
    assertThat(context.get("budget")).containsEntry("consumedPercent", 90);
    assertThat(context.get("budget")).containsEntry("thresholdPercent", 80);
    assertThat(context.get("budget")).containsEntry("dimension", "HOURS");

    // Verify project section
    assertThat(context.get("project")).containsEntry("id", projectId.toString());
    assertThat(context.get("project")).containsEntry("name", "Budget Project");

    // Verify actor and rule
    assertThat(context.get("actor")).containsEntry("name", "System");
    assertThat(context.get("rule")).containsEntry("name", "Budget alert");
  }

  // --- Helpers ---

  private Map<String, Object> condition(String field, String operator, Object value) {
    var map = new LinkedHashMap<String, Object>();
    map.put("field", field);
    map.put("operator", operator);
    map.put("value", value);
    return map;
  }

  private Map<String, Map<String, Object>> contextWith(String entity, String field, Object value) {
    var context = new LinkedHashMap<String, Map<String, Object>>();
    var entityMap = new LinkedHashMap<String, Object>();
    entityMap.put(field, value);
    context.put(entity, entityMap);
    return context;
  }

  private AutomationRule createTestRule(String name, TriggerType triggerType) {
    return new AutomationRule(
        name, "Test rule", triggerType, Map.of(), null, RuleSource.CUSTOM, null, UUID.randomUUID());
  }
}
