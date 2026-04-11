import { describe, it, expect } from "vitest";
import { evaluateCondition, renderTiptapToHtml } from "@/components/editor/client-renderer";
import type { TiptapNode } from "@/components/editor/client-renderer";

describe("evaluateCondition", () => {
  it("eq returns true when values match", () => {
    expect(evaluateCondition("company", "eq", "company")).toBe(true);
  });

  it("eq returns false when values differ", () => {
    expect(evaluateCondition("individual", "eq", "company")).toBe(false);
  });

  it("neq returns true when values differ", () => {
    expect(evaluateCondition("individual", "neq", "company")).toBe(true);
  });

  it("neq returns false when values match", () => {
    expect(evaluateCondition("company", "neq", "company")).toBe(false);
  });

  it("isEmpty returns true when value is null", () => {
    expect(evaluateCondition(null, "isEmpty", "")).toBe(true);
  });

  it("isEmpty returns true when value is empty string", () => {
    expect(evaluateCondition("", "isEmpty", "")).toBe(true);
  });

  it("isEmpty returns true when value is whitespace", () => {
    expect(evaluateCondition("  ", "isEmpty", "")).toBe(true);
  });

  it("isEmpty returns false when value is present", () => {
    expect(evaluateCondition("VAT123", "isEmpty", "")).toBe(false);
  });

  it("isNotEmpty returns true when value is present", () => {
    expect(evaluateCondition("VAT123", "isNotEmpty", "")).toBe(true);
  });

  it("isNotEmpty returns false when value is null", () => {
    expect(evaluateCondition(null, "isNotEmpty", "")).toBe(false);
  });

  it("isNotEmpty returns false when value is empty", () => {
    expect(evaluateCondition("", "isNotEmpty", "")).toBe(false);
  });

  it("contains returns true when substring matches", () => {
    expect(evaluateCondition("Tax Advisory 2026", "contains", "Tax")).toBe(true);
  });

  it("contains returns false when substring not found", () => {
    expect(evaluateCondition("Advisory 2026", "contains", "Tax")).toBe(false);
  });

  it("in returns true when value is in comma-separated list", () => {
    expect(evaluateCondition("trust", "in", "company, trust, cc")).toBe(true);
  });

  it("in returns false when value is not in list", () => {
    expect(evaluateCondition("individual", "in", "company, trust, cc")).toBe(false);
  });

  it("unknown operator returns true (fail-open)", () => {
    expect(evaluateCondition("anything", "unknownOp", "")).toBe(true);
  });

  it("handles numeric values via string coercion", () => {
    expect(evaluateCondition(42, "eq", "42")).toBe(true);
  });
});

describe("renderTiptapToHtml with conditionalBlock", () => {
  const clauses = new Map<string, TiptapNode>();

  function conditionalBlockNode(
    fieldKey: string,
    operator: string,
    value: string,
    text: string
  ): TiptapNode {
    return {
      type: "conditionalBlock",
      attrs: { fieldKey, operator, value },
      content: [
        {
          type: "paragraph",
          content: [{ type: "text", text }],
        },
      ],
    };
  }

  it("renders content when isNotEmpty and value present", () => {
    const doc: TiptapNode = {
      type: "doc",
      content: [conditionalBlockNode("customer.taxNumber", "isNotEmpty", "", "Tax details")],
    };
    const context = { customer: { taxNumber: "VAT123" } };
    const html = renderTiptapToHtml(doc, context, clauses);
    expect(html).toContain("<p>Tax details</p>");
  });

  it("hides content when isNotEmpty and value missing", () => {
    const doc: TiptapNode = {
      type: "doc",
      content: [conditionalBlockNode("customer.taxNumber", "isNotEmpty", "", "Tax details")],
    };
    const context = { customer: {} };
    const html = renderTiptapToHtml(doc, context, clauses);
    expect(html).not.toContain("Tax details");
  });

  it("renders content when isEmpty and value missing", () => {
    const doc: TiptapNode = {
      type: "doc",
      content: [conditionalBlockNode("customer.taxNumber", "isEmpty", "", "No tax number")],
    };
    const context = { customer: {} };
    const html = renderTiptapToHtml(doc, context, clauses);
    expect(html).toContain("<p>No tax number</p>");
  });

  it("renders content when eq matches", () => {
    const doc: TiptapNode = {
      type: "doc",
      content: [conditionalBlockNode("customer.type", "eq", "company", "Company terms")],
    };
    const context = { customer: { type: "company" } };
    const html = renderTiptapToHtml(doc, context, clauses);
    expect(html).toContain("<p>Company terms</p>");
  });

  it("hides content when eq does not match", () => {
    const doc: TiptapNode = {
      type: "doc",
      content: [conditionalBlockNode("customer.type", "eq", "company", "Company terms")],
    };
    const context = { customer: { type: "individual" } };
    const html = renderTiptapToHtml(doc, context, clauses);
    expect(html).not.toContain("Company terms");
  });

  it("renders content when in matches one of list", () => {
    const doc: TiptapNode = {
      type: "doc",
      content: [conditionalBlockNode("customer.type", "in", "company, trust", "Entity clause")],
    };
    const context = { customer: { type: "trust" } };
    const html = renderTiptapToHtml(doc, context, clauses);
    expect(html).toContain("<p>Entity clause</p>");
  });

  it("renders unconfigured block unconditionally", () => {
    const doc: TiptapNode = {
      type: "doc",
      content: [conditionalBlockNode("", "isNotEmpty", "", "Always shown")],
    };
    const html = renderTiptapToHtml(doc, {}, clauses);
    expect(html).toContain("<p>Always shown</p>");
  });

  it("evaluates nested conditional blocks independently", () => {
    const innerBlock = conditionalBlockNode("customer.taxNumber", "isNotEmpty", "", "Tax info");
    const outerBlock: TiptapNode = {
      type: "conditionalBlock",
      attrs: { fieldKey: "customer.type", operator: "eq", value: "company" },
      content: [innerBlock],
    };
    const doc: TiptapNode = { type: "doc", content: [outerBlock] };

    // Both met
    const html1 = renderTiptapToHtml(
      doc,
      { customer: { type: "company", taxNumber: "VAT123" } },
      clauses
    );
    expect(html1).toContain("Tax info");

    // Outer met, inner not
    const html2 = renderTiptapToHtml(doc, { customer: { type: "company" } }, clauses);
    expect(html2).not.toContain("Tax info");

    // Outer not met
    const html3 = renderTiptapToHtml(
      doc,
      { customer: { type: "individual", taxNumber: "VAT123" } },
      clauses
    );
    expect(html3).not.toContain("Tax info");
  });
});
