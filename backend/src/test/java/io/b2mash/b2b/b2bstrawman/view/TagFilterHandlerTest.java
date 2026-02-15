package io.b2mash.b2b.b2bstrawman.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TagFilterHandlerTest {

  private final TagFilterHandler handler = new TagFilterHandler();

  @Test
  void returnsEmptyStringForNullValue() {
    Map<String, Object> params = new HashMap<>();
    assertThat(handler.buildPredicate(null, params, "PROJECT")).isEmpty();
    assertThat(params).isEmpty();
  }

  @Test
  void returnsEmptyStringForEmptyList() {
    Map<String, Object> params = new HashMap<>();
    assertThat(handler.buildPredicate(List.of(), params, "PROJECT")).isEmpty();
    assertThat(params).isEmpty();
  }

  @Test
  void buildsSingleExistsSubqueryForOneTag() {
    Map<String, Object> params = new HashMap<>();
    String result = handler.buildPredicate(List.of("urgent"), params, "TASK");

    assertThat(result)
        .isEqualTo(
            "EXISTS (SELECT 1 FROM entity_tags et JOIN tags t ON et.tag_id = t.id"
                + " WHERE et.tenant_id = e.tenant_id"
                + " AND et.entity_type = :entityType AND et.entity_id = e.id AND t.slug ="
                + " :tag0)");
    assertThat(params).containsEntry("tag0", "urgent");
    assertThat(params).containsEntry("entityType", "TASK");
  }

  @Test
  void buildsAndedExistsSubqueriesForMultipleTags() {
    Map<String, Object> params = new HashMap<>();
    String result = handler.buildPredicate(List.of("vip_client", "urgent"), params, "PROJECT");

    assertThat(result).contains("t.slug = :tag0");
    assertThat(result).contains("t.slug = :tag1");
    assertThat(result).contains(" AND ");
    assertThat(params).containsEntry("tag0", "vip_client");
    assertThat(params).containsEntry("tag1", "urgent");
    assertThat(params).containsEntry("entityType", "PROJECT");
  }
}
