package io.b2mash.b2b.b2bstrawman.view;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Utility for extracting and evaluating custom field filters from query parameters. Supports both
 * JSONB custom fields and promoted entity columns.
 *
 * <p>Promoted-slug sets are <strong>entity-scoped</strong>: filtering on {@code city} is valid for
 * a CUSTOMER view (maps to the {@code customers.city} column) but must stay in JSONB for a PROJECT
 * view (where no such column exists). Callers should consult {@link #PROMOTED_BY_ENTITY} via {@link
 * #isPromotedForEntity(String, String)}, and must pass the entity type through the SQL predicate
 * builder.
 */
public final class CustomFieldFilterUtil {

  /**
   * Promoted field slugs for Customer entity. These fields have been promoted from JSONB
   * custom_fields to dedicated entity columns.
   */
  public static final Set<String> CUSTOMER_PROMOTED_SLUGS =
      Set.of(
          "registration_number",
          "address_line1",
          "address_line2",
          "city",
          "state_province",
          "postal_code",
          "country",
          "tax_number",
          "contact_name",
          "contact_email",
          "contact_phone",
          "entity_type",
          "financial_year_end");

  /**
   * Promoted field slugs for Project entity. These fields have been promoted from JSONB
   * custom_fields to dedicated entity columns.
   */
  public static final Set<String> PROJECT_PROMOTED_SLUGS =
      Set.of("reference_number", "priority", "work_type");

  /**
   * Promoted field slugs for Task entity. These fields have been promoted from JSONB custom_fields
   * to dedicated entity columns.
   */
  public static final Set<String> TASK_PROMOTED_SLUGS = Set.of("estimated_hours");

  /**
   * Promoted field slugs for Invoice entity. These fields have been promoted from JSONB
   * custom_fields to dedicated entity columns.
   */
  public static final Set<String> INVOICE_PROMOTED_SLUGS =
      Set.of("po_number", "tax_type", "billing_period_start", "billing_period_end");

  /**
   * Entity-scoped promoted slug sets. Keyed by the canonical {@code entityType} string used by view
   * filters (e.g. {@code "CUSTOMER"}, {@code "PROJECT"}, {@code "TASK"}, {@code "INVOICE"}). Use
   * this — not {@link #ALL_PROMOTED_SLUGS} — when deciding whether a slug should be routed to an
   * entity column on a particular entity, to prevent cross-entity leakage (e.g. filtering {@code
   * city} on a PROJECT view must stay in JSONB since {@code projects.city} does not exist).
   */
  public static final Map<String, Set<String>> PROMOTED_BY_ENTITY =
      Map.of(
          "CUSTOMER", CUSTOMER_PROMOTED_SLUGS,
          "PROJECT", PROJECT_PROMOTED_SLUGS,
          "TASK", TASK_PROMOTED_SLUGS,
          "INVOICE", INVOICE_PROMOTED_SLUGS);

  /**
   * Union of all promoted slugs across every entity type. Prefer {@link
   * #isPromotedForEntity(String, String)} when the entity context is available — this set is only
   * useful for "has any entity promoted this slug?" checks (e.g. deduping structural prerequisite
   * violations that are already handled by {@link
   * io.b2mash.b2b.b2bstrawman.prerequisite.StructuralPrerequisiteCheck}).
   */
  public static final Set<String> ALL_PROMOTED_SLUGS;

  static {
    var all = new java.util.HashSet<String>();
    all.addAll(CUSTOMER_PROMOTED_SLUGS);
    all.addAll(PROJECT_PROMOTED_SLUGS);
    all.addAll(TASK_PROMOTED_SLUGS);
    all.addAll(INVOICE_PROMOTED_SLUGS);
    ALL_PROMOTED_SLUGS = Set.copyOf(all);
  }

  private CustomFieldFilterUtil() {}

  /**
   * Returns {@code true} when the given slug is promoted to an entity column for the supplied
   * entity type. A {@code null} entity type conservatively returns {@code false} (no column
   * promotion).
   */
  public static boolean isPromotedForEntity(String slug, String entityType) {
    if (entityType == null) {
      return false;
    }
    return PROMOTED_BY_ENTITY.getOrDefault(entityType, Set.of()).contains(slug);
  }

  public static Map<String, String> extractCustomFieldFilters(Map<String, String> allParams) {
    var filters = new HashMap<String, String>();
    if (allParams != null) {
      allParams.forEach(
          (key, value) -> {
            if (key.startsWith("customField[") && key.endsWith("]")) {
              String slug = key.substring("customField[".length(), key.length() - 1);
              filters.put(slug, value);
            }
          });
    }
    return filters;
  }

  /**
   * Matches custom field filters against JSONB custom fields, falling back through the shared 3-arg
   * implementation. Callers that cannot (yet) provide a promoted-fields map still work correctly:
   * the 3-arg variant treats an empty promoted-fields map as "no entity column values known" and
   * falls back to the JSONB map. This preserves backward compatibility for every existing caller
   * that only knows about {@code custom_fields}.
   */
  public static boolean matchesCustomFieldFilters(
      Map<String, Object> customFields, Map<String, String> filters) {
    return matchesCustomFieldFilters(customFields, Map.of(), filters);
  }

  /**
   * Matches custom field filters against both JSONB custom fields and promoted entity column
   * values. For a slug that is promoted on <em>any</em> entity, the promoted-fields map is checked
   * first; if the caller did not supply a value (e.g. because the caller only knows about JSONB)
   * the evaluation transparently falls back to the JSONB {@code customFields} map. This mirrors
   * {@link io.b2mash.b2b.b2bstrawman.prerequisite.StructuralPrerequisiteCheck} and keeps legacy
   * 2-arg callers working during the JSONB→column migration.
   *
   * @param customFields the JSONB custom fields map (may be null)
   * @param promotedFields a map of promoted field slug to entity column value (may be null/empty)
   * @param filters the filters to evaluate
   * @return true if all filters match
   */
  public static boolean matchesCustomFieldFilters(
      Map<String, Object> customFields,
      Map<String, Object> promotedFields,
      Map<String, String> filters) {
    if (filters == null || filters.isEmpty()) {
      return true;
    }
    for (var entry : filters.entrySet()) {
      String slug = entry.getKey();
      Object fieldValue = resolveFieldValue(slug, customFields, promotedFields);
      if (fieldValue == null || !fieldValue.toString().equals(entry.getValue())) {
        return false;
      }
    }
    return true;
  }

  /**
   * Resolves a slug value with entity-column-then-JSONB fallback. For promoted slugs the entity
   * column map is checked first; if missing or {@code null}, we fall through to JSONB so that
   * tenants still in the middle of the migration keep matching. Non-promoted slugs always read from
   * JSONB.
   */
  private static Object resolveFieldValue(
      String slug, Map<String, Object> customFields, Map<String, Object> promotedFields) {
    boolean promoted = ALL_PROMOTED_SLUGS.contains(slug);
    if (promoted && promotedFields != null && promotedFields.containsKey(slug)) {
      Object value = promotedFields.get(slug);
      if (value != null) {
        return value;
      }
    }
    return customFields != null ? customFields.get(slug) : null;
  }
}
