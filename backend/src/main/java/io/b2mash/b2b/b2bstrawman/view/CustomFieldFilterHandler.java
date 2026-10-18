package io.b2mash.b2b.b2bstrawman.view;

import io.b2mash.b2b.b2bstrawman.worktype.WorkTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Translates custom field filters into SQL predicates. For promoted fields (entity columns),
 * generates direct column-based WHERE clauses. For non-promoted fields, generates JSONB-based SQL
 * predicates. Supports operators: eq, neq, gt, gte, lt, lte, contains, in.
 */
@Component
public class CustomFieldFilterHandler {

  /** Only alphanumeric, underscore, and hyphen are allowed in custom field slugs. */
  private static final Pattern SAFE_SLUG = Pattern.compile("^[a-zA-Z0-9_-]+$");

  private static final Set<String> NUMERIC_OPS = Set.of("gt", "gte", "lt", "lte");

  /** The promoted {@code projects.work_type} slug, stored in canonical {@link WorkTypes} form. */
  private static final String WORK_TYPE_SLUG = "work_type";

  private static final Map<String, String> OP_MAP =
      Map.of(
          "eq", "=",
          "neq", "!=",
          "gt", ">",
          "gte", ">=",
          "lt", "<",
          "lte", "<=");

  /**
   * Builds SQL predicates for custom field filtering. For promoted field slugs, generates
   * column-based WHERE clauses (e.g., {@code city = :cf_city}) <em>only when the slug is promoted
   * on the target {@code entityType}</em>; otherwise generates JSONB-based access expressions
   * (e.g., {@code custom_fields ->> 'court' = :cf_court}). This guards against cross-entity leak
   * where e.g. filtering {@code city} on a PROJECT view would otherwise emit a broken {@code
   * projects.city} clause.
   *
   * @param filterValue a Map of slug to {op, value} (e.g., {"court": {"op": "eq", "value":
   *     "high_court"}})
   * @param params the parameter map to populate with named bindings
   * @param entityType the entity type of the view being filtered (e.g. "CUSTOMER", "PROJECT")
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

      String clause = buildFieldClause(slug, op, value, paramName, params, entityType);
      if (!clause.isEmpty()) {
        clauses.add(clause);
      }
    }

    return String.join(" AND ", clauses);
  }

  private String buildFieldClause(
      String slug,
      String op,
      Object value,
      String paramName,
      Map<String, Object> params,
      String entityType) {
    // Validate slug to prevent SQL injection -- slug is concatenated into expressions
    if (!SAFE_SLUG.matcher(slug).matches()) {
      return "";
    }
    // Sanitize paramName (derived from slug) for use as a named parameter.
    // Hyphens are replaced with underscores to ensure valid parameter names.
    paramName = "cf_" + slug.replace('-', '_');

    // Entity-scoped: only treat as a column when the slug is promoted for THIS entity type.
    // Cross-entity slugs (e.g. "city" on a PROJECT view) stay in JSONB to avoid emitting a
    // reference to a column that does not exist on the target table.
    boolean isPromoted = CustomFieldFilterUtil.isPromotedForEntity(slug, entityType);

    // projects.work_type holds the canonical form (CONVEYANCING), but view operands are whatever
    // the user typed ("Conveyancing"). Normalise eq/neq/in/contains operands at query time so they
    // still match; this also covers saved views stored before canonicalisation, so
    // saved_views.filters needs no data migration. contains keeps ILIKE for substring matching —
    // normalising it adds the space → underscore step ILIKE can't do ("personal inj").
    boolean canonicalWorkType = isPromoted && WORK_TYPE_SLUG.equals(slug);

    if ("contains".equals(op)) {
      String raw = String.valueOf(value);
      params.put(paramName, canonicalWorkType ? canonicalWorkTypeOperand(raw) : raw);
      if (isPromoted) {
        return slug + " ILIKE '%' || :" + paramName + " || '%'";
      }
      return "custom_fields ->> '" + slug + "' ILIKE '%' || :" + paramName + " || '%'";
    }

    if ("in".equals(op)) {
      if (value instanceof List<?> list) {
        List<String> stringValues =
            list.stream()
                .map(Object::toString)
                .map(v -> canonicalWorkType ? canonicalWorkTypeOperand(v) : v)
                .toList();
        params.put(paramName, stringValues);
        if (isPromoted) {
          return slug + " IN (:" + paramName + ")";
        }
        return "custom_fields ->> '" + slug + "' IN (:" + paramName + ")";
      }
      return "";
    }

    String sqlOp = OP_MAP.get(op);
    if (sqlOp == null) {
      return "";
    }

    String operand = String.valueOf(value);
    params.put(
        paramName,
        canonicalWorkType && !NUMERIC_OPS.contains(op)
            ? canonicalWorkTypeOperand(operand)
            : operand);

    if (isPromoted) {
      if (NUMERIC_OPS.contains(op)) {
        return slug + "::numeric " + sqlOp + " :" + paramName;
      }
      return slug + " " + sqlOp + " :" + paramName;
    }

    if (NUMERIC_OPS.contains(op)) {
      return "(custom_fields ->> '" + slug + "')::numeric " + sqlOp + " :" + paramName;
    }

    return "custom_fields ->> '" + slug + "' " + sqlOp + " :" + paramName;
  }

  /** Canonical form of a work-type operand; a blank operand is kept as-is (it matches nothing). */
  private static String canonicalWorkTypeOperand(String operand) {
    String canonical = WorkTypes.normalize(operand);
    return canonical != null ? canonical : operand;
  }
}
