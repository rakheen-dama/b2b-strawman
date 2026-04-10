package io.b2mash.b2b.b2bstrawman.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CustomFieldFilterUtilTest {

  // --- extractCustomFieldFilters tests ---

  @Test
  void extractCustomFieldFilters_returnsEmptyForNullParams() {
    assertThat(CustomFieldFilterUtil.extractCustomFieldFilters(null)).isEmpty();
  }

  @Test
  void extractCustomFieldFilters_returnsEmptyForEmptyParams() {
    assertThat(CustomFieldFilterUtil.extractCustomFieldFilters(Map.of())).isEmpty();
  }

  @Test
  void extractCustomFieldFilters_ignoresNonCustomFieldParams() {
    var params = Map.of("view", "abc", "status", "open", "page", "1");
    assertThat(CustomFieldFilterUtil.extractCustomFieldFilters(params)).isEmpty();
  }

  @Test
  void extractCustomFieldFilters_extractsCustomFieldParams() {
    var params = new HashMap<String, String>();
    params.put("customField[industry]", "tech");
    params.put("customField[region]", "emea");
    params.put("status", "open");

    var result = CustomFieldFilterUtil.extractCustomFieldFilters(params);

    assertThat(result)
        .containsExactlyInAnyOrderEntriesOf(Map.of("industry", "tech", "region", "emea"));
  }

  // --- matchesCustomFieldFilters tests ---

  @Test
  void matchesCustomFieldFilters_returnsTrueForNullFieldsAndEmptyFilters() {
    assertThat(CustomFieldFilterUtil.matchesCustomFieldFilters(null, Map.of())).isTrue();
  }

  @Test
  void matchesCustomFieldFilters_returnsFalseForNullFieldsAndNonEmptyFilters() {
    assertThat(CustomFieldFilterUtil.matchesCustomFieldFilters(null, Map.of("key", "val")))
        .isFalse();
  }

  @Test
  void matchesCustomFieldFilters_returnsTrueWhenAllFiltersMatch() {
    Map<String, Object> fields = Map.of("industry", "tech", "region", "emea");
    Map<String, String> filters = Map.of("industry", "tech");

    assertThat(CustomFieldFilterUtil.matchesCustomFieldFilters(fields, filters)).isTrue();
  }

  @Test
  void matchesCustomFieldFilters_returnsFalseWhenFilterValueMismatches() {
    Map<String, Object> fields = Map.of("industry", "tech");
    Map<String, String> filters = Map.of("industry", "finance");

    assertThat(CustomFieldFilterUtil.matchesCustomFieldFilters(fields, filters)).isFalse();
  }

  @Test
  void matchesCustomFieldFilters_returnsFalseWhenFilterKeyMissing() {
    Map<String, Object> fields = Map.of("industry", "tech");
    Map<String, String> filters = Map.of("region", "emea");

    assertThat(CustomFieldFilterUtil.matchesCustomFieldFilters(fields, filters)).isFalse();
  }

  @Test
  void matchesCustomFieldFilters_usesToStringForNonStringValues() {
    Map<String, Object> fields = Map.of("count", 42);
    Map<String, String> filters = Map.of("count", "42");

    assertThat(CustomFieldFilterUtil.matchesCustomFieldFilters(fields, filters)).isTrue();
  }

  // --- Promoted field tests ---

  @Test
  void matchesCustomFieldFilters_promotedFieldCheckedFromPromotedMap() {
    Map<String, Object> customFields = Map.of(); // empty JSONB
    Map<String, Object> promotedFields = Map.of("city", "Johannesburg");
    Map<String, String> filters = Map.of("city", "Johannesburg");

    assertThat(
            CustomFieldFilterUtil.matchesCustomFieldFilters(customFields, promotedFields, filters))
        .isTrue();
  }

  @Test
  void matchesCustomFieldFilters_promotedFieldMissingFromPromotedMap_fallsBackToJsonb() {
    // Legacy / in-flight migration scenario: the caller didn't hydrate the promoted-fields map
    // (e.g. controllers still using the 2-arg entry point) but the tenant still has the value in
    // JSONB. We MUST match so that filters keep working during the JSONB→column migration.
    Map<String, Object> customFields = Map.of("city", "Johannesburg"); // JSONB has value
    Map<String, Object> promotedFields = Map.of(); // promoted map is empty
    Map<String, String> filters = Map.of("city", "Johannesburg");

    assertThat(
            CustomFieldFilterUtil.matchesCustomFieldFilters(customFields, promotedFields, filters))
        .isTrue();
  }

  @Test
  void matchesCustomFieldFilters_promotedFieldPrefersEntityColumnOverJsonb() {
    // When both sources are populated, the entity column wins (post-migration source of truth).
    Map<String, Object> customFields = Map.of("city", "Stale JSONB Value");
    Map<String, Object> promotedFields = Map.of("city", "Johannesburg");
    Map<String, String> filters = Map.of("city", "Johannesburg");

    assertThat(
            CustomFieldFilterUtil.matchesCustomFieldFilters(customFields, promotedFields, filters))
        .isTrue();
  }

  @Test
  void matchesCustomFieldFilters_twoArgCallerWithPromotedSlugInJsonb_matches() {
    // Legacy 2-arg caller (e.g. CustomerController pre-migration): hasn't been updated to supply a
    // promoted-fields map. A tenant that still stores city in JSONB must still match.
    Map<String, Object> customFields = Map.of("city", "Durban");
    Map<String, String> filters = Map.of("city", "Durban");

    assertThat(CustomFieldFilterUtil.matchesCustomFieldFilters(customFields, filters)).isTrue();
  }

  @Test
  void matchesCustomFieldFilters_nonPromotedFieldStillChecksJsonb() {
    Map<String, Object> customFields = Map.of("court", "high_court");
    Map<String, Object> promotedFields = Map.of();
    Map<String, String> filters = Map.of("court", "high_court");

    assertThat(
            CustomFieldFilterUtil.matchesCustomFieldFilters(customFields, promotedFields, filters))
        .isTrue();
  }

  @Test
  void matchesCustomFieldFilters_mixedPromotedAndNonPromoted() {
    Map<String, Object> customFields = Map.of("court", "high_court");
    Map<String, Object> promotedFields = Map.of("city", "Cape Town");
    Map<String, String> filters = Map.of("city", "Cape Town", "court", "high_court");

    assertThat(
            CustomFieldFilterUtil.matchesCustomFieldFilters(customFields, promotedFields, filters))
        .isTrue();
  }

  @Test
  void promotedSlugSets_containExpectedSlugs() {
    assertThat(CustomFieldFilterUtil.CUSTOMER_PROMOTED_SLUGS)
        .contains("registration_number", "city", "country", "financial_year_end");
    assertThat(CustomFieldFilterUtil.PROJECT_PROMOTED_SLUGS)
        .contains("reference_number", "priority", "work_type");
    assertThat(CustomFieldFilterUtil.TASK_PROMOTED_SLUGS).contains("estimated_hours");
    assertThat(CustomFieldFilterUtil.INVOICE_PROMOTED_SLUGS)
        .contains("po_number", "tax_type", "billing_period_start", "billing_period_end");
  }

  @Test
  void isPromotedForEntity_scopesPromotedSlugsToEntity() {
    // city is promoted on CUSTOMER, not on PROJECT/TASK/INVOICE
    assertThat(CustomFieldFilterUtil.isPromotedForEntity("city", "CUSTOMER")).isTrue();
    assertThat(CustomFieldFilterUtil.isPromotedForEntity("city", "PROJECT")).isFalse();
    assertThat(CustomFieldFilterUtil.isPromotedForEntity("city", "TASK")).isFalse();
    assertThat(CustomFieldFilterUtil.isPromotedForEntity("city", "INVOICE")).isFalse();

    // estimated_hours is promoted on TASK only
    assertThat(CustomFieldFilterUtil.isPromotedForEntity("estimated_hours", "TASK")).isTrue();
    assertThat(CustomFieldFilterUtil.isPromotedForEntity("estimated_hours", "PROJECT")).isFalse();

    // work_type is promoted on PROJECT only
    assertThat(CustomFieldFilterUtil.isPromotedForEntity("work_type", "PROJECT")).isTrue();
    assertThat(CustomFieldFilterUtil.isPromotedForEntity("work_type", "CUSTOMER")).isFalse();

    // Non-promoted slug never matches
    assertThat(CustomFieldFilterUtil.isPromotedForEntity("court", "CUSTOMER")).isFalse();

    // Null/unknown entity types are conservatively non-promoted
    assertThat(CustomFieldFilterUtil.isPromotedForEntity("city", null)).isFalse();
    assertThat(CustomFieldFilterUtil.isPromotedForEntity("city", "UNKNOWN")).isFalse();
  }

  @Test
  void allPromotedSlugs_containsAllEntitySets() {
    assertThat(CustomFieldFilterUtil.ALL_PROMOTED_SLUGS)
        .containsAll(CustomFieldFilterUtil.CUSTOMER_PROMOTED_SLUGS);
    assertThat(CustomFieldFilterUtil.ALL_PROMOTED_SLUGS)
        .containsAll(CustomFieldFilterUtil.PROJECT_PROMOTED_SLUGS);
    assertThat(CustomFieldFilterUtil.ALL_PROMOTED_SLUGS)
        .containsAll(CustomFieldFilterUtil.TASK_PROMOTED_SLUGS);
    assertThat(CustomFieldFilterUtil.ALL_PROMOTED_SLUGS)
        .containsAll(CustomFieldFilterUtil.INVOICE_PROMOTED_SLUGS);
  }
}
