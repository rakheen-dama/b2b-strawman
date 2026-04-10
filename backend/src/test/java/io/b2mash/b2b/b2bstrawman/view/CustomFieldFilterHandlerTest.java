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
    fields.put("region", Map.of("op", "eq", "value", "north"));

    String result = handler.buildPredicate(fields, params, "PROJECT");

    assertThat(result).contains("custom_fields ->> 'court' = :cf_court");
    assertThat(result).contains("custom_fields ->> 'region' = :cf_region");
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

  // --- Promoted field tests ---

  @Test
  void promotedField_eqPredicate_generatesColumnClause() {
    Map<String, Object> params = new HashMap<>();
    var fields = Map.of("city", (Object) Map.of("op", "eq", "value", "Johannesburg"));

    String result = handler.buildPredicate(fields, params, "CUSTOMER");

    assertThat(result).isEqualTo("city = :cf_city");
    assertThat(params).containsEntry("cf_city", "Johannesburg");
  }

  @Test
  void promotedField_neqPredicate_generatesColumnClause() {
    Map<String, Object> params = new HashMap<>();
    var fields = Map.of("country", (Object) Map.of("op", "neq", "value", "US"));

    String result = handler.buildPredicate(fields, params, "CUSTOMER");

    assertThat(result).isEqualTo("country != :cf_country");
    assertThat(params).containsEntry("cf_country", "US");
  }

  @Test
  void promotedField_containsPredicate_generatesColumnClause() {
    Map<String, Object> params = new HashMap<>();
    var fields = Map.of("address_line1", (Object) Map.of("op", "contains", "value", "Main"));

    String result = handler.buildPredicate(fields, params, "CUSTOMER");

    assertThat(result).isEqualTo("address_line1 ILIKE '%' || :cf_address_line1 || '%'");
    assertThat(params).containsEntry("cf_address_line1", "Main");
  }

  @Test
  void promotedField_inPredicate_generatesColumnClause() {
    Map<String, Object> params = new HashMap<>();
    var fields = Map.of("work_type", (Object) Map.of("op", "in", "value", List.of("tax", "audit")));

    String result = handler.buildPredicate(fields, params, "PROJECT");

    assertThat(result).isEqualTo("work_type IN (:cf_work_type)");
    assertThat(params).containsEntry("cf_work_type", List.of("tax", "audit"));
  }

  @Test
  void promotedField_numericPredicate_generatesColumnCast() {
    Map<String, Object> params = new HashMap<>();
    var fields = Map.of("estimated_hours", (Object) Map.of("op", "gte", "value", 10));

    String result = handler.buildPredicate(fields, params, "TASK");

    assertThat(result).isEqualTo("estimated_hours::numeric >= :cf_estimated_hours");
    assertThat(params).containsEntry("cf_estimated_hours", "10");
  }

  @Test
  void promotedField_onWrongEntityType_fallsBackToJsonb() {
    // "city" is promoted on CUSTOMER only — on a PROJECT view it must NOT emit a column clause,
    // otherwise we'd produce broken SQL referring to projects.city which does not exist.
    Map<String, Object> params = new HashMap<>();
    var fields = Map.of("city", (Object) Map.of("op", "eq", "value", "Durban"));

    String result = handler.buildPredicate(fields, params, "PROJECT");

    assertThat(result).isEqualTo("custom_fields ->> 'city' = :cf_city");
    assertThat(params).containsEntry("cf_city", "Durban");
  }

  @Test
  void promotedField_onWrongEntityType_taskSlugStaysJsonbOnCustomer() {
    // "estimated_hours" is promoted on TASK only — on a CUSTOMER view it must stay in JSONB.
    Map<String, Object> params = new HashMap<>();
    var fields = Map.of("estimated_hours", (Object) Map.of("op", "gte", "value", 10));

    String result = handler.buildPredicate(fields, params, "CUSTOMER");

    assertThat(result)
        .isEqualTo("(custom_fields ->> 'estimated_hours')::numeric >= :cf_estimated_hours");
    assertThat(params).containsEntry("cf_estimated_hours", "10");
  }

  @Test
  void promotedField_onWrongEntityType_workTypeStaysJsonbOnCustomer() {
    // "work_type" is promoted on PROJECT only.
    Map<String, Object> params = new HashMap<>();
    var fields = Map.of("work_type", (Object) Map.of("op", "eq", "value", "audit"));

    String result = handler.buildPredicate(fields, params, "CUSTOMER");

    assertThat(result).isEqualTo("custom_fields ->> 'work_type' = :cf_work_type");
    assertThat(params).containsEntry("cf_work_type", "audit");
  }

  @Test
  void mixedPromotedAndNonPromoted_generatesMixedClauses() {
    Map<String, Object> params = new HashMap<>();
    Map<String, Object> fields = new HashMap<>();
    fields.put("city", Map.of("op", "eq", "value", "Durban"));
    fields.put("court", Map.of("op", "eq", "value", "high_court"));

    String result = handler.buildPredicate(fields, params, "CUSTOMER");

    assertThat(result).contains("city = :cf_city");
    assertThat(result).contains("custom_fields ->> 'court' = :cf_court");
    assertThat(result).contains(" AND ");
  }
}
