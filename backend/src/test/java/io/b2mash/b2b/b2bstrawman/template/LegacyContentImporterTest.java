package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LegacyContentImporterTest {

  private LegacyContentImporter importer;

  @BeforeEach
  void setUp() {
    importer = new LegacyContentImporter();
  }

  @Test
  @SuppressWarnings("unchecked")
  void converts_paragraph() {
    var result = importer.convertHtml("<p>Hello world</p>");

    assertThat(result.get("type")).isEqualTo("doc");
    var content = (List<Map<String, Object>>) result.get("content");
    assertThat(content).hasSize(1);
    assertThat(content.getFirst().get("type")).isEqualTo("paragraph");

    var textNodes = (List<Map<String, Object>>) content.getFirst().get("content");
    assertThat(textNodes).hasSize(1);
    assertThat(textNodes.getFirst().get("type")).isEqualTo("text");
    assertThat(textNodes.getFirst().get("text")).isEqualTo("Hello world");
  }

  @Test
  @SuppressWarnings("unchecked")
  void converts_headings() {
    var result = importer.convertHtml("<h1>Title</h1><h2>Subtitle</h2><h3>Section</h3>");

    var content = (List<Map<String, Object>>) result.get("content");
    assertThat(content).hasSize(3);

    assertThat(content.get(0).get("type")).isEqualTo("heading");
    assertThat(((Map<String, Object>) content.get(0).get("attrs")).get("level")).isEqualTo(1);
    assertThat(content.get(1).get("type")).isEqualTo("heading");
    assertThat(((Map<String, Object>) content.get(1).get("attrs")).get("level")).isEqualTo(2);
    assertThat(content.get(2).get("type")).isEqualTo("heading");
    assertThat(((Map<String, Object>) content.get(2).get("attrs")).get("level")).isEqualTo(3);
  }

  @Test
  @SuppressWarnings("unchecked")
  void converts_bold_italic_underline_marks() {
    var result =
        importer.convertHtml("<p><strong>bold</strong> <em>italic</em> <u>underlined</u></p>");

    var content = (List<Map<String, Object>>) result.get("content");
    assertThat(content).hasSize(1);

    var paraContent = (List<Map<String, Object>>) content.getFirst().get("content");
    // bold text node
    var boldNode =
        paraContent.stream().filter(n -> "bold".equals(n.get("text"))).findFirst().orElseThrow();
    var boldMarks = (List<Map<String, Object>>) boldNode.get("marks");
    assertThat(boldMarks).extracting(m -> m.get("type")).contains("bold");

    // italic text node
    var italicNode =
        paraContent.stream().filter(n -> "italic".equals(n.get("text"))).findFirst().orElseThrow();
    var italicMarks = (List<Map<String, Object>>) italicNode.get("marks");
    assertThat(italicMarks).extracting(m -> m.get("type")).contains("italic");

    // underlined text node
    var underlineNode =
        paraContent.stream()
            .filter(n -> "underlined".equals(n.get("text")))
            .findFirst()
            .orElseThrow();
    var underlineMarks = (List<Map<String, Object>>) underlineNode.get("marks");
    assertThat(underlineMarks).extracting(m -> m.get("type")).contains("underline");
  }

  @Test
  @SuppressWarnings("unchecked")
  void converts_link() {
    var result = importer.convertHtml("<p><a href=\"https://example.com\">click here</a></p>");

    var content = (List<Map<String, Object>>) result.get("content");
    var paraContent = (List<Map<String, Object>>) content.getFirst().get("content");
    var linkNode = paraContent.getFirst();

    assertThat(linkNode.get("text")).isEqualTo("click here");
    var marks = (List<Map<String, Object>>) linkNode.get("marks");
    assertThat(marks).hasSize(1);
    assertThat(marks.getFirst().get("type")).isEqualTo("link");
    var attrs = (Map<String, Object>) marks.getFirst().get("attrs");
    assertThat(attrs.get("href")).isEqualTo("https://example.com");
  }

  @Test
  @SuppressWarnings("unchecked")
  void converts_bullet_list() {
    var result = importer.convertHtml("<ul><li>Item 1</li><li>Item 2</li></ul>");

    var content = (List<Map<String, Object>>) result.get("content");
    assertThat(content).hasSize(1);
    assertThat(content.getFirst().get("type")).isEqualTo("bulletList");

    var items = (List<Map<String, Object>>) content.getFirst().get("content");
    assertThat(items).hasSize(2);
    assertThat(items.get(0).get("type")).isEqualTo("listItem");
    assertThat(items.get(1).get("type")).isEqualTo("listItem");

    // Each list item should contain a paragraph
    var firstItemContent = (List<Map<String, Object>>) items.get(0).get("content");
    assertThat(firstItemContent.getFirst().get("type")).isEqualTo("paragraph");
  }

  @Test
  @SuppressWarnings("unchecked")
  void converts_ordered_list() {
    var result = importer.convertHtml("<ol><li>First</li><li>Second</li></ol>");

    var content = (List<Map<String, Object>>) result.get("content");
    assertThat(content).hasSize(1);
    assertThat(content.getFirst().get("type")).isEqualTo("orderedList");
  }

  @Test
  @SuppressWarnings("unchecked")
  void converts_table() {
    var result =
        importer.convertHtml("<table><tr><th>Header</th></tr><tr><td>Cell</td></tr></table>");

    var content = (List<Map<String, Object>>) result.get("content");
    assertThat(content).hasSize(1);
    assertThat(content.getFirst().get("type")).isEqualTo("table");

    var rows = (List<Map<String, Object>>) content.getFirst().get("content");
    assertThat(rows).hasSize(2);

    // First row has a header cell
    var headerRow = (List<Map<String, Object>>) rows.get(0).get("content");
    assertThat(headerRow.getFirst().get("type")).isEqualTo("tableHeader");

    // Second row has a data cell
    var dataRow = (List<Map<String, Object>>) rows.get(1).get("content");
    assertThat(dataRow.getFirst().get("type")).isEqualTo("tableCell");
  }

  @Test
  @SuppressWarnings("unchecked")
  void converts_thymeleaf_variable() {
    var result =
        importer.convertHtml("<p>Dear <span th:text=\"${customer.name}\">placeholder</span>,</p>");

    var content = (List<Map<String, Object>>) result.get("content");
    var paraContent = (List<Map<String, Object>>) content.getFirst().get("content");

    var variableNode =
        paraContent.stream()
            .filter(n -> "variable".equals(n.get("type")))
            .findFirst()
            .orElseThrow();
    var attrs = (Map<String, Object>) variableNode.get("attrs");
    assertThat(attrs.get("key")).isEqualTo("customer.name");
  }

  @Test
  @SuppressWarnings("unchecked")
  void converts_horizontal_rule_and_hard_break() {
    var result = importer.convertHtml("<p>Before</p><hr><p>Line 1<br>Line 2</p>");

    var content = (List<Map<String, Object>>) result.get("content");
    assertThat(content).hasSizeGreaterThanOrEqualTo(3);

    assertThat(content.get(0).get("type")).isEqualTo("paragraph");
    assertThat(content.get(1).get("type")).isEqualTo("horizontalRule");
    assertThat(content.get(2).get("type")).isEqualTo("paragraph");

    // Check for hardBreak in the last paragraph
    var lastParaContent = (List<Map<String, Object>>) content.get(2).get("content");
    var hasHardBreak = lastParaContent.stream().anyMatch(n -> "hardBreak".equals(n.get("type")));
    assertThat(hasHardBreak).isTrue();
  }

  @Test
  @SuppressWarnings("unchecked")
  void returns_empty_doc_for_null_input() {
    var result = importer.convertHtml(null);

    assertThat(result.get("type")).isEqualTo("doc");
    var content = (List<Map<String, Object>>) result.get("content");
    assertThat(content).hasSize(1);
    assertThat(content.getFirst().get("type")).isEqualTo("paragraph");
  }

  @Test
  @SuppressWarnings("unchecked")
  void returns_empty_doc_for_blank_input() {
    var result = importer.convertHtml("   ");

    assertThat(result.get("type")).isEqualTo("doc");
    var content = (List<Map<String, Object>>) result.get("content");
    assertThat(content).hasSize(1);
    assertThat(content.getFirst().get("type")).isEqualTo("paragraph");
  }
}
