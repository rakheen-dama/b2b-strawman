package io.b2mash.b2b.b2bstrawman.view;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Translates a status filter (list of status values) into a SQL IN clause. */
@Component
public class StatusFilterHandler {

  /**
   * Builds a SQL predicate for status filtering.
   *
   * @param filterValue a List of status strings (e.g., ["ACTIVE", "ON_HOLD"])
   * @param params the parameter map to populate with named bindings
   * @param entityType the entity type (unused for status filtering)
   * @return SQL predicate fragment, or empty string if filterValue is null/empty
   */
  @SuppressWarnings("unchecked")
  public String buildPredicate(Object filterValue, Map<String, Object> params, String entityType) {
    if (filterValue == null) {
      return "";
    }

    if (!(filterValue instanceof List<?> rawList) || rawList.isEmpty()) {
      return "";
    }

    List<String> statuses = ((List<Object>) filterValue).stream().map(Object::toString).toList();
    params.put("statuses", statuses);
    return "status IN (:statuses)";
  }
}
