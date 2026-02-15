package io.b2mash.b2b.b2bstrawman.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Translates custom field filters into JSONB-based SQL predicates. Supports operators: eq, neq, gt,
 * gte, lt, lte, contains, in.
 */
@Component
public class CustomFieldFilterHandler {

  private static final Set<String> NUMERIC_OPS = Set.of("gt", "gte", "lt", "lte");

  private static final Map<String, String> OP_MAP =
      Map.of(
          "eq", "=",
          "neq", "!=",
          "gt", ">",
          "gte", ">=",
          "lt", "<",
          "lte", "<=");

  /**
   * Builds SQL predicates for custom field filtering using JSONB operators.
   *
   * @param filterValue a Map of slug to {op, value} (e.g., {"court": {"op": "eq", "value":
   *     "high_court"}})
   * @param params the parameter map to populate with named bindings
   * @param entityType the entity type (unused for custom field filtering)
   * @return SQL predicate fragment, or empty string if filterValue is null/empty
   */
  @SuppressWarnings("unchecked")
  public String buildPredicate(Object filterValue, Map<String, Object> params, String entityType) {
    if (filterValue == null) {
      return "";
    }

    if (!(filterValue instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
      return "";
    }

    Map<String, Object> customFields = (Map<String, Object>) rawMap;
    List<String> clauses = new ArrayList<>();

    for (var entry : customFields.entrySet()) {
      String slug = entry.getKey();
      if (!(entry.getValue() instanceof Map<?, ?> opMap)) {
        continue;
      }

      String op = String.valueOf(opMap.get("op"));
      Object value = opMap.get("value");
      String paramName = "cf_" + slug;

      String clause = buildFieldClause(slug, op, value, paramName, params);
      if (!clause.isEmpty()) {
        clauses.add(clause);
      }
    }

    return String.join(" AND ", clauses);
  }

  private String buildFieldClause(
      String slug, String op, Object value, String paramName, Map<String, Object> params) {
    if ("contains".equals(op)) {
      params.put(paramName, String.valueOf(value));
      return "custom_fields ->> '" + slug + "' ILIKE '%' || :" + paramName + " || '%'";
    }

    if ("in".equals(op)) {
      if (value instanceof List<?> list) {
        List<String> stringValues = list.stream().map(Object::toString).toList();
        params.put(paramName, stringValues);
        return "custom_fields ->> '" + slug + "' IN (:" + paramName + ")";
      }
      return "";
    }

    String sqlOp = OP_MAP.get(op);
    if (sqlOp == null) {
      return "";
    }

    params.put(paramName, String.valueOf(value));

    if (NUMERIC_OPS.contains(op)) {
      return "(custom_fields ->> '" + slug + "')::numeric " + sqlOp + " :" + paramName;
    }

    return "custom_fields ->> '" + slug + "' " + sqlOp + " :" + paramName;
  }
}
