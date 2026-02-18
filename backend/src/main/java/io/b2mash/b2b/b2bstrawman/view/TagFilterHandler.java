package io.b2mash.b2b.b2bstrawman.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Translates a tag filter (list of tag slugs) into EXISTS subqueries. Uses AND logic — all
 * specified tags must be present on the entity.
 */
@Component
public class TagFilterHandler {

  /**
   * Builds SQL predicates for tag filtering using EXISTS subqueries with AND logic.
   *
   * @param filterValue a List of tag slug strings (e.g., ["vip_client", "urgent"])
   * @param params the parameter map to populate with named bindings
   * @param entityType the entity type (PROJECT, TASK, CUSTOMER)
   * @return SQL predicate fragment with one EXISTS per tag ANDed together, or empty string
   */
  @SuppressWarnings("unchecked")
  public String buildPredicate(Object filterValue, Map<String, Object> params, String entityType) {
    if (filterValue == null) {
      return "";
    }

    if (!(filterValue instanceof List<?> rawList) || rawList.isEmpty()) {
      return "";
    }

    List<String> tagSlugs = ((List<Object>) filterValue).stream().map(Object::toString).toList();
    params.put("entityType", entityType);

    List<String> existsClauses = new ArrayList<>();
    for (int i = 0; i < tagSlugs.size(); i++) {
      String paramName = "tag" + i;
      params.put(paramName, tagSlugs.get(i));
      existsClauses.add(
          "EXISTS (SELECT 1 FROM entity_tags et JOIN tags t ON et.tag_id = t.id"
              + " WHERE et.entity_type = :entityType AND et.entity_id = e.id AND t.slug = :"
              + paramName
              + ")");
    }

    return String.join(" AND ", existsClauses);
  }
}
