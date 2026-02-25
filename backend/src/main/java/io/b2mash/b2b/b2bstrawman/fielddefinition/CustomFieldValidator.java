package io.b2mash.b2b.b2bstrawman.fielddefinition;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomFieldValidator {

  private final FieldDefinitionRepository fieldDefinitionRepository;
  private final FieldGroupMemberRepository fieldGroupMemberRepository;

  public CustomFieldValidator(
      FieldDefinitionRepository fieldDefinitionRepository,
      FieldGroupMemberRepository fieldGroupMemberRepository) {
    this.fieldDefinitionRepository = fieldDefinitionRepository;
    this.fieldGroupMemberRepository = fieldGroupMemberRepository;
  }

  /**
   * Validates custom field values against active field definitions.
   *
   * <p>Steps per architecture doc Section 11.3.3:
   *
   * <ol>
   *   <li>Load active FieldDefinitions for the entityType
   *   <li>Strip unknown keys (keys with no matching active field definition)
   *   <li>Type-check each value against fieldType
   *   <li>Apply validation rules (min/max, pattern, minLength, maxLength)
   *   <li>Check required fields in applied groups
   * </ol>
   *
   * <p>Returns a validated Map with unknown keys stripped. Throws InvalidStateException with
   * field-level errors for bad input.
   */
  @Transactional(readOnly = true)
  public Map<String, Object> validate(
      EntityType entityType, Map<String, Object> input, List<UUID> appliedGroupIds) {

    if (input == null || input.isEmpty()) {
      // Check required fields even if input is empty
      checkRequiredFields(entityType, Map.of(), appliedGroupIds);
      return new HashMap<>();
    }

    // 1. Load active FieldDefinitions for the entityType
    var activeDefinitions =
        fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(entityType);
    var slugToDefinition =
        activeDefinitions.stream()
            .collect(Collectors.toMap(FieldDefinition::getSlug, fd -> fd, (a, b) -> a));

    // 2. Strip unknown keys
    var validated = new HashMap<String, Object>();
    var errors = new ArrayList<Map<String, String>>();

    for (var entry : input.entrySet()) {
      String slug = entry.getKey();
      Object value = entry.getValue();

      var definition = slugToDefinition.get(slug);
      if (definition == null) {
        // Unknown key - strip it silently
        continue;
      }

      // 3. Type-check and validate
      if (value == null) {
        validated.put(slug, null);
        continue;
      }

      // Hidden fields: still type-validate if a value is provided, but skip required check later
      String error = validateFieldValue(definition, value);
      if (error != null) {
        errors.add(Map.of("field", slug, "message", error));
      } else {
        validated.put(slug, value);
      }
    }

    if (!errors.isEmpty()) {
      throw new InvalidStateException("Custom field validation failed", errors.toString());
    }

    // 5. Check required fields in applied groups
    checkRequiredFields(entityType, validated, appliedGroupIds);

    return validated;
  }

  private String validateFieldValue(FieldDefinition definition, Object value) {
    return switch (definition.getFieldType()) {
      case TEXT -> validateText(definition, value);
      case NUMBER -> validateNumber(definition, value);
      case DATE -> validateDate(definition, value);
      case DROPDOWN -> validateDropdown(definition, value);
      case BOOLEAN -> validateBoolean(value);
      case CURRENCY -> validateCurrency(value);
      case URL -> validateUrl(value);
      case EMAIL -> validateEmail(value);
      case PHONE -> validatePhone(value);
    };
  }

  private String validateText(FieldDefinition definition, Object value) {
    if (!(value instanceof String str)) {
      return "Expected a text value";
    }

    var validation = definition.getValidation();
    if (validation != null) {
      var minLengthVal = validation.get("minLength");
      if (minLengthVal != null) {
        int minLength = toInt(minLengthVal);
        if (str.length() < minLength) {
          return "Text must be at least " + minLength + " characters";
        }
      }

      var maxLengthVal = validation.get("maxLength");
      if (maxLengthVal != null) {
        int maxLength = toInt(maxLengthVal);
        if (str.length() > maxLength) {
          return "Text must be at most " + maxLength + " characters";
        }
      }

      var patternVal = validation.get("pattern");
      if (patternVal instanceof String patternStr) {
        try {
          if (!Pattern.matches(patternStr, str)) {
            return "Text does not match required pattern";
          }
        } catch (PatternSyntaxException e) {
          // Invalid pattern in definition — skip pattern validation
        }
      }
    }

    return null;
  }

  private String validateNumber(FieldDefinition definition, Object value) {
    BigDecimal number;
    if (value instanceof Number num) {
      number = new BigDecimal(num.toString());
    } else {
      return "Expected a numeric value";
    }

    var validation = definition.getValidation();
    if (validation != null) {
      var minVal = validation.get("min");
      if (minVal != null) {
        BigDecimal min = new BigDecimal(minVal.toString());
        if (number.compareTo(min) < 0) {
          return "Value must be at least " + min;
        }
      }

      var maxVal = validation.get("max");
      if (maxVal != null) {
        BigDecimal max = new BigDecimal(maxVal.toString());
        if (number.compareTo(max) > 0) {
          return "Value must be at most " + max;
        }
      }
    }

    return null;
  }

  private String validateDate(FieldDefinition definition, Object value) {
    if (!(value instanceof String str)) {
      return "Expected a date string in YYYY-MM-DD format";
    }
    try {
      LocalDate.parse(str);
    } catch (DateTimeParseException e) {
      return "Invalid date format, expected YYYY-MM-DD";
    }

    var validation = definition.getValidation();
    if (validation != null) {
      var minVal = validation.get("min");
      if (minVal instanceof String minStr && !minStr.isBlank()) {
        if (str.compareTo(minStr) < 0) {
          return "Date must be on or after " + minStr;
        }
      }

      var maxVal = validation.get("max");
      if (maxVal instanceof String maxStr && !maxStr.isBlank()) {
        if (str.compareTo(maxStr) > 0) {
          return "Date must be on or before " + maxStr;
        }
      }
    }

    return null;
  }

  private String validateDropdown(FieldDefinition definition, Object value) {
    if (!(value instanceof String str)) {
      return "Expected a string value for dropdown";
    }

    var options = definition.getOptions();
    if (options != null && !options.isEmpty()) {
      boolean valid = options.stream().anyMatch(opt -> str.equals(opt.get("value")));
      if (!valid) {
        return "Value '" + str + "' is not a valid option";
      }
    }

    return null;
  }

  private String validateBoolean(Object value) {
    if (!(value instanceof Boolean)) {
      return "Expected a boolean value (true or false)";
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private String validateCurrency(Object value) {
    if (!(value instanceof Map<?, ?> map)) {
      return "Expected an object with 'amount' and 'currency' fields";
    }

    var amount = map.get("amount");
    var currency = map.get("currency");

    if (amount == null || currency == null) {
      return "Currency field requires both 'amount' and 'currency'";
    }

    if (!(amount instanceof Number)) {
      return "'amount' must be a number";
    }

    if (!(currency instanceof String currencyStr) || currencyStr.length() != 3) {
      return "'currency' must be a 3-letter ISO code";
    }

    return null;
  }

  private String validateUrl(Object value) {
    if (!(value instanceof String str)) {
      return "Expected a URL string";
    }
    if (!str.startsWith("http://") && !str.startsWith("https://")) {
      return "URL must start with http:// or https://";
    }
    return null;
  }

  private String validateEmail(Object value) {
    if (!(value instanceof String str)) {
      return "Expected an email string";
    }
    // Basic email validation: must contain @ with text before and after
    if (!str.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
      return "Invalid email format";
    }
    return null;
  }

  private String validatePhone(Object value) {
    if (!(value instanceof String str)) {
      return "Expected a phone string";
    }
    if (str.isBlank()) {
      return "Phone number must not be blank";
    }
    return null;
  }

  private void checkRequiredFields(
      EntityType entityType, Map<String, Object> validated, List<UUID> appliedGroupIds) {
    if (appliedGroupIds == null || appliedGroupIds.isEmpty()) {
      return;
    }

    // Collect all field definition IDs from applied groups
    Set<UUID> fieldDefIdsInGroups = new HashSet<>();
    for (UUID groupId : appliedGroupIds) {
      var members = fieldGroupMemberRepository.findByFieldGroupIdOrderBySortOrder(groupId);
      for (var member : members) {
        fieldDefIdsInGroups.add(member.getFieldDefinitionId());
      }
    }

    // Load active definitions
    var activeDefinitions =
        fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(entityType);
    var slugToDefinition =
        activeDefinitions.stream()
            .collect(Collectors.toMap(FieldDefinition::getSlug, fd -> fd, (a, b) -> a));

    var errors = new ArrayList<Map<String, String>>();
    for (var def : activeDefinitions) {
      if (def.isRequired() && fieldDefIdsInGroups.contains(def.getId())) {
        // Skip required check for hidden fields
        if (!isFieldVisible(def, validated, slugToDefinition)) {
          continue;
        }
        Object value = validated.get(def.getSlug());
        if (value == null) {
          errors.add(Map.of("field", def.getSlug(), "message", "Required field is missing"));
        }
      }
    }

    if (!errors.isEmpty()) {
      throw new InvalidStateException("Custom field validation failed", errors.toString());
    }
  }

  /**
   * Evaluates whether a field should be visible based on its visibility condition.
   *
   * <p>Rules:
   *
   * <ul>
   *   <li>No condition (null) = always visible
   *   <li>Controlling field not found in active definitions = visible (safe default)
   *   <li>Controlling field value is null = hidden
   *   <li>Unknown operator = visible (safe default)
   *   <li>Operators: eq, neq, in
   * </ul>
   */
  @SuppressWarnings("unchecked")
  private boolean isFieldVisible(
      FieldDefinition definition,
      Map<String, Object> allValues,
      Map<String, FieldDefinition> slugToDefinition) {
    var condition = definition.getVisibilityCondition();
    if (condition == null) {
      return true;
    }

    var dependsOnSlug = condition.get("dependsOnSlug");
    if (!(dependsOnSlug instanceof String controllingSlug)) {
      return true;
    }

    // If the controlling field is not an active field definition, show the dependent field
    if (!slugToDefinition.containsKey(controllingSlug)) {
      return true;
    }

    Object actualValue = allValues.get(controllingSlug);
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
      default -> true;
    };
  }

  private int toInt(Object value) {
    if (value instanceof Number num) {
      return num.intValue();
    }
    return Integer.parseInt(value.toString());
  }
}
