package io.b2mash.b2b.b2bstrawman.template;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fluent builder for constructing Tiptap JSON documents in tests. Produces valid {@code Map<String,
 * Object>} structures that {@link TiptapRenderer} can consume.
 *
 * <p>Usage: {@code TestDocumentBuilder.doc().heading(1, "Title").paragraph("Body").variable(
 * "project.name").build()}
 */
public final class TestDocumentBuilder {

  private final List<Map<String, Object>> content = new ArrayList<>();

  private TestDocumentBuilder() {}

  /** Creates a new document builder. */
  public static TestDocumentBuilder doc() {
    return new TestDocumentBuilder();
  }

  /** Adds a heading node with the given level and text. */
  public TestDocumentBuilder heading(int level, String text) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "heading");
    node.put("attrs", Map.of("level", level));
    node.put("content", List.of(textNode(text)));
    content.add(node);
    return this;
  }

  /** Adds a paragraph node with plain text content. */
  public TestDocumentBuilder paragraph(String text) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "paragraph");
    node.put("content", List.of(textNode(text)));
    content.add(node);
    return this;
  }

  /** Adds a paragraph containing a variable node. */
  public TestDocumentBuilder variable(String key) {
    var variableNode = new LinkedHashMap<String, Object>();
    variableNode.put("type", "variable");
    variableNode.put("attrs", Map.of("key", key));

    var paragraph = new LinkedHashMap<String, Object>();
    paragraph.put("type", "paragraph");
    paragraph.put("content", List.of(variableNode));
    content.add(paragraph);
    return this;
  }

  /** Adds a clauseBlock node with the given attributes. */
  public TestDocumentBuilder clauseBlock(
      UUID clauseId, String slug, String title, boolean required) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "clauseBlock");
    node.put(
        "attrs",
        Map.of(
            "clauseId", clauseId.toString(),
            "slug", slug,
            "title", title,
            "required", required));
    content.add(node);
    return this;
  }

  /** Adds a loopTable node with the given data source and column definitions. */
  public TestDocumentBuilder loopTable(String dataSource, List<Map<String, String>> columns) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "loopTable");
    node.put("attrs", Map.of("dataSource", dataSource, "columns", columns));
    content.add(node);
    return this;
  }

  /** Adds a raw text node wrapped in a paragraph. */
  public TestDocumentBuilder text(String content) {
    return paragraph(content);
  }

  /** Adds a paragraph with bold text. */
  public TestDocumentBuilder bold(String content) {
    var boldTextNode = new LinkedHashMap<String, Object>();
    boldTextNode.put("type", "text");
    boldTextNode.put("text", content);
    boldTextNode.put("marks", List.of(Map.of("type", "bold")));

    var paragraph = new LinkedHashMap<String, Object>();
    paragraph.put("type", "paragraph");
    paragraph.put("content", List.of(boldTextNode));
    this.content.add(paragraph);
    return this;
  }

  /** Builds the Tiptap document as a {@code Map<String, Object>}. */
  public Map<String, Object> build() {
    var doc = new LinkedHashMap<String, Object>();
    doc.put("type", "doc");
    doc.put("content", List.copyOf(content));
    return doc;
  }

  private static Map<String, Object> textNode(String text) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "text");
    node.put("text", text);
    return node;
  }
}
