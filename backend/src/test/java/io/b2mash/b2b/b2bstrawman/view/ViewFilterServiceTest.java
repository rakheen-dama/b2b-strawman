package io.b2mash.b2b.b2bstrawman.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ViewFilterServiceTest {

  private final ViewFilterService service =
      new ViewFilterService(
          new StatusFilterHandler(),
          new TagFilterHandler(),
          new CustomFieldFilterHandler(),
          new DateRangeFilterHandler(),
          new SearchFilterHandler());

  @Test
  void returnsEmptyStringForNullFilters() {
    Map<String, Object> params = new HashMap<>();
    String result = service.buildWhereClause(null, params, "PROJECT");
    assertThat(result).isEmpty();
    assertThat(params).isEmpty();
  }

  @Test
  void returnsEmptyStringForEmptyFilters() {
    Map<String, Object> params = new HashMap<>();
    String result = service.buildWhereClause(Map.of(), params, "PROJECT");
    assertThat(result).isEmpty();
    assertThat(params).isEmpty();
  }

  @Test
  void singleStatusFilterBuildsInClause() {
    Map<String, Object> filters = Map.of("status", List.of("ACTIVE", "ON_HOLD"));
    Map<String, Object> params = new HashMap<>();

    String result = service.buildWhereClause(filters, params, "PROJECT");

    assertThat(result).isEqualTo("status IN (:statuses)");
    assertThat(params).containsEntry("statuses", List.of("ACTIVE", "ON_HOLD"));
  }

  @Test
  void singleSearchFilterBuildsIlikeClause() {
    Map<String, Object> filters = Map.of("search", "acme");
    Map<String, Object> params = new HashMap<>();

    String result = service.buildWhereClause(filters, params, "PROJECT");

    assertThat(result).isEqualTo("name ILIKE '%' || :search || '%'");
    assertThat(params).containsEntry("search", "acme");
  }

  @Test
  void searchFilterUsesColumnTitleForTasks() {
    Map<String, Object> filters = Map.of("search", "fix bug");
    Map<String, Object> params = new HashMap<>();

    String result = service.buildWhereClause(filters, params, "TASK");

    assertThat(result).isEqualTo("title ILIKE '%' || :search || '%'");
    assertThat(params).containsEntry("search", "fix bug");
  }

  @Test
  void combinedFiltersJoinWithAnd() {
    Map<String, Object> filters = new HashMap<>();
    filters.put("status", List.of("ACTIVE"));
    filters.put("search", "acme");

    Map<String, Object> params = new HashMap<>();

    String result = service.buildWhereClause(filters, params, "PROJECT");

    assertThat(result).contains("status IN (:statuses)");
    assertThat(result).contains("name ILIKE '%' || :search || '%'");
    assertThat(result).contains(" AND ");
    assertThat(params).containsEntry("statuses", List.of("ACTIVE"));
    assertThat(params).containsEntry("search", "acme");
  }

  @Test
  void tagsFilterBuildsExistsSubqueries() {
    Map<String, Object> filters = Map.of("tags", List.of("vip_client", "urgent"));
    Map<String, Object> params = new HashMap<>();

    String result = service.buildWhereClause(filters, params, "PROJECT");

    assertThat(result).contains("EXISTS (SELECT 1 FROM entity_tags et");
    assertThat(result).contains("t.slug = :tag0");
    assertThat(result).contains("t.slug = :tag1");
    assertThat(result).contains(" AND ");
    assertThat(params).containsEntry("tag0", "vip_client");
    assertThat(params).containsEntry("tag1", "urgent");
    assertThat(params).containsEntry("entityType", "PROJECT");
  }

  @Test
  void customFieldFilterBuildsJsonbPredicates() {
    Map<String, Object> filters =
        Map.of("customFields", Map.of("court", Map.of("op", "eq", "value", "high_court")));
    Map<String, Object> params = new HashMap<>();

    String result = service.buildWhereClause(filters, params, "CUSTOMER");

    assertThat(result).isEqualTo("custom_fields ->> 'court' = :cf_court");
    assertThat(params).containsEntry("cf_court", "high_court");
  }

  @Test
  void dateRangeFilterBuildsRangeClause() {
    Map<String, Object> dateRange = new HashMap<>();
    dateRange.put("field", "created_at");
    dateRange.put("from", "2025-01-01");
    dateRange.put("to", "2025-12-31");
    Map<String, Object> filters = Map.of("dateRange", dateRange);
    Map<String, Object> params = new HashMap<>();

    String result = service.buildWhereClause(filters, params, "PROJECT");

    assertThat(result).contains("created_at >= CAST(:dateFrom AS timestamp)");
    assertThat(result).contains("created_at <= CAST(:dateTo AS timestamp)");
    assertThat(params).containsEntry("dateFrom", "2025-01-01");
    assertThat(params).containsEntry("dateTo", "2025-12-31");
  }

  @Test
  void allFiltersTogetherProduceAndSeparatedClauses() {
    Map<String, Object> filters = new HashMap<>();
    filters.put("status", List.of("ACTIVE"));
    filters.put("tags", List.of("urgent"));
    filters.put("customFields", Map.of("court", Map.of("op", "eq", "value", "high_court")));
    Map<String, Object> dateRange = new HashMap<>();
    dateRange.put("field", "created_at");
    dateRange.put("from", "2025-01-01");
    filters.put("dateRange", dateRange);
    filters.put("search", "acme");

    Map<String, Object> params = new HashMap<>();

    String result = service.buildWhereClause(filters, params, "PROJECT");

    // All five filter types should be present
    assertThat(result).contains("status IN (:statuses)");
    assertThat(result).contains("EXISTS (SELECT 1 FROM entity_tags");
    assertThat(result).contains("custom_fields ->> 'court'");
    assertThat(result).contains("created_at >= CAST(:dateFrom AS timestamp)");
    assertThat(result).contains("name ILIKE '%' || :search || '%'");

    // Count AND separators — should be 4 (between 5 clauses)
    long andCount = result.chars().filter(c -> c == 'A').count();
    // Just verify all clauses are joined
    assertThat(result.split(" AND ").length).isGreaterThanOrEqualTo(5);
  }
}
