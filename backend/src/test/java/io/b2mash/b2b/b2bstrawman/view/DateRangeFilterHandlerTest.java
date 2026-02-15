package io.b2mash.b2b.b2bstrawman.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DateRangeFilterHandlerTest {

  private final DateRangeFilterHandler handler = new DateRangeFilterHandler();

  @Test
  void returnsEmptyStringForNullValue() {
    Map<String, Object> params = new HashMap<>();
    assertThat(handler.buildPredicate(null, params, "PROJECT")).isEmpty();
    assertThat(params).isEmpty();
  }

  @Test
  void returnsEmptyStringForEmptyMap() {
    Map<String, Object> params = new HashMap<>();
    assertThat(handler.buildPredicate(Map.of(), params, "PROJECT")).isEmpty();
    assertThat(params).isEmpty();
  }

  @Test
  void buildsFromAndToClause() {
    Map<String, Object> dateRange = new HashMap<>();
    dateRange.put("field", "created_at");
    dateRange.put("from", "2025-01-01");
    dateRange.put("to", "2025-12-31");

    Map<String, Object> params = new HashMap<>();
    String result = handler.buildPredicate(dateRange, params, "PROJECT");

    assertThat(result)
        .isEqualTo(
            "created_at >= CAST(:dateFrom AS timestamp)"
                + " AND created_at <= CAST(:dateTo AS timestamp)");
    assertThat(params).containsEntry("dateFrom", "2025-01-01");
    assertThat(params).containsEntry("dateTo", "2025-12-31");
  }

  @Test
  void buildsFromOnlyClause() {
    Map<String, Object> dateRange = new HashMap<>();
    dateRange.put("field", "updated_at");
    dateRange.put("from", "2025-06-01");
    dateRange.put("to", null);

    Map<String, Object> params = new HashMap<>();
    String result = handler.buildPredicate(dateRange, params, "PROJECT");

    assertThat(result).isEqualTo("updated_at >= CAST(:dateFrom AS timestamp)");
    assertThat(params).containsEntry("dateFrom", "2025-06-01");
    assertThat(params).doesNotContainKey("dateTo");
  }

  @Test
  void buildsToOnlyClause() {
    Map<String, Object> dateRange = new HashMap<>();
    dateRange.put("field", "created_at");
    dateRange.put("from", null);
    dateRange.put("to", "2025-12-31");

    Map<String, Object> params = new HashMap<>();
    String result = handler.buildPredicate(dateRange, params, "PROJECT");

    assertThat(result).isEqualTo("created_at <= CAST(:dateTo AS timestamp)");
    assertThat(params).doesNotContainKey("dateFrom");
    assertThat(params).containsEntry("dateTo", "2025-12-31");
  }

  @Test
  void rejectsDisallowedFieldName() {
    Map<String, Object> dateRange = new HashMap<>();
    dateRange.put("field", "malicious_field");
    dateRange.put("from", "2025-01-01");

    Map<String, Object> params = new HashMap<>();
    String result = handler.buildPredicate(dateRange, params, "PROJECT");

    assertThat(result).isEmpty();
    assertThat(params).isEmpty();
  }

  @Test
  void returnsEmptyWhenNoBoundsProvided() {
    Map<String, Object> dateRange = new HashMap<>();
    dateRange.put("field", "created_at");
    dateRange.put("from", null);
    dateRange.put("to", null);

    Map<String, Object> params = new HashMap<>();
    String result = handler.buildPredicate(dateRange, params, "PROJECT");

    assertThat(result).isEmpty();
    assertThat(params).isEmpty();
  }
}
