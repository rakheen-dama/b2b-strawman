package io.b2mash.b2b.b2bstrawman.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Translates a date range filter into SQL range predicates. Supports optional from/to bounds on
 * validated date fields.
 */
@Component
public class DateRangeFilterHandler {

  /** Allowed field names for date range filtering (prevent SQL injection via field name). */
  private static final Set<String> ALLOWED_FIELDS = Set.of("created_at", "updated_at", "due_date");

  /**
   * Builds SQL predicates for date range filtering.
   *
   * @param filterValue a Map with keys "field", "from", "to"
   * @param params the parameter map to populate with named bindings
   * @param entityType the entity type (unused for date range filtering)
   * @return SQL predicate fragment, or empty string if filterValue is null/empty
   */
  public String buildPredicate(Object filterValue, Map<String, Object> params, String entityType) {
    if (filterValue == null) {
      return "";
    }

    if (!(filterValue instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
      return "";
    }

    String field = rawMap.get("field") != null ? rawMap.get("field").toString() : null;
    if (field == null || !ALLOWED_FIELDS.contains(field)) {
      return "";
    }

    Object from = rawMap.get("from");
    Object to = rawMap.get("to");

    if (from == null && to == null) {
      return "";
    }

    List<String> clauses = new ArrayList<>();

    if (from != null) {
      params.put("dateFrom", from.toString());
      clauses.add(field + " >= CAST(:dateFrom AS timestamp)");
    }

    if (to != null) {
      params.put("dateTo", to.toString());
      clauses.add(field + " <= CAST(:dateTo AS timestamp)");
    }

    return String.join(" AND ", clauses);
  }
}
