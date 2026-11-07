package io.b2mash.b2b.b2bstrawman.template;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 *
 * <p>Each result also carries a readable {@code label} (G89 632A(b)) — the entity's legal noun and
 * the field's display name, e.g. "Matter › Seller Name" — so the warning never shows the raw path.
 * Display names come from the caller's variable labels (the variable registry: field definition
 * names for custom fields, the curated labels for core fields); a path the registry does not know
 * falls back to its last segment, humanised ({@code opposing_party} → "Opposing party").
 */
@Service
public class TemplateValidationService {

  /** Separator between the entity noun and the field name in a label. */
  static final String LABEL_SEPARATOR = " › ";

  /** The legal noun of each context entity key (ADR-357 single vocabulary). */
  private static final Map<String, String> ENTITY_NOUNS =
      Map.of(
          "project", "Matter",
          "customer", "Client",
          "invoice", "Fee note",
          "org", "Firm",
          "task", "Task");

  /** {@link #validateRequiredFields(List, Map, Map)} without registry labels (fallback labels). */
  public TemplateValidationResult validateRequiredFields(
      List<Map<String, String>> requiredContextFields, Map<String, Object> context) {
    return validateRequiredFields(requiredContextFields, context, Map.of());
  }

  /**
   * Validates all required fields against the rendering context.
   *
   * @param requiredContextFields list of {entity, field} maps from the template; may be null/empty
   * @param context the assembled context map from a TemplateContextBuilder
   * @param variableLabels variable key ({@code project.customFields.seller_name}) → display name,
   *     e.g. {@link VariableMetadataRegistry#labelsFor}; may be empty
   * @return validation result with per-field pass/fail status and a readable label
   */
  public TemplateValidationResult validateRequiredFields(
      List<Map<String, String>> requiredContextFields,
      Map<String, Object> context,
      Map<String, String> variableLabels) {

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
            new FieldValidationResult(
                entity, field, false, "Malformed required field entry", null));
        continue;
      }

      String label = label(entity, field, variableLabels);
      var entityObj = context.get(entity);
      if (!(entityObj instanceof Map<?, ?> entityMap)) {
        fieldResults.add(
            new FieldValidationResult(
                entity, field, false, "Entity context not found: " + entity, label));
        continue;
      }

      Object value = resolvePath(entityMap, field);
      boolean present = value != null && !(value instanceof String s && s.isBlank());
      String reason = present ? null : "Field not populated: " + entity + "." + field;
      fieldResults.add(new FieldValidationResult(entity, field, present, reason, label));
    }

    boolean allPresent = fieldResults.stream().allMatch(FieldValidationResult::present);
    return new TemplateValidationResult(allPresent, List.copyOf(fieldResults));
  }

  /** "Matter › Seller Name": the entity's legal noun, then the field's display name. */
  static String label(String entity, String field, Map<String, String> variableLabels) {
    String noun = ENTITY_NOUNS.getOrDefault(entity, humanise(entity));
    String name = variableLabels == null ? null : variableLabels.get(entity + "." + field);
    if (name == null || name.isBlank()) {
      String[] segments = field.split("\\.");
      name = humanise(segments.length == 0 ? field : segments[segments.length - 1]);
    }
    return noun + LABEL_SEPARATOR + name;
  }

  /** {@code opposing_party} / {@code opposingParty} → "Opposing party". */
  private static String humanise(String key) {
    String words =
        key.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
            .replace('_', ' ')
            .trim()
            .toLowerCase(Locale.ROOT);
    if (words.isEmpty()) {
      return key;
    }
    return Character.toUpperCase(words.charAt(0)) + words.substring(1);
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

  /**
   * Result for a single required field validation. {@code label} is the readable field name
   * ("Matter › Seller Name"); null only for a malformed entry.
   */
  public record FieldValidationResult(
      String entity, String field, boolean present, String reason, String label) {}
}
