package io.b2mash.b2b.b2bstrawman.fielddefinition;

import java.util.List;
import java.util.Map;

/** Shared utility methods for custom field value inspection. */
public final class CustomFieldUtils {

  private CustomFieldUtils() {}

  /**
   * Determines whether a custom field value is considered "filled" (non-blank). CURRENCY fields
   * require both a non-null {@code amount} and a non-blank {@code currency} code inside the map.
   * All other field types fall back to a simple {@code toString().isBlank()} check.
   */
  public static boolean isFieldValueFilled(FieldDefinition fd, Object value) {
    if (value == null) {
      return false;
    }
    if (fd.getFieldType() == FieldType.CURRENCY) {
      if (!(value instanceof Map<?, ?> map)) {
        return false;
      }
      var amount = map.get("amount");
      var currency = map.get("currency");
      return amount != null && currency != null && !currency.toString().isBlank();
    }
    return !value.toString().isBlank();
  }

  /**
   * OBS-5004: evaluates whether a field should be visible based on its visibility condition and the
   * actual field values available. Extracted from {@code CustomFieldValidator.isFieldVisible()} for
   * reuse in completeness scoring and readiness computations.
   *
   * <p>Rules:
   *
   * <ul>
   *   <li>No condition (null) = always visible
   *   <li>Controlling field value is null = hidden
   *   <li>Unknown operator = visible (safe default)
   *   <li>Operators: eq, neq, in, isSet
   * </ul>
   *
   * @param definition the field definition to check
   * @param allValues map of all field values (custom fields + promoted structural values)
   */
  @SuppressWarnings("unchecked")
  public static boolean isFieldVisible(FieldDefinition definition, Map<String, Object> allValues) {
    var condition = definition.getVisibilityCondition();
    if (condition == null) {
      return true;
    }

    var dependsOnSlug = condition.get("dependsOnSlug");
    if (!(dependsOnSlug instanceof String controllingSlug)) {
      return true;
    }

    Object actualValue = allValues != null ? allValues.get(controllingSlug) : null;
    if (actualValue == null) {
      return false;
    }

    var operator = condition.get("operator");
    if (!(operator instanceof String op)) {
      return true;
    }

    var expectedValue = condition.get("value");

    return switch (op) {
      case "eq" -> actualValue.equals(expectedValue);
      case "neq" -> !actualValue.equals(expectedValue);
      case "in" -> {
        if (expectedValue instanceof List<?> list) {
          yield list.contains(actualValue);
        }
        yield true;
      }
      case "isSet" -> true;
      default -> true;
    };
  }
}
