package io.b2mash.b2b.b2bstrawman.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StatusFilterHandlerTest {

  private final StatusFilterHandler handler = new StatusFilterHandler();

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
  void buildsInClauseForStatusList() {
    Map<String, Object> params = new HashMap<>();
    String result = handler.buildPredicate(List.of("ACTIVE", "ON_HOLD"), params, "PROJECT");

    assertThat(result).isEqualTo("status IN (:statuses)");
    assertThat(params).containsEntry("statuses", List.of("ACTIVE", "ON_HOLD"));
  }

  @Test
  void buildsInClauseForSingleStatus() {
    Map<String, Object> params = new HashMap<>();
    String result = handler.buildPredicate(List.of("ACTIVE"), params, "PROJECT");

    assertThat(result).isEqualTo("status IN (:statuses)");
    assertThat(params).containsEntry("statuses", List.of("ACTIVE"));
  }
}
