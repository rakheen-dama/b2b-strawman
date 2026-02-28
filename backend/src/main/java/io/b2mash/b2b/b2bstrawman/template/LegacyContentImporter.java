package io.b2mash.b2b.b2bstrawman.template;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Service;

/**
 * Converts HTML content (from legacyHtml Tiptap nodes) to proper Tiptap JSON nodes. Used by {@link
 * LegacyContentImportRunner} to upgrade "simple" legacy content from the V48 migration to native
 * Tiptap nodes.
 */
@Service
public class LegacyContentImporter {

  private static final Pattern THYMELEAF_VAR_PATTERN =
      Pattern.compile("\\$\\{([a-zA-Z][a-zA-Z0-9_.]*)}");

  private static final Set<String> BLOCK_TAGS =
      Set.of(
          "p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "table", "thead", "tbody",
          "tr", "td", "th", "hr", "br");

  /**
   * Converts an HTML string to a Tiptap document JSON structure.
   *
   * @param html the HTML string to convert
   * @return a Tiptap "doc" node as {@code Map<String, Object>}
   */
  public Map<String, Object> convertHtml(String html) {
    if (html == null || html.isBlank()) {
      return docNode(List.of(paragraphNode(List.of())));
    }

    var body = Jsoup.parseBodyFragment(html).body();
    var content = convertChildren(body, List.of());

    if (content.isEmpty()) {
      content = List.of(paragraphNode(List.of()));
    }

    return docNode(content);
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> convertChildren(
      Element parent, List<Map<String, Object>> marks) {
    var result = new ArrayList<Map<String, Object>>();

    for (Node child : parent.childNodes()) {
      if (child instanceof TextNode textNode) {
        String text = textNode.getWholeText();
        if (!text.isBlank()) {
          result.addAll(createTextNodes(text, marks));
        }
      } else if (child instanceof Element el) {
        result.addAll(convertElement(el, marks));
      }
    }

    return result;
  }

  private List<Map<String, Object>> convertElement(
      Element el, List<Map<String, Object>> parentMarks) {
    String tag = el.tagName().toLowerCase();

    return switch (tag) {
      case "p" -> List.of(paragraphNode(convertInlineContent(el, parentMarks)));
      case "h1" -> List.of(headingNode(1, convertInlineContent(el, parentMarks)));
      case "h2" -> List.of(headingNode(2, convertInlineContent(el, parentMarks)));
      case "h3" -> List.of(headingNode(3, convertInlineContent(el, parentMarks)));
      case "h4" -> List.of(headingNode(4, convertInlineContent(el, parentMarks)));
      case "h5" -> List.of(headingNode(5, convertInlineContent(el, parentMarks)));
      case "h6" -> List.of(headingNode(6, convertInlineContent(el, parentMarks)));
      case "ul" -> List.of(bulletListNode(convertListItems(el)));
      case "ol" -> List.of(orderedListNode(convertListItems(el)));
      case "li" -> List.of(listItemNode(convertListItemContent(el, parentMarks)));
      case "table" -> List.of(tableNode(convertTableContent(el)));
      case "thead", "tbody" -> convertTableContent(el);
      case "tr" -> List.of(tableRowNode(convertRowCells(el)));
      case "td" -> List.of(tableCellNode(convertCellContent(el, parentMarks)));
      case "th" -> List.of(tableHeaderNode(convertCellContent(el, parentMarks)));
      case "hr" -> List.of(horizontalRuleNode());
      case "br" -> List.of(hardBreakNode());
      case "strong", "b" -> {
        var newMarks = appendMark(parentMarks, Map.of("type", "bold"));
        yield convertInlineContent(el, newMarks);
      }
      case "em", "i" -> {
        var newMarks = appendMark(parentMarks, Map.of("type", "italic"));
        yield convertInlineContent(el, newMarks);
      }
      case "u" -> {
        var newMarks = appendMark(parentMarks, Map.of("type", "underline"));
        yield convertInlineContent(el, newMarks);
      }
      case "a" -> {
        String href = el.attr("href");
        var linkMark = new LinkedHashMap<String, Object>();
        linkMark.put("type", "link");
        linkMark.put("attrs", Map.of("href", href));
        var newMarks = appendMark(parentMarks, linkMark);
        yield convertInlineContent(el, newMarks);
      }
      case "span" -> {
        if (el.hasAttr("th:text")) {
          String thText = el.attr("th:text");
          Matcher matcher = THYMELEAF_VAR_PATTERN.matcher(thText);
          if (matcher.find()) {
            yield List.of(variableNode(matcher.group(1)));
          }
        }
        yield convertInlineContent(el, parentMarks);
      }
      default -> convertInlineContent(el, parentMarks);
    };
  }

  /**
   * Convert inline content of an element. Walks children, accumulating marks for inline elements.
   */
  private List<Map<String, Object>> convertInlineContent(
      Element parent, List<Map<String, Object>> marks) {
    var result = new ArrayList<Map<String, Object>>();

    for (Node child : parent.childNodes()) {
      if (child instanceof TextNode textNode) {
        String text = textNode.getWholeText();
        if (!text.isEmpty()) {
          result.addAll(createTextNodes(text, marks));
        }
      } else if (child instanceof Element el) {
        result.addAll(convertElement(el, marks));
      }
    }

    return result;
  }

  private List<Map<String, Object>> convertListItems(Element listEl) {
    var items = new ArrayList<Map<String, Object>>();
    for (Element child : listEl.children()) {
      if ("li".equals(child.tagName().toLowerCase())) {
        items.add(listItemNode(convertListItemContent(child, List.of())));
      }
    }
    return items;
  }

  /**
   * Convert the content of a list item. If the li contains block elements, convert them directly.
   * Otherwise wrap inline content in a paragraph.
   */
  private List<Map<String, Object>> convertListItemContent(
      Element li, List<Map<String, Object>> marks) {
    boolean hasBlockChild = false;
    for (Element child : li.children()) {
      if (BLOCK_TAGS.contains(child.tagName().toLowerCase())) {
        hasBlockChild = true;
        break;
      }
    }

    if (hasBlockChild) {
      return convertChildren(li, marks);
    } else {
      var inlineContent = convertInlineContent(li, marks);
      if (inlineContent.isEmpty()) {
        return List.of(paragraphNode(List.of()));
      }
      return List.of(paragraphNode(inlineContent));
    }
  }

  private List<Map<String, Object>> convertTableContent(Element tableOrSection) {
    var rows = new ArrayList<Map<String, Object>>();
    for (Element child : tableOrSection.children()) {
      String tag = child.tagName().toLowerCase();
      if ("tr".equals(tag)) {
        rows.add(tableRowNode(convertRowCells(child)));
      } else if ("thead".equals(tag) || "tbody".equals(tag)) {
        rows.addAll(convertTableContent(child));
      }
    }
    return rows;
  }

  private List<Map<String, Object>> convertRowCells(Element tr) {
    var cells = new ArrayList<Map<String, Object>>();
    for (Element child : tr.children()) {
      String tag = child.tagName().toLowerCase();
      if ("td".equals(tag)) {
        cells.add(tableCellNode(convertCellContent(child, List.of())));
      } else if ("th".equals(tag)) {
        cells.add(tableHeaderNode(convertCellContent(child, List.of())));
      }
    }
    return cells;
  }

  /**
   * Convert cell content. If the cell contains block elements, convert them directly. Otherwise
   * wrap inline content in a paragraph.
   */
  private List<Map<String, Object>> convertCellContent(
      Element cell, List<Map<String, Object>> marks) {
    boolean hasBlockChild = false;
    for (Element child : cell.children()) {
      if (BLOCK_TAGS.contains(child.tagName().toLowerCase())) {
        hasBlockChild = true;
        break;
      }
    }

    if (hasBlockChild) {
      return convertChildren(cell, marks);
    } else {
      var inlineContent = convertInlineContent(cell, marks);
      if (inlineContent.isEmpty()) {
        return List.of(paragraphNode(List.of()));
      }
      return List.of(paragraphNode(inlineContent));
    }
  }

  /** Create text nodes from a string, handling whitespace normalization. */
  private List<Map<String, Object>> createTextNodes(String text, List<Map<String, Object>> marks) {
    // Normalize whitespace but preserve meaningful content
    String normalized = text.replaceAll("\\s+", " ");
    if (normalized.isEmpty() || normalized.equals(" ")) {
      return List.of();
    }

    var node = new LinkedHashMap<String, Object>();
    node.put("type", "text");
    node.put("text", normalized);
    if (!marks.isEmpty()) {
      node.put("marks", List.copyOf(marks));
    }
    return List.of(node);
  }

  private List<Map<String, Object>> appendMark(
      List<Map<String, Object>> existingMarks, Map<String, Object> newMark) {
    var combined = new ArrayList<>(existingMarks);
    combined.add(newMark);
    return combined;
  }

  // --- Node constructors ---

  private Map<String, Object> docNode(List<Map<String, Object>> content) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "doc");
    node.put("content", content);
    return node;
  }

  private Map<String, Object> paragraphNode(List<Map<String, Object>> content) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "paragraph");
    if (!content.isEmpty()) {
      node.put("content", content);
    }
    return node;
  }

  private Map<String, Object> headingNode(int level, List<Map<String, Object>> content) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "heading");
    node.put("attrs", Map.of("level", level));
    if (!content.isEmpty()) {
      node.put("content", content);
    }
    return node;
  }

  private Map<String, Object> bulletListNode(List<Map<String, Object>> items) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "bulletList");
    node.put("content", items);
    return node;
  }

  private Map<String, Object> orderedListNode(List<Map<String, Object>> items) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "orderedList");
    node.put("content", items);
    return node;
  }

  private Map<String, Object> listItemNode(List<Map<String, Object>> content) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "listItem");
    node.put("content", content);
    return node;
  }

  private Map<String, Object> tableNode(List<Map<String, Object>> rows) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "table");
    node.put("content", rows);
    return node;
  }

  private Map<String, Object> tableRowNode(List<Map<String, Object>> cells) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "tableRow");
    node.put("content", cells);
    return node;
  }

  private Map<String, Object> tableCellNode(List<Map<String, Object>> content) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "tableCell");
    node.put("content", content);
    return node;
  }

  private Map<String, Object> tableHeaderNode(List<Map<String, Object>> content) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "tableHeader");
    node.put("content", content);
    return node;
  }

  private Map<String, Object> variableNode(String key) {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "variable");
    node.put("attrs", Map.of("key", key));
    return node;
  }

  private Map<String, Object> horizontalRuleNode() {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "horizontalRule");
    return node;
  }

  private Map<String, Object> hardBreakNode() {
    var node = new LinkedHashMap<String, Object>();
    node.put("type", "hardBreak");
    return node;
  }
}
