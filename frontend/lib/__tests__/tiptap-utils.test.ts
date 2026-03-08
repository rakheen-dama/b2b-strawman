import { describe, expect, it } from "vitest";
import { extractTextFromBody } from "../tiptap-utils";

describe("extractTextFromBody", () => {
  it("extracts text from simple paragraph", () => {
    const body = {
      type: "doc",
      content: [
        {
          type: "paragraph",
          content: [{ type: "text", text: "Hello world" }],
        },
      ],
    };
    expect(extractTextFromBody(body)).toBe("Hello world");
  });

  it("renders variable nodes as {key} placeholders", () => {
    const body = {
      type: "doc",
      content: [
        {
          type: "paragraph",
          content: [
            { type: "text", text: "Issued by " },
            { type: "variable", attrs: { key: "org.name" } },
            { type: "text", text: " to " },
            { type: "variable", attrs: { key: "customer.name" } },
          ],
        },
      ],
    };
    expect(extractTextFromBody(body)).toBe(
      "Issued by {org.name} to {customer.name}",
    );
  });

  it("handles mixed text and variable nodes across paragraphs", () => {
    const body = {
      type: "doc",
      content: [
        {
          type: "paragraph",
          content: [
            { type: "text", text: "Project: " },
            { type: "variable", attrs: { key: "project.name" } },
          ],
        },
        {
          type: "paragraph",
          content: [
            { type: "text", text: "Budget: " },
            { type: "variable", attrs: { key: "budget.amount" } },
          ],
        },
      ],
    };
    expect(extractTextFromBody(body)).toBe(
      "Project: {project.name}\nBudget: {budget.amount}",
    );
  });

  it("handles variable node with missing attrs", () => {
    const body = {
      type: "doc",
      content: [
        {
          type: "paragraph",
          content: [
            { type: "text", text: "Hello " },
            { type: "variable" },
          ],
        },
      ],
    };
    // extractTextFromBody trims the result, so trailing space is removed
    expect(extractTextFromBody(body)).toBe("Hello");
  });

  it("returns null for empty content", () => {
    expect(extractTextFromBody({ type: "doc", content: [] })).toBeNull();
  });

  it("returns null for no content key", () => {
    expect(extractTextFromBody({ type: "doc" })).toBeNull();
  });
});
