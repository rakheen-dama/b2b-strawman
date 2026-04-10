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
  void matchesCustomFieldFilters_promotedFieldMissingFromPromotedMap_returnsFalse() {
    Map<String, Object> customFields = Map.of("city", "Johannesburg"); // JSONB has value
    Map<String, Object> promotedFields = Map.of(); // but promoted map doesn't
    Map<String, String> filters = Map.of("city", "Johannesburg");

    // Promoted field should check promotedFields, not customFields JSONB
    assertThat(
            CustomFieldFilterUtil.matchesCustomFieldFilters(customFields, promotedFields, filters))
        .isFalse();
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
