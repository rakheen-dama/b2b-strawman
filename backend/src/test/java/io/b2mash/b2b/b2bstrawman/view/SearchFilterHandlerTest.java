package io.b2mash.b2b.b2bstrawman.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SearchFilterHandlerTest {

  private final SearchFilterHandler handler = new SearchFilterHandler();

  @Test
  void returnsEmptyStringForNullValue() {
    Map<String, Object> params = new HashMap<>();
    assertThat(handler.buildPredicate(null, params, "PROJECT")).isEmpty();
    assertThat(params).isEmpty();
  }

  @Test
  void returnsEmptyStringForBlankValue() {
    Map<String, Object> params = new HashMap<>();
    assertThat(handler.buildPredicate("  ", params, "PROJECT")).isEmpty();
    assertThat(params).isEmpty();
  }

  @Test
  void buildsIlikeClauseWithNameForProjects() {
    Map<String, Object> params = new HashMap<>();
    String result = handler.buildPredicate("acme", params, "PROJECT");

    assertThat(result).isEqualTo("name ILIKE '%' || :search || '%'");
    assertThat(params).containsEntry("search", "acme");
  }

  @Test
  void buildsIlikeClauseWithNameForCustomers() {
    Map<String, Object> params = new HashMap<>();
    String result = handler.buildPredicate("smith", params, "CUSTOMER");

    assertThat(result).isEqualTo("name ILIKE '%' || :search || '%'");
    assertThat(params).containsEntry("search", "smith");
  }

  @Test
  void buildsIlikeClauseWithTitleForTasks() {
    Map<String, Object> params = new HashMap<>();
    String result = handler.buildPredicate("fix bug", params, "TASK");

    assertThat(result).isEqualTo("title ILIKE '%' || :search || '%'");
    assertThat(params).containsEntry("search", "fix bug");
  }

  @Test
  void trimssSearchValue() {
    Map<String, Object> params = new HashMap<>();
    String result = handler.buildPredicate("  acme  ", params, "PROJECT");

    assertThat(result).isEqualTo("name ILIKE '%' || :search || '%'");
    assertThat(params).containsEntry("search", "acme");
  }
}
