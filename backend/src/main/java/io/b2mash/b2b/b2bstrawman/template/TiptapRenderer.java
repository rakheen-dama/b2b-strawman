package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.clause.Clause;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Stateless JSON tree walker that converts a Tiptap document (stored as {@code Map<String, Object>}
 * JSONB) to a complete HTML document string. Replaces Thymeleaf document rendering.
 *
 * <p>Variables are resolved via dot-path map lookups — no expression language, no injection
 * surface. Clause blocks are rendered recursively using the same walker and context. Loop tables
 * iterate a named collection from the context map.
 */
@Service
public class TiptapRenderer {

  private static final int MAX_CLAUSE_DEPTH = 10;

  private static final Safelist LEGACY_HTML_SAFELIST =
      new Safelist()
          .addTags(
              "p", "h1", "h2", "h3", "strong", "em", "u", "a", "ul", "ol", "li", "table", "thead",
              "tbody", "tr", "td", "th", "hr", "br", "span", "div")
          .addAttributes("a", "href")
          .addProtocols("a", "href", "https", "http", "mailto");

  private final String defaultCss;

  /** Production constructor — loads CSS from classpath resource. */
  @Autowired
  public TiptapRenderer(@Value("classpath:templates/document-default.css") Resource cssResource)
      throws IOException {
    this(new String(cssResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
  }

  /** Package-private constructor for unit tests — accepts CSS string directly. */
  TiptapRenderer(String defaultCss) {
    this.defaultCss = defaultCss;
  }

  /**
   * Renders a Tiptap document JSON tree to a complete HTML document string.
   *
   * @param document the root Tiptap JSON node (type: "doc"), as a {@code Map<String, Object>}
   * @param context the rendering context (dot-path variable lookups)
   * @param clauses resolved clauses by UUID for clauseBlock rendering
   * @param templateCss template-specific CSS to append after the default CSS; may be null
   * @return a complete {@code <!DOCTYPE html>} document string
   */
  public String render(
      Map<String, Object> document,
      Map<String, Object> context,
      Map<UUID, Clause> clauses,
      String templateCss) {
    var body = new StringBuilder();
    renderNode(document, context, clauses, body, 0);

    String safeCss = templateCss != null ? templateCss.replaceAll("(?i)</style>", "") : "";

    return "<!DOCTYPE html>\n<html><head>\n<meta charset=\"UTF-8\"/>\n<style>"
        + defaultCss
        + "\n"
        + safeCss
        + "</style>\n</head><body>\n"
        + body
        + "\n</body></html>";
  }

  @SuppressWarnings("unchecked")
  private void renderNode(
      Map<String, Object> node,
      Map<String, Object> context,
      Map<UUID, Clause> clauses,
      StringBuilder sb,
      int depth) {
    String type = (String) node.get("type");
    if (type == null) return;
    Map<String, Object> attrs = (Map<String, Object>) node.getOrDefault("attrs", Map.of());

    switch (type) {
      case "doc" -> renderChildren(node, context, clauses, sb, depth);
      case "heading" -> {
        Object rawLevel = attrs.getOrDefault("level", 1);
        int level = rawLevel instanceof Number n ? n.intValue() : 1;
        sb.append("<h").append(level).append(">");
        renderChildren(node, context, clauses, sb, depth);
        sb.append("</h").append(level).append(">");
      }
      case "paragraph" -> {
        sb.append("<p>");
        renderChildren(node, context, clauses, sb, depth);
        sb.append("</p>");
      }
      case "text" -> renderText(node, sb);
      case "variable" -> {
        String key = (String) attrs.get("key");
        sb.append(resolveVariable(key, context));
      }
      case "clauseBlock" -> {
        if (depth >= MAX_CLAUSE_DEPTH) {
          String slug = (String) attrs.getOrDefault("slug", "unknown");
          sb.append("<!-- max clause depth reached: ")
              .append(HtmlUtils.htmlEscape(slug))
              .append(" -->");
          return;
        }
        String clauseIdStr = (String) attrs.get("clauseId");
        String slug = (String) attrs.getOrDefault("slug", "unknown");
        if (clauseIdStr == null) {
          sb.append("<!-- clause not found: ").append(HtmlUtils.htmlEscape(slug)).append(" -->");
          return;
        }
        UUID clauseId;
        try {
          clauseId = UUID.fromString(clauseIdStr);
        } catch (IllegalArgumentException e) {
          sb.append("<!-- invalid clauseId -->");
          return;
        }
        Clause clause = clauses.get(clauseId);
        Map<String, Object> bodyJson = clause != null ? clause.getBody() : null;
        if (bodyJson != null) {
          sb.append("<div class=\"clause-block\" data-clause-slug=\"")
              .append(HtmlUtils.htmlEscape(slug))
              .append("\">");
          renderNode(bodyJson, context, clauses, sb, depth + 1);
          sb.append("</div>");
        } else {
          sb.append("<!-- clause not found: ").append(HtmlUtils.htmlEscape(slug)).append(" -->");
        }
      }
      case "loopTable" -> renderLoopTable(attrs, context, sb);
      case "bulletList" -> wrapTag("ul", node, context, clauses, sb, depth);
      case "orderedList" -> wrapTag("ol", node, context, clauses, sb, depth);
      case "listItem" -> wrapTag("li", node, context, clauses, sb, depth);
      case "table" -> wrapTag("table", node, context, clauses, sb, depth);
      case "tableRow" -> wrapTag("tr", node, context, clauses, sb, depth);
      case "tableCell" -> renderTableCell("td", attrs, node, context, clauses, sb, depth);
      case "tableHeader" -> renderTableCell("th", attrs, node, context, clauses, sb, depth);
      case "horizontalRule" -> sb.append("<hr/>");
      case "hardBreak" -> sb.append("<br/>");
      case "legacyHtml" ->
          sb.append(Jsoup.clean((String) attrs.getOrDefault("html", ""), LEGACY_HTML_SAFELIST));
      default -> renderChildren(node, context, clauses, sb, depth);
    }
  }

  @SuppressWarnings("unchecked")
  private void renderChildren(
      Map<String, Object> node,
      Map<String, Object> context,
      Map<UUID, Clause> clauses,
      StringBuilder sb,
      int depth) {
    var content = (List<Map<String, Object>>) node.get("content");
    if (content == null) return;
    for (var child : content) {
      renderNode(child, context, clauses, sb, depth);
    }
  }

  @SuppressWarnings("unchecked")
  private void renderText(Map<String, Object> node, StringBuilder sb) {
    String text = (String) node.getOrDefault("text", "");
    String escaped = HtmlUtils.htmlEscape(text);
    var marks = (List<Map<String, Object>>) node.get("marks");
    if (marks == null || marks.isEmpty()) {
      sb.append(escaped);
      return;
    }
    // Build opening/closing tag pairs for each mark
    var openTags = new StringBuilder();
    var closeTags = new StringBuilder();
    for (var mark : marks) {
      String markType = (String) mark.get("type");
      Map<String, Object> markAttrs = (Map<String, Object>) mark.getOrDefault("attrs", Map.of());
      switch (markType) {
        case "bold" -> {
          openTags.append("<strong>");
          closeTags.insert(0, "</strong>");
        }
        case "italic" -> {
          openTags.append("<em>");
          closeTags.insert(0, "</em>");
        }
        case "underline" -> {
          openTags.append("<u>");
          closeTags.insert(0, "</u>");
        }
        case "link" -> {
          String raw = (String) markAttrs.getOrDefault("href", "");
          String href =
              (raw.startsWith("https://")
                      || raw.startsWith("http://")
                      || raw.startsWith("mailto:")
                      || raw.startsWith("#"))
                  ? HtmlUtils.htmlEscape(raw)
                  : "";
          openTags.append("<a href=\"").append(href).append("\">");
          closeTags.insert(0, "</a>");
        }
          // Unknown mark types: ignore
      }
    }
    sb.append(openTags).append(escaped).append(closeTags);
  }

  @SuppressWarnings("unchecked")
  private String resolveVariable(String key, Map<String, Object> context) {
    if (key == null || key.isBlank()) return "";
    String[] segments = key.split("\\.");
    Object current = context;
    for (String segment : segments) {
      if (!(current instanceof Map)) return "";
      current = ((Map<?, ?>) current).get(segment);
      if (current == null) return "";
    }
    return HtmlUtils.htmlEscape(String.valueOf(current));
  }

  @SuppressWarnings("unchecked")
  private void renderLoopTable(
      Map<String, Object> attrs, Map<String, Object> context, StringBuilder sb) {
    String dataSource = (String) attrs.get("dataSource");
    var columns = (List<Map<String, Object>>) attrs.getOrDefault("columns", List.of());

    List<Map<String, Object>> rows = resolveDataSource(dataSource, context);

    sb.append("<table>");
    sb.append("<thead><tr>");
    for (var col : columns) {
      String header = (String) col.getOrDefault("header", "");
      sb.append("<th>").append(HtmlUtils.htmlEscape(header)).append("</th>");
    }
    sb.append("</tr></thead>");
    sb.append("<tbody>");
    if (rows != null) {
      for (var row : rows) {
        sb.append("<tr>");
        for (var col : columns) {
          String colKey = (String) col.get("key");
          Object val = colKey != null ? row.get(colKey) : null;
          String cellText = val != null ? HtmlUtils.htmlEscape(String.valueOf(val)) : "";
          sb.append("<td>").append(cellText).append("</td>");
        }
        sb.append("</tr>");
      }
    }
    sb.append("</tbody></table>");
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> resolveDataSource(String path, Map<String, Object> context) {
    if (path == null || path.isBlank()) return null;
    String[] segments = path.split("\\.");
    Object current = context;
    for (String segment : segments) {
      if (!(current instanceof Map)) return null;
      current = ((Map<?, ?>) current).get(segment);
      if (current == null) return null;
    }
    if (current instanceof List<?> list) {
      // Safe unchecked cast — JSONB deserialization produces List<Map<String,Object>>
      return (List<Map<String, Object>>) list;
    }
    return null;
  }

  private void wrapTag(
      String tag,
      Map<String, Object> node,
      Map<String, Object> context,
      Map<UUID, Clause> clauses,
      StringBuilder sb,
      int depth) {
    sb.append("<").append(tag).append(">");
    renderChildren(node, context, clauses, sb, depth);
    sb.append("</").append(tag).append(">");
  }

  @SuppressWarnings("unchecked")
  private void renderTableCell(
      String tag,
      Map<String, Object> attrs,
      Map<String, Object> node,
      Map<String, Object> context,
      Map<UUID, Clause> clauses,
      StringBuilder sb,
      int depth) {
    sb.append("<").append(tag);
    Object colspan = attrs.get("colspan");
    Object rowspan = attrs.get("rowspan");
    if (colspan instanceof Number n && n.intValue() > 1) {
      sb.append(" colspan=\"").append(n.intValue()).append("\"");
    }
    if (rowspan instanceof Number n && n.intValue() > 1) {
      sb.append(" rowspan=\"").append(n.intValue()).append("\"");
    }
    sb.append(">");
    renderChildren(node, context, clauses, sb, depth);
    sb.append("</").append(tag).append(">");
  }
}
