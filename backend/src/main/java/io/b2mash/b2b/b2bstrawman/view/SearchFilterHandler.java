package io.b2mash.b2b.b2bstrawman.view;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Translates a search keyword into an ILIKE predicate on the name/title column. Uses "title" for
 * TASK and DEAL entities (their tables have a {@code title} column, not {@code name}) and "name"
 * for PROJECT/CUSTOMER entities.
 */
@Component
public class SearchFilterHandler {

  /**
   * Builds a SQL predicate for search filtering using ILIKE.
   *
   * @param filterValue a String search keyword
   * @param params the parameter map to populate with named bindings
   * @param entityType the entity type — determines column name (title for TASK/DEAL, name
   *     otherwise)
   * @return SQL predicate fragment, or empty string if filterValue is null/empty
   */
  public String buildPredicate(Object filterValue, Map<String, Object> params, String entityType) {
    if (filterValue == null) {
      return "";
    }

    String search = filterValue.toString().trim();
    if (search.isEmpty()) {
      return "";
    }

    String column = ("TASK".equals(entityType) || "DEAL".equals(entityType)) ? "title" : "name";
    params.put("search", search);
    return column + " ILIKE '%' || :search || '%'";
  }
}
