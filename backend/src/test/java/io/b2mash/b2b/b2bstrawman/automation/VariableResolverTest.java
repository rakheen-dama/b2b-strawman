package io.b2mash.b2b.b2bstrawman.automation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VariableResolverTest {

  private final VariableResolver resolver = new VariableResolver();

  @Test
  void resolvesSimpleVariable() {
    var context = buildContext("task", Map.of("name", "Review PR"));
    String result = resolver.resolve("Task: {{task.name}}", context);
    assertThat(result).isEqualTo("Task: Review PR");
  }

  @Test
  void resolvesMultipleVariables() {
    var context = buildContext("task", Map.of("name", "Review PR", "status", "COMPLETED"));
    context.put("actor", new LinkedHashMap<>(Map.of("name", "Alice")));
    String result =
        resolver.resolve("{{actor.name}} changed {{task.name}} to {{task.status}}", context);
    assertThat(result).isEqualTo("Alice changed Review PR to COMPLETED");
  }

  @Test
  void unresolvedVariableStaysLiteral() {
    var context = buildContext("task", Map.of("name", "Review PR"));
    String result = resolver.resolve("{{unknown.field}} and {{task.name}}", context);
    assertThat(result).isEqualTo("{{unknown.field}} and Review PR");
  }

  @Test
  void nullTemplateReturnsNull() {
    var context = buildContext("task", Map.of("name", "Review PR"));
    assertThat(resolver.resolve(null, context)).isNull();
  }

  @Test
  void emptyTemplateReturnsEmpty() {
    var context = buildContext("task", Map.of("name", "Review PR"));
    assertThat(resolver.resolve("", context)).isEmpty();
  }

  @Test
  void nullContextReturnsTemplateUnchanged() {
    String result = resolver.resolve("Hello {{task.name}}", null);
    assertThat(result).isEqualTo("Hello {{task.name}}");
  }

  @Test
  void emptyContextReturnsTemplateUnchanged() {
    String result = resolver.resolve("Hello {{task.name}}", Map.of());
    assertThat(result).isEqualTo("Hello {{task.name}}");
  }

  @Test
  void nullFieldValueLeavesVariableLiteral() {
    var entityMap = new LinkedHashMap<String, Object>();
    entityMap.put("name", null);
    var context = buildContext("task", entityMap);
    String result = resolver.resolve("Task: {{task.name}}", context);
    assertThat(result).isEqualTo("Task: {{task.name}}");
  }

  @Test
  void templateWithNoVariablesReturnedUnchanged() {
    var context = buildContext("task", Map.of("name", "Review PR"));
    String result = resolver.resolve("Plain text without variables", context);
    assertThat(result).isEqualTo("Plain text without variables");
  }

  @Test
  void variableWithoutDotNotationStaysLiteral() {
    var context = buildContext("task", Map.of("name", "Review PR"));
    String result = resolver.resolve("{{nodot}}", context);
    assertThat(result).isEqualTo("{{nodot}}");
  }

  @Test
  void partiallyResolvedTemplate() {
    var context = buildContext("task", Map.of("name", "Deploy"));
    String result = resolver.resolve("{{task.name}} by {{actor.name}}", context);
    assertThat(result).isEqualTo("Deploy by {{actor.name}}");
  }

  private Map<String, Map<String, Object>> buildContext(String key, Map<String, Object> fields) {
    var context = new LinkedHashMap<String, Map<String, Object>>();
    context.put(key, new LinkedHashMap<>(fields));
    return context;
  }
}
