package io.b2mash.b2b.b2bstrawman.automation;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Resolves {@code {{variable}}} placeholders in text templates using dot-notation lookup against an
 * automation context map. Unresolved variables are left as-is (safe — no blanks or errors).
 */
@Component
public class VariableResolver {

  private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

  /**
   * Resolves all {@code {{entity.field}}} placeholders in the template string.
   *
   * @param template the text containing variable placeholders
   * @param context the automation context (entity key -> field map)
   * @return the resolved string, or null if template is null
   */
  public String resolve(String template, Map<String, Map<String, Object>> context) {
    if (template == null) {
      return null;
    }
    if (context == null || context.isEmpty()) {
      return template;
    }

    Matcher matcher = VARIABLE_PATTERN.matcher(template);
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      String variablePath = matcher.group(1).trim();
      String replacement = resolveField(variablePath, context);
      matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  private String resolveField(String fieldPath, Map<String, Map<String, Object>> context) {
    if (fieldPath == null || !fieldPath.contains(".")) {
      return "{{" + fieldPath + "}}";
    }

    String[] parts = fieldPath.split("\\.", 2);
    String entityKey = parts[0];
    String fieldKey = parts[1];

    Map<String, Object> entityMap = context.get(entityKey);
    if (entityMap == null) {
      return "{{" + fieldPath + "}}";
    }

    Object value = entityMap.get(fieldKey);
    if (value == null) {
      return "{{" + fieldPath + "}}";
    }

    return value.toString();
  }

  /**
   * Resolves a UUID value from the automation context by entity key and field key. Handles both
   * {@link UUID} instances and string representations.
   *
   * @param context the automation context map
   * @param entityKey the top-level entity key (e.g. "task", "project", "actor")
   * @param fieldKey the field within the entity map (e.g. "id", "projectId")
   * @return the resolved UUID, or null if not found or unparseable
   */
  public static UUID resolveUuid(
      Map<String, Map<String, Object>> context, String entityKey, String fieldKey) {
    Map<String, Object> entityMap = context.get(entityKey);
    if (entityMap == null) {
      return null;
    }
    Object value = entityMap.get(fieldKey);
    if (value == null) {
      return null;
    }
    if (value instanceof UUID uuid) {
      return uuid;
    }
    try {
      return UUID.fromString(value.toString());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
