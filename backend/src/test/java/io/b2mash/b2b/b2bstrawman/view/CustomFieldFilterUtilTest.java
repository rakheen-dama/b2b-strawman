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
}
