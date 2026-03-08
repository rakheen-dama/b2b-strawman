import { describe, expect, it } from "vitest";
import {
  extractVariableKeys,
  findMissingVariables,
} from "../client-renderer";
import type { TiptapNode } from "../client-renderer";

function doc(...content: TiptapNode[]): TiptapNode {
  return { type: "doc", content };
}

function paragraph(...content: TiptapNode[]): TiptapNode {
  return { type: "paragraph", content };
}

function text(t: string): TiptapNode {
  return { type: "text", text: t };
}

function variable(key: string): TiptapNode {
  return { type: "variable", attrs: { key } };
}

describe("extractVariableKeys", () => {
  it("extracts keys from a simple document", () => {
    const node = doc(
      paragraph(text("Hello "), variable("org.name"), text("!")),
    );
    expect(extractVariableKeys(node)).toEqual(["org.name"]);
  });

  it("extracts keys from multiple paragraphs", () => {
    const node = doc(
      paragraph(variable("project.name")),
      paragraph(variable("customer.name")),
    );
    expect(extractVariableKeys(node)).toEqual(
      expect.arrayContaining(["project.name", "customer.name"]),
    );
    expect(extractVariableKeys(node)).toHaveLength(2);
  });

  it("deduplicates keys", () => {
    const node = doc(
      paragraph(variable("org.name")),
      paragraph(variable("org.name")),
    );
    expect(extractVariableKeys(node)).toEqual(["org.name"]);
  });

  it("returns empty array for document with no variables", () => {
    const node = doc(paragraph(text("No variables here")));
    expect(extractVariableKeys(node)).toEqual([]);
  });

  it("returns empty array for empty document", () => {
    const node = doc();
    expect(extractVariableKeys(node)).toEqual([]);
  });

  it("extracts keys from clause bodies when clauses map is provided", () => {
    const clauseBody: TiptapNode = doc(
      paragraph(variable("customer.email"), variable("org.name")),
    );
    const clauses = new Map<string, TiptapNode>([["clause-1", clauseBody]]);
    const node = doc(
      paragraph(variable("project.name")),
      { type: "clauseBlock", attrs: { clauseId: "clause-1" } },
    );
    const keys = extractVariableKeys(node, clauses);
    expect(keys).toEqual(
      expect.arrayContaining(["project.name", "customer.email", "org.name"]),
    );
    expect(keys).toHaveLength(3);
  });

  it("ignores clause blocks when no clauses map is provided", () => {
    const node = doc(
      paragraph(variable("project.name")),
      { type: "clauseBlock", attrs: { clauseId: "clause-1" } },
    );
    const keys = extractVariableKeys(node);
    expect(keys).toEqual(["project.name"]);
  });
});

describe("findMissingVariables", () => {
  it("identifies variables with no value in context", () => {
    const node = doc(
      paragraph(variable("project.name"), variable("project.budget")),
    );
    const context = { project: { name: "My Project" } };
    const missing = findMissingVariables(node, context);
    expect(missing).toEqual(new Set(["project.budget"]));
  });

  it("returns empty set when all variables resolve", () => {
    const node = doc(paragraph(variable("project.name")));
    const context = { project: { name: "My Project" } };
    const missing = findMissingVariables(node, context);
    expect(missing.size).toBe(0);
  });

  it("marks all variables missing when context is empty", () => {
    const node = doc(
      paragraph(variable("project.name"), variable("customer.name")),
    );
    const missing = findMissingVariables(node, {});
    expect(missing).toEqual(
      new Set(["project.name", "customer.name"]),
    );
  });

  it("handles nested context paths", () => {
    const node = doc(
      paragraph(
        variable("project.customFields.tax_ref"),
        variable("project.name"),
      ),
    );
    const context = {
      project: { name: "Test", customFields: { tax_ref: "TR-001" } },
    };
    const missing = findMissingVariables(node, context);
    expect(missing.size).toBe(0);
  });

  it("marks nested custom field missing when not set", () => {
    const node = doc(paragraph(variable("project.customFields.tax_ref")));
    const context = { project: { name: "Test", customFields: {} } };
    const missing = findMissingVariables(node, context);
    expect(missing).toEqual(new Set(["project.customFields.tax_ref"]));
  });

  it("detects missing variables inside clause bodies", () => {
    const clauseBody: TiptapNode = doc(
      paragraph(variable("customer.email")),
    );
    const clauses = new Map<string, TiptapNode>([["c1", clauseBody]]);
    const node = doc(
      paragraph(variable("project.name")),
      { type: "clauseBlock", attrs: { clauseId: "c1" } },
    );
    const context = { project: { name: "Test" } };
    const missing = findMissingVariables(node, context, clauses);
    expect(missing).toEqual(new Set(["customer.email"]));
  });
});
