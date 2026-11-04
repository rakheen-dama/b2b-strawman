package io.b2mash.b2b.b2bstrawman.template;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Validates that all required context fields declared on a DocumentTemplate are present and
 * non-null in the assembled rendering context map.
 *
 * <p>Each required field entry has shape: {"entity": "project", "field": "name"}. The entity key
 * maps to a top-level key in the context map; the field key is a dotted path ({@code
 * customFields.seller_name}) resolved segment by segment through nested maps — a missing map, a
 * non-map segment, a null or a blank string is absent.
 */
@Service
public class TemplateValidationService {

  /**
   * Validates all required fields against the rendering context.
   *
   * @param requiredContextFields list of {entity, field} maps from the template; may be null/empty
   * @param context the assembled context map from a TemplateContextBuilder
   * @return validation result with per-field pass/fail status
   */
  public TemplateValidationResult validateRequiredFields(
      List<Map<String, String>> requiredContextFields, Map<String, Object> context) {

    if (requiredContextFields == null || requiredContextFields.isEmpty()) {
      return new TemplateValidationResult(true, List.of());
    }

    var fieldResults = new ArrayList<FieldValidationResult>();

    for (var entry : requiredContextFields) {
      String entity = entry.get("entity");
      String field = entry.get("field");

      if (entity == null || field == null) {
        // Malformed entry -- treat as missing
        fieldResults.add(
            new FieldValidationResult(entity, field, false, "Malformed required field entry"));
        continue;
      }

      var entityObj = context.get(entity);
      if (!(entityObj instanceof Map<?, ?> entityMap)) {
        fieldResults.add(
            new FieldValidationResult(entity, field, false, "Entity context not found: " + entity));
        continue;
      }

      Object value = resolvePath(entityMap, field);
      boolean present = value != null && !(value instanceof String s && s.isBlank());
      String reason = present ? null : "Field not populated: " + entity + "." + field;
      fieldResults.add(new FieldValidationResult(entity, field, present, reason));
    }

    boolean allPresent = fieldResults.stream().allMatch(FieldValidationResult::present);
    return new TemplateValidationResult(allPresent, List.copyOf(fieldResults));
  }

  /**
   * Walks a dotted path ({@code customFields.seller_name}) through nested maps; null when any
   * segment is absent, empty ({@code customFields.}, {@code .x}, {@code a..b}) or not a map. The
   * {@code -1} split limit keeps trailing empty segments so {@code customFields.} cannot resolve to
   * the {@code customFields} map itself.
   */
  private static Object resolvePath(Map<?, ?> root, String dottedPath) {
    Object current = root;
    for (String segment : dottedPath.split("\\.", -1)) {
      if (segment.isEmpty() || !(current instanceof Map<?, ?> map)) {
        return null;
      }
      current = map.get(segment);
      if (current == null) {
        return null;
      }
    }
    return current;
  }

  /** Result of validating all required context fields for a template. */
  public record TemplateValidationResult(boolean allPresent, List<FieldValidationResult> fields) {}

  /** Result for a single required field validation. */
  public record FieldValidationResult(
      String entity, String field, boolean present, String reason) {}
}
