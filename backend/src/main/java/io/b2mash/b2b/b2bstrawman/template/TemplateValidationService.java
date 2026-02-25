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
 * maps to a top-level key in the context map; the field key maps to a nested key in that sub-map.
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
  @SuppressWarnings("unchecked")
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

      var value = ((Map<String, Object>) entityMap).get(field);
      boolean present = value != null && !(value instanceof String s && s.isBlank());
      String reason = present ? null : "Field not populated: " + entity + "." + field;
      fieldResults.add(new FieldValidationResult(entity, field, present, reason));
    }

    boolean allPresent = fieldResults.stream().allMatch(FieldValidationResult::present);
    return new TemplateValidationResult(allPresent, List.copyOf(fieldResults));
  }

  /** Result of validating all required context fields for a template. */
  public record TemplateValidationResult(boolean allPresent, List<FieldValidationResult> fields) {}

  /** Result for a single required field validation. */
  public record FieldValidationResult(
      String entity, String field, boolean present, String reason) {}
}
