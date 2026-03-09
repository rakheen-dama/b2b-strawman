package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConditionalBlockRenderTest {

  private TiptapRenderer renderer;

  @BeforeEach
  void setUp() {
    renderer = new TiptapRenderer("body { font-size: 11pt; }");
  }

  @Test
  void conditionalBlock_isNotEmpty_rendersWhenValuePresent() {
    var context = new HashMap<String, Object>();
    context.put("customer", Map.of("taxNumber", "VAT123"));

    var doc = doc(conditionalBlock("customer.taxNumber", "isNotEmpty", "", "Tax: present"));

    String html = renderer.render(doc, context, Map.of(), null);

    assertThat(html).contains("<p>Tax: present</p>");
  }

  @Test
  void conditionalBlock_isNotEmpty_skipsWhenValueMissing() {
    var context = new HashMap<String, Object>();
    context.put("customer", Map.of());

    var doc = doc(conditionalBlock("customer.taxNumber", "isNotEmpty", "", "Tax: present"));

    String html = renderer.render(doc, context, Map.of(), null);

    assertThat(html).doesNotContain("Tax: present");
  }

  @Test
  void conditionalBlock_isEmpty_rendersWhenValueMissing() {
    var context = new HashMap<String, Object>();
    context.put("customer", Map.of());

    var doc = doc(conditionalBlock("customer.taxNumber", "isEmpty", "", "No tax number on file"));

    String html = renderer.render(doc, context, Map.of(), null);

    assertThat(html).contains("<p>No tax number on file</p>");
  }

  @Test
  void conditionalBlock_isEmpty_skipsWhenValuePresent() {
    var context = new HashMap<String, Object>();
    context.put("customer", Map.of("taxNumber", "VAT123"));

    var doc = doc(conditionalBlock("customer.taxNumber", "isEmpty", "", "No tax number on file"));

    String html = renderer.render(doc, context, Map.of(), null);

    assertThat(html).doesNotContain("No tax number on file");
  }

  @Test
  void conditionalBlock_eq_matchesValue() {
    var context = new HashMap<String, Object>();
    context.put("customer", Map.of("type", "company"));

    var doc = doc(conditionalBlock("customer.type", "eq", "company", "Company clause"));

    String html = renderer.render(doc, context, Map.of(), null);

    assertThat(html).contains("<p>Company clause</p>");
  }

  @Test
  void conditionalBlock_eq_doesNotMatchDifferentValue() {
    var context = new HashMap<String, Object>();
    context.put("customer", Map.of("type", "individual"));

    var doc = doc(conditionalBlock("customer.type", "eq", "company", "Company clause"));

    String html = renderer.render(doc, context, Map.of(), null);

    assertThat(html).doesNotContain("Company clause");
  }

  @Test
  void conditionalBlock_neq_excludesValue() {
    var context = new HashMap<String, Object>();
    context.put("customer", Map.of("type", "individual"));

    var doc = doc(conditionalBlock("customer.type", "neq", "company", "Non-company clause"));

    String html = renderer.render(doc, context, Map.of(), null);

    assertThat(html).contains("<p>Non-company clause</p>");
  }

  @Test
  void conditionalBlock_neq_skipsWhenEqual() {
    var context = new HashMap<String, Object>();
    context.put("customer", Map.of("type", "company"));

    var doc = doc(conditionalBlock("customer.type", "neq", "company", "Non-company clause"));

    String html = renderer.render(doc, context, Map.of(), null);

    assertThat(html).doesNotContain("Non-company clause");
  }

  @Test
  void conditionalBlock_in_matchesOneOfList() {
    var context = new HashMap<String, Object>();
    context.put("customer", Map.of("type", "trust"));

    var doc = doc(conditionalBlock("customer.type", "in", "company, trust, cc", "Entity clause"));

    String html = renderer.render(doc, context, Map.of(), null);

    assertThat(html).contains("<p>Entity clause</p>");
  }

  @Test
  void conditionalBlock_in_doesNotMatchOutsideList() {
    var context = new HashMap<String, Object>();
    context.put("customer", Map.of("type", "individual"));

    var doc = doc(conditionalBlock("customer.type", "in", "company, trust, cc", "Entity clause"));

    String html = renderer.render(doc, context, Map.of(), null);

    assertThat(html).doesNotContain("Entity clause");
  }

  @Test
  void conditionalBlock_contains_matchesSubstring() {
    var context = new HashMap<String, Object>();
    context.put("project", Map.of("name", "Tax Advisory 2026"));

    var doc = doc(conditionalBlock("project.name", "contains", "Tax", "Tax-related content"));

    String html = renderer.render(doc, context, Map.of(), null);

    assertThat(html).contains("<p>Tax-related content</p>");
  }

  @Test
  void conditionalBlock_unconfigured_rendersContent() {
    var doc = doc(conditionalBlock("", "isNotEmpty", "", "Always shown"));

    String html = renderer.render(doc, Map.of(), Map.of(), null);

    assertThat(html).contains("<p>Always shown</p>");
  }

  @Test
  void conditionalBlock_nested_evaluatesIndependently() {
    // Outer: show if customer.type eq "company"
    // Inner: show if customer.taxNumber isNotEmpty
    var innerBlock = conditionalBlock("customer.taxNumber", "isNotEmpty", "", "Tax details here");
    var outerAttrs = new HashMap<String, Object>();
    outerAttrs.put("fieldKey", "customer.type");
    outerAttrs.put("operator", "eq");
    outerAttrs.put("value", "company");
    var outerBlock = new HashMap<String, Object>();
    outerBlock.put("type", "conditionalBlock");
    outerBlock.put("attrs", outerAttrs);
    outerBlock.put("content", List.of(innerBlock));

    var doc = doc(outerBlock);

    // Both conditions met
    var context1 = new HashMap<String, Object>();
    context1.put("customer", Map.of("type", "company", "taxNumber", "VAT123"));
    assertThat(renderer.render(doc, context1, Map.of(), null)).contains("Tax details here");

    // Outer met, inner not met (no taxNumber)
    var context2 = new HashMap<String, Object>();
    context2.put("customer", Map.of("type", "company"));
    assertThat(renderer.render(doc, context2, Map.of(), null)).doesNotContain("Tax details here");

    // Outer not met
    var context3 = new HashMap<String, Object>();
    context3.put("customer", Map.of("type", "individual", "taxNumber", "VAT123"));
    assertThat(renderer.render(doc, context3, Map.of(), null)).doesNotContain("Tax details here");
  }

  @Test
  void conditionalBlock_unknownOperator_rendersContent() {
    var context = new HashMap<String, Object>();
    context.put("customer", Map.of("name", "Acme"));

    var doc = doc(conditionalBlock("customer.name", "unknownOp", "", "Fail-open content"));

    String html = renderer.render(doc, context, Map.of(), null);

    assertThat(html).contains("<p>Fail-open content</p>");
  }

  // --- Test helpers ---

  private Map<String, Object> conditionalBlock(
      String fieldKey, String operator, String value, String contentText) {
    var attrs = new HashMap<String, Object>();
    attrs.put("fieldKey", fieldKey);
    attrs.put("operator", operator);
    attrs.put("value", value);

    var paragraph =
        Map.<String, Object>of(
            "type",
            "paragraph",
            "content",
            List.of(Map.<String, Object>of("type", "text", "text", contentText)));

    var block = new HashMap<String, Object>();
    block.put("type", "conditionalBlock");
    block.put("attrs", attrs);
    block.put("content", List.of(paragraph));
    return block;
  }

  private Map<String, Object> doc(Map<String, Object> childNode) {
    return Map.of("type", "doc", "content", List.of(childNode));
  }
}
