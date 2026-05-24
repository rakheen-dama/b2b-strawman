package io.b2mash.b2b.b2bstrawman.integration.ai.skill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static utility methods for constructing Tiptap JSON node structures.
 *
 * <p>Used by AI skill generators ({@code AiDraftDocumentGenerator}, {@code
 * AiReviewReportGenerator}) to build Tiptap-compatible document JSON without duplicating node
 * construction logic.
 */
public final class TiptapNodeBuilder {

  private TiptapNodeBuilder() {}

  /** Wrap content nodes in a top-level Tiptap document node. */
  public static Map<String, Object> buildDocument(List<Map<String, Object>> content) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "doc");
    node.put("content", content);
    return node;
  }

  /** Build a heading node at the specified level. */
  public static Map<String, Object> buildHeading(String text, int level) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "heading");
    node.put("attrs", Map.of("level", level));
    node.put("content", List.of(text(text)));
    return node;
  }

  /** Build a paragraph node with plain text content. */
  public static Map<String, Object> buildParagraph(String text) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "paragraph");
    if (text != null && !text.isEmpty()) {
      node.put("content", List.of(text(text)));
    }
    return node;
  }

  /** Build a paragraph node with a bold label followed by a plain-text value. */
  public static Map<String, Object> buildBoldParagraph(String label, String value) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "paragraph");
    node.put("content", List.of(boldText(label + ": "), text(value != null ? value : "")));
    return node;
  }

  /** Build a bullet list node from a list of plain-text items. */
  public static Map<String, Object> buildBulletList(List<String> items) {
    var listItems =
        items.stream()
            .map(
                item -> {
                  var listItem = new LinkedHashMap<String, Object>();
                  listItem.put("type", "listItem");
                  listItem.put("content", List.of(buildParagraph(item)));
                  return (Map<String, Object>) listItem;
                })
            .toList();

    var node = new LinkedHashMap<String, Object>();
    node.put("type", "bulletList");
    node.put("content", listItems);
    return node;
  }

  /** Build a plain text node. */
  public static Map<String, Object> text(String value) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "text");
    node.put("text", value);
    return node;
  }

  /** Build a bold text node. */
  public static Map<String, Object> boldText(String value) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "text");
    node.put("marks", List.of(Map.of("type", "bold")));
    node.put("text", value);
    return node;
  }
}
