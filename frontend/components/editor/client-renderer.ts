/**
 * Client-side Tiptap JSON to HTML renderer.
 * TypeScript mirror of backend TiptapRenderer — same logic, same output.
 */

export interface TiptapNode {
  type: string;
  content?: TiptapNode[];
  text?: string;
  attrs?: Record<string, unknown>;
  marks?: Array<{ type: string; attrs?: Record<string, unknown> }>;
}

const MAX_CLAUSE_DEPTH = 10;

const DEFAULT_CSS = `@page {
    size: A4;
    margin: 20mm;
}

body {
    font-family: 'Helvetica Neue', Arial, sans-serif;
    font-size: 11pt;
    line-height: 1.5;
    color: #333;
}

.header {
    border-bottom: 3px solid var(--brand-color, #1a1a2e);
    padding-bottom: 10px;
    margin-bottom: 20px;
}

.footer {
    font-size: 9pt;
    color: #666;
    margin-top: 20px;
    padding-top: 10px;
    border-top: 1px solid #ccc;
}

table {
    width: 100%;
    border-collapse: collapse;
    margin-bottom: 20px;
}

table th {
    background-color: #f5f5f5;
    padding: 8px;
    text-align: left;
    border-bottom: 2px solid #ddd;
}

table td {
    padding: 8px;
    border-bottom: 1px solid #eee;
}

h1, h2, h3 {
    color: #1a1a2e;
    margin-top: 20px;
    margin-bottom: 10px;
}

h1 { font-size: 18pt; }
h2 { font-size: 14pt; }
h3 { font-size: 12pt; }`;

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function resolveVariable(
  key: string | null | undefined,
  context: Record<string, unknown>,
): string {
  if (!key || key.trim() === "") return "";
  const segments = key.split(".");
  let current: unknown = context;
  for (const segment of segments) {
    if (typeof current !== "object" || current === null) return "";
    current = (current as Record<string, unknown>)[segment];
    if (current == null) return "";
  }
  return escapeHtml(String(current));
}

function resolveDataSource(
  path: string | null | undefined,
  context: Record<string, unknown>,
): Record<string, unknown>[] | null {
  if (!path || path.trim() === "") return null;
  const segments = path.split(".");
  let current: unknown = context;
  for (const segment of segments) {
    if (typeof current !== "object" || current === null) return null;
    current = (current as Record<string, unknown>)[segment];
    if (current == null) return null;
  }
  if (Array.isArray(current)) {
    return current as Record<string, unknown>[];
  }
  return null;
}

function renderText(node: TiptapNode): string {
  const text = node.text ?? "";
  const escaped = escapeHtml(text);
  const marks = node.marks;
  if (!marks || marks.length === 0) return escaped;

  let openTags = "";
  let closeTags = "";
  for (const mark of marks) {
    const markAttrs = mark.attrs ?? {};
    switch (mark.type) {
      case "bold":
        openTags += "<strong>";
        closeTags = "</strong>" + closeTags;
        break;
      case "italic":
        openTags += "<em>";
        closeTags = "</em>" + closeTags;
        break;
      case "underline":
        openTags += "<u>";
        closeTags = "</u>" + closeTags;
        break;
      case "link": {
        const raw = String(markAttrs.href ?? "");
        const href =
          raw.startsWith("https://") ||
          raw.startsWith("http://") ||
          raw.startsWith("mailto:") ||
          raw.startsWith("#")
            ? escapeHtml(raw)
            : "";
        openTags += `<a href="${href}">`;
        closeTags = "</a>" + closeTags;
        break;
      }
    }
  }
  return openTags + escaped + closeTags;
}

function renderLoopTable(
  attrs: Record<string, unknown>,
  context: Record<string, unknown>,
): string {
  const dataSource = attrs.dataSource as string | undefined;
  const columns = (attrs.columns ?? []) as Array<{
    header?: string;
    key?: string;
  }>;
  const rows = resolveDataSource(dataSource, context);

  let sb = "<table>";
  sb += "<thead><tr>";
  for (const col of columns) {
    sb += "<th>" + escapeHtml(col.header ?? "") + "</th>";
  }
  sb += "</tr></thead>";
  sb += "<tbody>";
  if (rows) {
    for (const row of rows) {
      sb += "<tr>";
      for (const col of columns) {
        const val = col.key ? row[col.key] : null;
        const cellText = val != null ? escapeHtml(String(val)) : "";
        sb += "<td>" + cellText + "</td>";
      }
      sb += "</tr>";
    }
  }
  sb += "</tbody></table>";
  return sb;
}

function renderTableCell(
  tag: string,
  attrs: Record<string, unknown>,
  node: TiptapNode,
  context: Record<string, unknown>,
  clauses: Map<string, TiptapNode>,
  depth: number,
): string {
  let sb = "<" + tag;
  const colspan = attrs.colspan;
  const rowspan = attrs.rowspan;
  if (typeof colspan === "number" && colspan > 1) {
    sb += ` colspan="${colspan}"`;
  }
  if (typeof rowspan === "number" && rowspan > 1) {
    sb += ` rowspan="${rowspan}"`;
  }
  sb += ">";
  sb += renderChildren(node, context, clauses, depth);
  sb += "</" + tag + ">";
  return sb;
}

function renderChildren(
  node: TiptapNode,
  context: Record<string, unknown>,
  clauses: Map<string, TiptapNode>,
  depth: number,
): string {
  if (!node.content) return "";
  let sb = "";
  for (const child of node.content) {
    sb += renderNode(child, context, clauses, depth);
  }
  return sb;
}

function wrapTag(
  tag: string,
  node: TiptapNode,
  context: Record<string, unknown>,
  clauses: Map<string, TiptapNode>,
  depth: number,
): string {
  return "<" + tag + ">" + renderChildren(node, context, clauses, depth) + "</" + tag + ">";
}

function renderNode(
  node: TiptapNode,
  context: Record<string, unknown>,
  clauses: Map<string, TiptapNode>,
  depth: number,
): string {
  const type = node.type;
  if (!type) return "";
  const attrs = node.attrs ?? {};

  switch (type) {
    case "doc":
      return renderChildren(node, context, clauses, depth);

    case "heading": {
      const rawLevel = attrs.level ?? 1;
      const level = Math.max(1, Math.min(6, typeof rawLevel === "number" ? rawLevel : 1));
      return (
        "<h" + level + ">" + renderChildren(node, context, clauses, depth) + "</h" + level + ">"
      );
    }

    case "paragraph":
      return "<p>" + renderChildren(node, context, clauses, depth) + "</p>";

    case "text":
      return renderText(node);

    case "variable": {
      const key = attrs.key as string | undefined;
      return resolveVariable(key, context);
    }

    case "clauseBlock": {
      if (depth >= MAX_CLAUSE_DEPTH) {
        const slug = escapeHtml(String(attrs.slug ?? "unknown"));
        return `<!-- max clause depth reached: ${slug} -->`;
      }
      const clauseIdStr = attrs.clauseId as string | undefined;
      const slug = String(attrs.slug ?? "unknown");
      if (!clauseIdStr) {
        return `<!-- clause not found: ${escapeHtml(slug)} -->`;
      }
      const clauseBody = clauses.get(clauseIdStr);
      if (clauseBody) {
        return (
          `<div class="clause-block" data-clause-slug="${escapeHtml(slug)}">` +
          renderNode(clauseBody, context, clauses, depth + 1) +
          "</div>"
        );
      }
      return `<!-- clause not found: ${escapeHtml(slug)} -->`;
    }

    case "loopTable":
      return renderLoopTable(attrs, context);

    case "bulletList":
      return wrapTag("ul", node, context, clauses, depth);

    case "orderedList":
      return wrapTag("ol", node, context, clauses, depth);

    case "listItem":
      return wrapTag("li", node, context, clauses, depth);

    case "table":
      return wrapTag("table", node, context, clauses, depth);

    case "tableRow":
      return wrapTag("tr", node, context, clauses, depth);

    case "tableCell":
      return renderTableCell("td", attrs, node, context, clauses, depth);

    case "tableHeader":
      return renderTableCell("th", attrs, node, context, clauses, depth);

    case "horizontalRule":
      return "<hr/>";

    case "hardBreak":
      return "<br/>";

    default:
      return renderChildren(node, context, clauses, depth);
  }
}

/**
 * Renders a Tiptap JSON document to a complete HTML document string.
 *
 * @param doc - The Tiptap JSON document node
 * @param context - Nested context map for variable resolution (e.g., { project: { name: "..." } })
 * @param clauses - Map of clauseId (string) to clause body TiptapNode
 * @param templateCss - Optional custom CSS to append after default styles
 * @returns Complete HTML document string
 */
export function renderTiptapToHtml(
  doc: TiptapNode,
  context: Record<string, unknown>,
  clauses: Map<string, TiptapNode>,
  templateCss?: string,
): string {
  const body = renderNode(doc, context, clauses, 0);
  const safeCss = templateCss ? templateCss.replace(/<\/style>/gi, "") : "";

  return (
    '<!DOCTYPE html>\n<html><head>\n<meta charset="UTF-8"/>\n<style>' +
    DEFAULT_CSS +
    "\n" +
    safeCss +
    "</style>\n</head><body>\n" +
    body +
    "\n</body></html>"
  );
}

/**
 * Builds a preview context map from entity data, matching backend context builder output shape.
 */
export function buildPreviewContext(
  entityType: "PROJECT" | "CUSTOMER" | "INVOICE",
  entityData: Record<string, unknown>,
): Record<string, unknown> {
  switch (entityType) {
    case "PROJECT":
      return { project: entityData };
    case "CUSTOMER":
      return { customer: entityData };
    case "INVOICE":
      return {
        invoice: entityData,
        customer: {
          name: entityData.customerName,
          email: entityData.customerEmail,
        },
      };
  }
}

/**
 * Extracts all clauseBlock node clauseIds from a Tiptap document tree.
 */
export function extractClauseIds(node: TiptapNode): string[] {
  const ids: string[] = [];
  function walk(n: TiptapNode) {
    if (n.type === "clauseBlock" && n.attrs?.clauseId) {
      ids.push(String(n.attrs.clauseId));
    }
    if (n.content) {
      for (const child of n.content) {
        walk(child);
      }
    }
  }
  walk(node);
  return ids;
}
