package io.b2mash.b2b.b2bstrawman.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CustomFieldFilterHandlerTest {

  private final CustomFieldFilterHandler handler = new CustomFieldFilterHandler();

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
  void buildsEqPredicate() {
    Map<String, Object> params = new HashMap<>();
    var fields = Map.of("court", (Object) Map.of("op", "eq", "value", "high_court"));

    String result = handler.buildPredicate(fields, params, "CUSTOMER");

    assertThat(result).isEqualTo("custom_fields ->> 'court' = :cf_court");
    assertThat(params).containsEntry("cf_court", "high_court");
  }

  @Test
  void buildsNeqPredicate() {
    Map<String, Object> params = new HashMap<>();
    var fields = Map.of("court", (Object) Map.of("op", "neq", "value", "high_court"));

    String result = handler.buildPredicate(fields, params, "CUSTOMER");

    assertThat(result).isEqualTo("custom_fields ->> 'court' != :cf_court");
    assertThat(params).containsEntry("cf_court", "high_court");
  }

  @Test
  void buildsNumericGtePredicate() {
    Map<String, Object> params = new HashMap<>();
    var fields = Map.of("amount", (Object) Map.of("op", "gte", "value", 1000));

    String result = handler.buildPredicate(fields, params, "PROJECT");

    assertThat(result).isEqualTo("(custom_fields ->> 'amount')::numeric >= :cf_amount");
    assertThat(params).containsEntry("cf_amount", "1000");
  }

  @Test
  void buildsContainsPredicate() {
    Map<String, Object> params = new HashMap<>();
    var fields = Map.of("notes", (Object) Map.of("op", "contains", "value", "important"));

    String result = handler.buildPredicate(fields, params, "PROJECT");

    assertThat(result).isEqualTo("custom_fields ->> 'notes' ILIKE '%' || :cf_notes || '%'");
    assertThat(params).containsEntry("cf_notes", "important");
  }

  @Test
  void buildsInPredicate() {
    Map<String, Object> params = new HashMap<>();
    var fields = Map.of("status", (Object) Map.of("op", "in", "value", List.of("draft", "review")));

    String result = handler.buildPredicate(fields, params, "PROJECT");

    assertThat(result).isEqualTo("custom_fields ->> 'status' IN (:cf_status)");
    assertThat(params).containsEntry("cf_status", List.of("draft", "review"));
  }

  @Test
  void buildsMultipleFieldPredicatesWithAnd() {
    Map<String, Object> params = new HashMap<>();
    Map<String, Object> fields = new HashMap<>();
    fields.put("court", Map.of("op", "eq", "value", "high_court"));
    fields.put("priority", Map.of("op", "eq", "value", "HIGH"));

    String result = handler.buildPredicate(fields, params, "PROJECT");

    assertThat(result).contains("custom_fields ->> 'court' = :cf_court");
    assertThat(result).contains("custom_fields ->> 'priority' = :cf_priority");
    assertThat(result).contains(" AND ");
  }

  @Test
  void ignoresUnknownOperator() {
    Map<String, Object> params = new HashMap<>();
    var fields = Map.of("court", (Object) Map.of("op", "unknown", "value", "test"));

    String result = handler.buildPredicate(fields, params, "PROJECT");

    assertThat(result).isEmpty();
    assertThat(params).isEmpty();
  }

  @Test
  void rejectsSlugWithSqlInjectionCharacters() {
    Map<String, Object> params = new HashMap<>();
    var fields = Map.of("foo' OR 1=1 --", (Object) Map.of("op", "eq", "value", "injected"));

    String result = handler.buildPredicate(fields, params, "PROJECT");

    assertThat(result).isEmpty();
    assertThat(params).isEmpty();
  }

  @Test
  void acceptsSlugWithHyphensAndUnderscores() {
    Map<String, Object> params = new HashMap<>();
    var fields = Map.of("filing-date_v2", (Object) Map.of("op", "eq", "value", "2025-01-01"));

    String result = handler.buildPredicate(fields, params, "PROJECT");

    assertThat(result).isEqualTo("custom_fields ->> 'filing-date_v2' = :cf_filing_date_v2");
    assertThat(params).containsEntry("cf_filing_date_v2", "2025-01-01");
  }
}
