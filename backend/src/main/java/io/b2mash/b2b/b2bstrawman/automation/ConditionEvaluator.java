package io.b2mash.b2b.b2bstrawman.automation;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Evaluates automation rule conditions against a structured context map. Supports dot-notation
 * field resolution (e.g., {@code "project.status"}) and nine comparison operators.
 *
 * <p>All conditions use AND logic — every condition must be satisfied for the result to be {@code
 * true}. An empty or null condition list evaluates to {@code true} (no conditions = unconditional).
 *
 * <p>Fail-safe behavior: unknown field paths resolve to {@code null}, and type mismatches (e.g.,
 * {@code GREATER_THAN} on a non-numeric value) return {@code false}. Warnings are logged for
 * unresolvable fields.
 */
@Component
public class ConditionEvaluator {

  private static final Logger log = LoggerFactory.getLogger(ConditionEvaluator.class);

  /**
   * Evaluates all conditions against the provided context using AND logic.
   *
   * @param conditions list of condition maps, each with "field", "operator", and "value" keys
   * @param context nested map keyed by entity name, containing entity field maps
   * @return {@code true} if all conditions are met or if conditions is null/empty
   */
  public boolean evaluate(
      List<Map<String, Object>> conditions, Map<String, Map<String, Object>> context) {
    if (conditions == null || conditions.isEmpty()) {
      return true;
    }
    return conditions.stream().allMatch(condition -> evaluateCondition(condition, context));
  }

  private boolean evaluateCondition(
      Map<String, Object> condition, Map<String, Map<String, Object>> context) {
    String field = (String) condition.get("field");
    String operatorStr = (String) condition.get("operator");
    Object value = condition.get("value");

    ConditionOperator operator;
    try {
      operator = ConditionOperator.valueOf(operatorStr);
    } catch (IllegalArgumentException e) {
      log.warn("Unknown condition operator '{}', evaluating as false", operatorStr);
      return false;
    }

    Object resolved = resolveField(field, context);
    return applyOperator(operator, resolved, value);
  }

  private Object resolveField(String fieldPath, Map<String, Map<String, Object>> context) {
    if (fieldPath == null || !fieldPath.contains(".")) {
      log.warn("Invalid field path '{}' — expected dot-notation (e.g., 'entity.field')", fieldPath);
      return null;
    }

    String[] parts = fieldPath.split("\\.", 2);
    String entityKey = parts[0];
    String fieldKey = parts[1];

    Map<String, Object> entityMap = context.get(entityKey);
    if (entityMap == null) {
      log.warn(
          "Unknown entity '{}' in field path '{}' — context has keys: {}",
          entityKey,
          fieldPath,
          context.keySet());
      return null;
    }

    if (!entityMap.containsKey(fieldKey)) {
      log.warn(
          "Unknown field '{}' in entity '{}' — available fields: {}",
          fieldKey,
          entityKey,
          entityMap.keySet());
      return null;
    }

    return entityMap.get(fieldKey);
  }

  @SuppressWarnings("unchecked")
  private boolean applyOperator(ConditionOperator operator, Object resolved, Object value) {
    return switch (operator) {
      case EQUALS -> equalsComparison(resolved, value);
      case NOT_EQUALS -> !equalsComparison(resolved, value);
      case IN -> {
        if (resolved == null || !(value instanceof Collection<?> collection)) {
          yield false;
        }
        yield collection.contains(resolved) || containsToString(collection, resolved);
      }
      case NOT_IN -> {
        if (resolved == null || !(value instanceof Collection<?> collection)) {
          yield false;
        }
        yield !collection.contains(resolved) && !containsToString(collection, resolved);
      }
      case GREATER_THAN -> numericComparison(resolved, value) > 0;
      case LESS_THAN -> numericComparison(resolved, value) < 0;
      case CONTAINS -> {
        if (resolved == null || value == null) {
          yield false;
        }
        yield resolved.toString().contains(value.toString());
      }
      case IS_NULL -> resolved == null;
      case IS_NOT_NULL -> resolved != null;
    };
  }

  private boolean equalsComparison(Object resolved, Object value) {
    if (Objects.equals(resolved, value)) {
      return true;
    }
    // Fall back to toString comparison for mixed types (e.g., UUID vs String)
    if (resolved != null && value != null) {
      return resolved.toString().equals(value.toString());
    }
    return false;
  }

  private boolean containsToString(Collection<?> collection, Object resolved) {
    String resolvedStr = resolved.toString();
    return collection.stream()
        .anyMatch(item -> item != null && item.toString().equals(resolvedStr));
  }

  /**
   * Compares two values numerically. Returns positive if resolved > value, negative if resolved <
   * value, zero if equal. Returns 0 (false for both GT and LT) on type mismatch.
   */
  private int numericComparison(Object resolved, Object value) {
    try {
      double resolvedNum = toDouble(resolved);
      double valueNum = toDouble(value);
      return Double.compare(resolvedNum, valueNum);
    } catch (NumberFormatException | NullPointerException e) {
      return 0;
    }
  }

  private double toDouble(Object obj) {
    if (obj instanceof Number number) {
      return number.doubleValue();
    }
    if (obj != null) {
      return Double.parseDouble(obj.toString());
    }
    throw new NullPointerException("Cannot convert null to double");
  }
}
