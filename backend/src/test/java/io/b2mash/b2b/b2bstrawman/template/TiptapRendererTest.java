package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.clause.Clause;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TiptapRendererTest {

  private TiptapRenderer renderer;

  @BeforeEach
  void setUp() {
    renderer = new TiptapRenderer("body { font-size: 11pt; }");
  }

  @Test
  void doc_node_renders_children() {
    var doc =
        Map.<String, Object>of(
            "type",
            "doc",
            "content",
            List.of(
                Map.<String, Object>of(
                    "type",
                    "paragraph",
                    "content",
                    List.of(Map.<String, Object>of("type", "text", "text", "Hello")))));

    String html = render(doc);

    assertThat(html).contains("<p>Hello</p>");
  }

  @Test
  void heading_level_1_renders_h1() {
    var doc =
        doc(
            Map.<String, Object>of(
                "type", "heading",
                "attrs", Map.<String, Object>of("level", 1),
                "content", List.of(Map.<String, Object>of("type", "text", "text", "Title"))));

    String html = render(doc);

    assertThat(html).contains("<h1>Title</h1>");
  }

  @Test
  void heading_level_3_renders_h3() {
    var doc =
        doc(
            Map.<String, Object>of(
                "type", "heading",
                "attrs", Map.<String, Object>of("level", 3),
                "content", List.of(Map.<String, Object>of("type", "text", "text", "Subtitle"))));

    String html = render(doc);

    assertThat(html).contains("<h3>Subtitle</h3>");
  }

  @Test
  void paragraph_renders_p_tag() {
    var doc =
        doc(
            Map.<String, Object>of(
                "type",
                "paragraph",
                "content",
                List.of(Map.<String, Object>of("type", "text", "text", "Some text"))));

    String html = render(doc);

    assertThat(html).contains("<p>Some text</p>");
  }

  @Test
  void text_with_bold_mark_renders_strong() {
    var doc =
        doc(
            Map.<String, Object>of(
                "type",
                "paragraph",
                "content",
                List.of(
                    Map.<String, Object>of(
                        "type", "text",
                        "text", "Bold text",
                        "marks", List.of(Map.<String, Object>of("type", "bold"))))));

    String html = render(doc);

    assertThat(html).contains("<strong>Bold text</strong>");
  }

  @Test
  void text_with_italic_mark_renders_em() {
    var doc =
        doc(
            Map.<String, Object>of(
                "type",
                "paragraph",
                "content",
                List.of(
                    Map.<String, Object>of(
                        "type", "text",
                        "text", "Italic text",
                        "marks", List.of(Map.<String, Object>of("type", "italic"))))));

    String html = render(doc);

    assertThat(html).contains("<em>Italic text</em>");
  }

  @Test
  void text_with_link_mark_renders_anchor() {
    var markAttrs = new HashMap<String, Object>();
    markAttrs.put("href", "https://example.com");
    var mark = new HashMap<String, Object>();
    mark.put("type", "link");
    mark.put("attrs", markAttrs);

    var textNode = new HashMap<String, Object>();
    textNode.put("type", "text");
    textNode.put("text", "Click here");
    textNode.put("marks", List.of(mark));

    var doc = doc(Map.<String, Object>of("type", "paragraph", "content", List.of(textNode)));

    String html = render(doc);

    assertThat(html).contains("<a href=\"https://example.com\">Click here</a>");
  }

  @Test
  void variable_resolves_dot_path() {
    var context = new HashMap<String, Object>();
    context.put("customer", Map.of("name", "Acme"));

    var doc =
        doc(
            Map.<String, Object>of(
                "type", "variable", "attrs", Map.<String, Object>of("key", "customer.name")));

    String html = renderer.render(doc, context, Map.of(), null);

    assertThat(html).contains("Acme");
  }

  @Test
  void variable_missing_segment_returns_empty() {
    var context = new HashMap<String, Object>();
    context.put("customer", Map.of());

    var doc =
        doc(
            Map.<String, Object>of(
                "type",
                "paragraph",
                "content",
                List.of(
                    Map.<String, Object>of("type", "text", "text", "Name: "),
                    Map.<String, Object>of(
                        "type",
                        "variable",
                        "attrs",
                        Map.<String, Object>of("key", "customer.missing")))));

    String html = renderer.render(doc, context, Map.of(), null);

    assertThat(html).contains("<p>Name: </p>");
  }

  @Test
  void bullet_list_renders_ul_li() {
    var doc =
        doc(
            Map.<String, Object>of(
                "type",
                "bulletList",
                "content",
                List.of(
                    Map.<String, Object>of(
                        "type",
                        "listItem",
                        "content",
                        List.of(
                            Map.<String, Object>of(
                                "type",
                                "paragraph",
                                "content",
                                List.of(
                                    Map.<String, Object>of("type", "text", "text", "Item 1"))))))));

    String html = render(doc);

    assertThat(html).contains("<ul><li><p>Item 1</p></li></ul>");
  }

  @Test
  void ordered_list_renders_ol_li() {
    var doc =
        doc(
            Map.<String, Object>of(
                "type",
                "orderedList",
                "content",
                List.of(
                    Map.<String, Object>of(
                        "type",
                        "listItem",
                        "content",
                        List.of(
                            Map.<String, Object>of(
                                "type",
                                "paragraph",
                                "content",
                                List.of(
                                    Map.<String, Object>of("type", "text", "text", "First"))))))));

    String html = render(doc);

    assertThat(html).contains("<ol><li><p>First</p></li></ol>");
  }

  @Test
  void horizontal_rule_renders_hr() {
    var doc = doc(Map.<String, Object>of("type", "horizontalRule"));

    String html = render(doc);

    assertThat(html).contains("<hr/>");
  }

  @Test
  void hard_break_renders_br() {
    var doc =
        doc(
            Map.<String, Object>of(
                "type",
                "paragraph",
                "content",
                List.of(
                    Map.<String, Object>of("type", "text", "text", "Line 1"),
                    Map.<String, Object>of("type", "hardBreak"),
                    Map.<String, Object>of("type", "text", "text", "Line 2"))));

    String html = render(doc);

    assertThat(html).contains("<p>Line 1<br/>Line 2</p>");
  }

  @Test
  void clause_block_renders_clause_body() {
    UUID clauseId = UUID.randomUUID();
    var clauseBody =
        Map.<String, Object>of(
            "type",
            "doc",
            "content",
            List.of(
                Map.<String, Object>of(
                    "type",
                    "paragraph",
                    "content",
                    List.of(Map.<String, Object>of("type", "text", "text", "Net 30 days")))));
    var clause = new Clause("Payment Terms", "payment-terms", clauseBody, "billing");

    var clauseNode = new HashMap<String, Object>();
    clauseNode.put("type", "clauseBlock");
    clauseNode.put(
        "attrs", Map.<String, Object>of("clauseId", clauseId.toString(), "slug", "payment-terms"));

    var doc = doc(clauseNode);

    String html = renderer.render(doc, Map.of(), Map.of(clauseId, clause), null);

    assertThat(html).contains("<div class=\"clause-block\" data-clause-slug=\"payment-terms\">");
    assertThat(html).contains("Net 30 days");
  }

  @Test
  void clause_block_missing_clause_renders_comment() {
    UUID missingId = UUID.randomUUID();
    var clauseNode = new HashMap<String, Object>();
    clauseNode.put("type", "clauseBlock");
    clauseNode.put(
        "attrs",
        Map.<String, Object>of("clauseId", missingId.toString(), "slug", "unknown-clause"));

    var doc = doc(clauseNode);

    String html = renderer.render(doc, Map.of(), Map.of(), null);

    assertThat(html).contains("<!-- clause not found: unknown-clause -->");
  }

  @Test
  void loop_table_with_data_renders_rows() {
    var context = new HashMap<String, Object>();
    context.put(
        "invoice",
        Map.of(
            "lines",
            List.of(
                Map.of("description", "Consulting", "amount", "1000"),
                Map.of("description", "Support", "amount", "500"))));

    var loopNode = new HashMap<String, Object>();
    loopNode.put("type", "loopTable");
    loopNode.put(
        "attrs",
        Map.<String, Object>of(
            "dataSource",
            "invoice.lines",
            "columns",
            List.of(
                Map.of("header", "Description", "key", "description"),
                Map.of("header", "Amount", "key", "amount"))));

    var doc = doc(loopNode);

    String html = renderer.render(doc, context, Map.of(), null);

    assertThat(html).contains("<thead><tr><th>Description</th><th>Amount</th></tr></thead>");
    assertThat(html).contains("<td>Consulting</td><td>1000</td>");
    assertThat(html).contains("<td>Support</td><td>500</td>");
  }

  @Test
  void loop_table_empty_data_renders_header_only() {
    var context = new HashMap<String, Object>();
    context.put("invoice", Map.of("lines", List.of()));

    var loopNode = new HashMap<String, Object>();
    loopNode.put("type", "loopTable");
    loopNode.put(
        "attrs",
        Map.<String, Object>of(
            "dataSource",
            "invoice.lines",
            "columns",
            List.of(Map.of("header", "Item", "key", "item"))));

    var doc = doc(loopNode);

    String html = renderer.render(doc, context, Map.of(), null);

    assertThat(html).contains("<thead><tr><th>Item</th></tr></thead>");
    assertThat(html).contains("<tbody></tbody>");
  }

  @Test
  void loop_table_missing_datasource_renders_header_only() {
    var loopNode = new HashMap<String, Object>();
    loopNode.put("type", "loopTable");
    loopNode.put(
        "attrs",
        Map.<String, Object>of(
            "dataSource",
            "missing.path",
            "columns",
            List.of(Map.of("header", "Col", "key", "col"))));

    var doc = doc(loopNode);

    String html = renderer.render(doc, Map.of(), Map.of(), null);

    assertThat(html).contains("<thead><tr><th>Col</th></tr></thead>");
    assertThat(html).contains("<tbody></tbody>");
  }

  @Test
  void legacy_html_passes_through_raw() {
    var legacyNode = new HashMap<String, Object>();
    legacyNode.put("type", "legacyHtml");
    legacyNode.put("attrs", Map.<String, Object>of("html", "<p>Raw <em>HTML</em> content</p>"));

    var doc = doc(legacyNode);

    String html = render(doc);

    assertThat(html).contains("<p>Raw <em>HTML</em> content</p>");
  }

  @Test
  void render_returns_full_html_document() {
    var doc =
        Map.<String, Object>of(
            "type",
            "doc",
            "content",
            List.of(
                Map.<String, Object>of(
                    "type",
                    "paragraph",
                    "content",
                    List.of(Map.<String, Object>of("type", "text", "text", "Hello")))));

    String html = render(doc);

    assertThat(html).startsWith("<!DOCTYPE html>");
    assertThat(html).contains("<html>");
    assertThat(html).contains("<style>");
    assertThat(html).contains("</body></html>");
  }

  @Test
  void variable_html_escaping() {
    var context = new HashMap<String, Object>();
    context.put("user", Map.of("name", "<script>alert('xss')</script>"));

    var doc =
        doc(
            Map.<String, Object>of(
                "type", "variable", "attrs", Map.<String, Object>of("key", "user.name")));

    String html = renderer.render(doc, context, Map.of(), null);

    assertThat(html).contains("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;");
    assertThat(html).doesNotContain("<script>");
  }

  @Test
  void legacyHtml_strips_script_tags() {
    var legacyNode = new HashMap<String, Object>();
    legacyNode.put("type", "legacyHtml");
    legacyNode.put(
        "attrs",
        Map.<String, Object>of("html", "<p>Safe</p><script>alert(1)</script><p>Also safe</p>"));

    var doc = doc(legacyNode);

    String html = render(doc);

    assertThat(html).contains("<p>Safe</p>");
    assertThat(html).contains("<p>Also safe</p>");
    assertThat(html).doesNotContain("<script>");
    assertThat(html).doesNotContain("alert(1)");
  }

  @Test
  void heading_with_invalid_level_defaults_to_h1() {
    var doc =
        doc(
            Map.<String, Object>of(
                "type", "heading",
                "attrs", Map.<String, Object>of("level", "not-a-number"),
                "content", List.of(Map.<String, Object>of("type", "text", "text", "Title"))));

    String html = render(doc);

    assertThat(html).contains("<h1>Title</h1>");
  }

  @Test
  void link_with_javascript_href_renders_empty_href() {
    var markAttrs = new HashMap<String, Object>();
    markAttrs.put("href", "javascript:alert(1)");
    var mark = new HashMap<String, Object>();
    mark.put("type", "link");
    mark.put("attrs", markAttrs);

    var textNode = new HashMap<String, Object>();
    textNode.put("type", "text");
    textNode.put("text", "Click here");
    textNode.put("marks", List.of(mark));

    var doc = doc(Map.<String, Object>of("type", "paragraph", "content", List.of(textNode)));

    String html = render(doc);

    assertThat(html).contains("<a href=\"\">Click here</a>");
    assertThat(html).doesNotContain("javascript:");
  }

  @Test
  void templateCss_injection_stripped() {
    var doc =
        Map.<String, Object>of(
            "type",
            "doc",
            "content",
            List.of(
                Map.<String, Object>of(
                    "type",
                    "paragraph",
                    "content",
                    List.of(Map.<String, Object>of("type", "text", "text", "Hello")))));

    String html =
        renderer.render(
            doc, Map.of(), Map.of(), "body{color:red}</style><script>alert(1)</script>");

    assertThat(html).doesNotContain("</style><script>");
    assertThat(html).contains("body{color:red}");
  }

  @Test
  void clause_block_with_malformed_uuid_renders_comment() {
    var clauseNode = new HashMap<String, Object>();
    clauseNode.put("type", "clauseBlock");
    clauseNode.put(
        "attrs", Map.<String, Object>of("clauseId", "not-a-uuid", "slug", "some-clause"));

    var doc = doc(clauseNode);

    String html = renderer.render(doc, Map.of(), Map.of(), null);

    assertThat(html).contains("<!-- invalid clauseId -->");
  }

  // --- Test helpers ---

  private Map<String, Object> doc(Map<String, Object> childNode) {
    return Map.of("type", "doc", "content", List.of(childNode));
  }

  private String render(Map<String, Object> doc) {
    return renderer.render(doc, Map.of(), Map.of(), null);
  }
}
