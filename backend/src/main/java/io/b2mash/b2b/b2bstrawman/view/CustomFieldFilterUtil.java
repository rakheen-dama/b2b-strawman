package io.b2mash.b2b.b2bstrawman.view;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Utility for extracting and evaluating custom field filters from query parameters. Supports both
 * JSONB custom fields and promoted entity columns.
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

  /** All promoted field slugs across all entity types. */
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
   * Matches custom field filters against JSONB custom fields only. For backward compatibility, this
   * method does NOT check promoted entity columns.
   */
  public static boolean matchesCustomFieldFilters(
      Map<String, Object> customFields, Map<String, String> filters) {
    return matchesCustomFieldFilters(customFields, Map.of(), filters);
  }

  /**
   * Matches custom field filters against both JSONB custom fields and promoted entity column
   * values. For promoted field slugs, the entity column value takes precedence over the JSONB
   * value. For non-promoted slugs, the JSONB custom fields map is checked as before.
   *
   * @param customFields the JSONB custom fields map (may be null)
   * @param promotedFields a map of promoted field slug to entity column value
   * @param filters the filters to evaluate
   * @return true if all filters match
   */
  public static boolean matchesCustomFieldFilters(
      Map<String, Object> customFields,
      Map<String, Object> promotedFields,
      Map<String, String> filters) {
    if (customFields == null && (promotedFields == null || promotedFields.isEmpty())) {
      return filters.isEmpty();
    }
    for (var entry : filters.entrySet()) {
      String slug = entry.getKey();
      Object fieldValue;
      if (promotedFields != null && ALL_PROMOTED_SLUGS.contains(slug)) {
        // Promoted field: check entity column value first
        fieldValue = promotedFields.get(slug);
      } else {
        // Non-promoted field: check JSONB
        fieldValue = customFields != null ? customFields.get(slug) : null;
      }
      if (fieldValue == null || !fieldValue.toString().equals(entry.getValue())) {
        return false;
      }
    }
    return true;
  }
}
