import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import {
  renderTiptapToHtml,
  buildPreviewContext,
  extractClauseIds,
} from "@/components/editor/client-renderer";
import type { TiptapNode } from "@/components/editor/client-renderer";

// Mock server-only since some transitive imports may reference it
vi.mock("server-only", () => ({}));

afterEach(() => {
  cleanup();
});

describe("renderTiptapToHtml (client-side renderer)", () => {
  it("renders a simple paragraph with text", () => {
    const doc: TiptapNode = {
      type: "doc",
      content: [
        {
          type: "paragraph",
          content: [{ type: "text", text: "Hello World" }],
        },
      ],
    };
    const html = renderTiptapToHtml(doc, {}, new Map());
    expect(html).toContain("<p>Hello World</p>");
    expect(html).toContain("<!DOCTYPE html>");
    expect(html).toContain("<style>");
  });

  it("resolves variables from nested context", () => {
    const doc: TiptapNode = {
      type: "doc",
      content: [
        {
          type: "paragraph",
          content: [
            { type: "variable", attrs: { key: "project.name" } },
          ],
        },
      ],
    };
    const context = { project: { name: "Test Project" } };
    const html = renderTiptapToHtml(doc, context, new Map());
    expect(html).toContain("Test Project");
  });

  it("escapes HTML in text and variables", () => {
    const doc: TiptapNode = {
      type: "doc",
      content: [
        {
          type: "paragraph",
          content: [{ type: "text", text: "<script>alert('xss')</script>" }],
        },
      ],
    };
    const html = renderTiptapToHtml(doc, {}, new Map());
    expect(html).not.toContain("<script>");
    expect(html).toContain("&lt;script&gt;");
  });

  it("renders clauseBlock nodes with clause body", () => {
    const clauseBody: TiptapNode = {
      type: "doc",
      content: [
        {
          type: "paragraph",
          content: [{ type: "text", text: "Clause content here" }],
        },
      ],
    };
    const doc: TiptapNode = {
      type: "doc",
      content: [
        {
          type: "clauseBlock",
          attrs: { clauseId: "clause-1", slug: "nda-standard" },
        },
      ],
    };
    const clauses = new Map<string, TiptapNode>();
    clauses.set("clause-1", clauseBody);

    const html = renderTiptapToHtml(doc, {}, clauses);
    expect(html).toContain("Clause content here");
    expect(html).toContain('class="clause-block"');
    expect(html).toContain('data-clause-slug="nda-standard"');
  });

  it("renders loopTable with data source", () => {
    const doc: TiptapNode = {
      type: "doc",
      content: [
        {
          type: "loopTable",
          attrs: {
            dataSource: "invoice.lines",
            columns: [
              { header: "Description", key: "description" },
              { header: "Amount", key: "amount" },
            ],
          },
        },
      ],
    };
    const context = {
      invoice: {
        lines: [
          { description: "Service A", amount: "100.00" },
          { description: "Service B", amount: "200.00" },
        ],
      },
    };
    const html = renderTiptapToHtml(doc, context, new Map());
    expect(html).toContain("<th>Description</th>");
    expect(html).toContain("<th>Amount</th>");
    expect(html).toContain("<td>Service A</td>");
    expect(html).toContain("<td>200.00</td>");
  });

  it("renders headings with correct level", () => {
    const doc: TiptapNode = {
      type: "doc",
      content: [
        {
          type: "heading",
          attrs: { level: 2 },
          content: [{ type: "text", text: "Section Title" }],
        },
      ],
    };
    const html = renderTiptapToHtml(doc, {}, new Map());
    expect(html).toContain("<h2>Section Title</h2>");
  });

  it("renders text with marks (bold, italic, underline, link)", () => {
    const doc: TiptapNode = {
      type: "doc",
      content: [
        {
          type: "paragraph",
          content: [
            {
              type: "text",
              text: "Bold text",
              marks: [{ type: "bold" }],
            },
            {
              type: "text",
              text: "Link text",
              marks: [{ type: "link", attrs: { href: "https://example.com" } }],
            },
          ],
        },
      ],
    };
    const html = renderTiptapToHtml(doc, {}, new Map());
    expect(html).toContain("<strong>Bold text</strong>");
    expect(html).toContain('<a href="https://example.com">Link text</a>');
  });

  it("returns empty string for missing variable", () => {
    const doc: TiptapNode = {
      type: "doc",
      content: [
        {
          type: "paragraph",
          content: [
            { type: "variable", attrs: { key: "nonexistent.path" } },
          ],
        },
      ],
    };
    const html = renderTiptapToHtml(doc, { project: { name: "Test" } }, new Map());
    expect(html).toContain("<p></p>");
  });

  it("respects MAX_CLAUSE_DEPTH to prevent infinite recursion", () => {
    // Create a clause that references itself (would cause infinite recursion)
    const selfReferencing: TiptapNode = {
      type: "doc",
      content: [
        {
          type: "clauseBlock",
          attrs: { clauseId: "self", slug: "self-ref" },
        },
      ],
    };
    const clauses = new Map<string, TiptapNode>();
    clauses.set("self", selfReferencing);

    const doc: TiptapNode = {
      type: "doc",
      content: [
        {
          type: "clauseBlock",
          attrs: { clauseId: "self", slug: "self-ref" },
        },
      ],
    };
    // Should not throw; should stop at MAX_CLAUSE_DEPTH
    const html = renderTiptapToHtml(doc, {}, clauses);
    expect(html).toContain("max clause depth reached");
  });

  it("appends template CSS after default CSS", () => {
    const doc: TiptapNode = { type: "doc", content: [] };
    const html = renderTiptapToHtml(doc, {}, new Map(), "body { color: red; }");
    expect(html).toContain("body { color: red; }");
    // Default CSS is also present
    expect(html).toContain("font-family: 'Helvetica Neue'");
  });

  it("strips </style> from template CSS for safety", () => {
    const doc: TiptapNode = { type: "doc", content: [] };
    const html = renderTiptapToHtml(
      doc,
      {},
      new Map(),
      "body { color: red; }</style><script>alert('xss')</script>",
    );
    expect(html).not.toContain("</style><script>");
  });
});

describe("buildPreviewContext", () => {
  it("wraps project data under project key", () => {
    const data = { id: "1", name: "My Project" };
    const ctx = buildPreviewContext("PROJECT", data);
    expect(ctx).toEqual({ project: { id: "1", name: "My Project" } });
  });

  it("wraps customer data under customer key", () => {
    const data = { id: "2", name: "Acme Corp", email: "acme@example.com" };
    const ctx = buildPreviewContext("CUSTOMER", data);
    expect(ctx).toEqual({ customer: data });
  });

  it("wraps invoice data with customer from invoice fields", () => {
    const data = {
      id: "3",
      invoiceNumber: "INV-001",
      customerName: "Acme Corp",
      customerEmail: "acme@example.com",
    };
    const ctx = buildPreviewContext("INVOICE", data);
    expect(ctx.invoice).toBe(data);
    expect(ctx.customer).toEqual({ name: "Acme Corp", email: "acme@example.com" });
  });
});

describe("extractClauseIds", () => {
  it("finds all clauseBlock clauseIds in a document", () => {
    const doc: TiptapNode = {
      type: "doc",
      content: [
        {
          type: "paragraph",
          content: [{ type: "text", text: "Hello" }],
        },
        {
          type: "clauseBlock",
          attrs: { clauseId: "abc-123", slug: "terms" },
        },
        {
          type: "clauseBlock",
          attrs: { clauseId: "def-456", slug: "nda" },
        },
      ],
    };
    const ids = extractClauseIds(doc);
    expect(ids).toEqual(["abc-123", "def-456"]);
  });

  it("returns empty array when no clauseBlocks exist", () => {
    const doc: TiptapNode = {
      type: "doc",
      content: [
        {
          type: "paragraph",
          content: [{ type: "text", text: "Hello" }],
        },
      ],
    };
    expect(extractClauseIds(doc)).toEqual([]);
  });
});

describe("PreviewPanel", () => {
  it("renders an iframe with sandboxed srcDoc", async () => {
    // Dynamic import to avoid issues with "use client" directive
    const { PreviewPanel } = await import("@/components/editor/PreviewPanel");
    const testHtml = "<html><body><p>Test content</p></body></html>";
    render(<PreviewPanel html={testHtml} />);
    const iframe = screen.getByTitle("Document Preview") as HTMLIFrameElement;
    expect(iframe).toBeInTheDocument();
    expect(iframe.getAttribute("sandbox")).toBe("");
    expect(iframe.getAttribute("srcdoc")).toBe(testHtml);
  });

  it("applies additional className", async () => {
    const { PreviewPanel } = await import("@/components/editor/PreviewPanel");
    const { container } = render(
      <PreviewPanel html="<p>Test</p>" className="extra-class" />,
    );
    const wrapper = container.firstChild as HTMLElement;
    expect(wrapper.className).toContain("extra-class");
  });
});
