import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TemplateEditorForm } from "@/components/templates/TemplateEditorForm";
import type { TemplateDetailResponse } from "@/lib/types";

const mockUpdateTemplate = vi.fn();

vi.mock("@/app/(app)/org/[slug]/settings/templates/actions", () => ({
  updateTemplateAction: (...args: unknown[]) => mockUpdateTemplate(...args),
  previewTemplateAction: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    refresh: vi.fn(),
  }),
}));

const TEMPLATE: TemplateDetailResponse = {
  id: "tpl-1",
  name: "Engagement Letter",
  slug: "engagement-letter",
  description: "Standard engagement letter",
  category: "ENGAGEMENT_LETTER",
  primaryEntityType: "PROJECT",
  content: "<h1>Hello ${project.name}</h1>",
  css: "h1 { color: blue; }",
  source: "ORG_CUSTOM",
  sourceTemplateId: null,
  packId: null,
  packTemplateKey: null,
  active: true,
  sortOrder: 0,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

describe("TemplateEditorForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders form fields with template data", () => {
    render(<TemplateEditorForm slug="acme" template={TEMPLATE} />);

    expect(screen.getByLabelText("Name")).toHaveValue("Engagement Letter");
    expect(screen.getByLabelText("Description")).toHaveValue(
      "Standard engagement letter",
    );
    expect(screen.getByLabelText("Content (HTML)")).toHaveValue(
      "<h1>Hello ${project.name}</h1>",
    );
    expect(screen.getByLabelText("Custom CSS")).toHaveValue(
      "h1 { color: blue; }",
    );
  });

  it("has monospace textareas for content and CSS", () => {
    render(<TemplateEditorForm slug="acme" template={TEMPLATE} />);

    const contentTextarea = screen.getByLabelText("Content (HTML)");
    const cssTextarea = screen.getByLabelText("Custom CSS");

    expect(contentTextarea.className).toContain("font-mono");
    expect(cssTextarea.className).toContain("font-mono");
  });

  it("renders variable reference panel", () => {
    render(<TemplateEditorForm slug="acme" template={TEMPLATE} />);

    expect(screen.getByText("Variable Reference")).toBeInTheDocument();
  });

  it("calls updateTemplateAction on save", async () => {
    mockUpdateTemplate.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    render(<TemplateEditorForm slug="acme" template={TEMPLATE} />);

    await user.click(screen.getByRole("button", { name: "Save Changes" }));

    await waitFor(() => {
      expect(mockUpdateTemplate).toHaveBeenCalledWith("acme", "tpl-1", {
        name: "Engagement Letter",
        description: "Standard engagement letter",
        content: "<h1>Hello ${project.name}</h1>",
        css: "h1 { color: blue; }",
        requiredContextFields: null,
      });
    });
  });

  it("shows success message after save", async () => {
    mockUpdateTemplate.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    render(<TemplateEditorForm slug="acme" template={TEMPLATE} />);

    await user.click(screen.getByRole("button", { name: "Save Changes" }));

    await waitFor(() => {
      expect(
        screen.getByText("Template saved successfully."),
      ).toBeInTheDocument();
    });
  });

  it("shows template info sidebar", () => {
    render(<TemplateEditorForm slug="acme" template={TEMPLATE} />);

    expect(screen.getByText("Template Info")).toBeInTheDocument();
    expect(screen.getByText("ENGAGEMENT LETTER")).toBeInTheDocument();
    expect(screen.getByText("PROJECT")).toBeInTheDocument();
  });

  it("renders required fields selector section", () => {
    render(<TemplateEditorForm slug="acme" template={TEMPLATE} />);
    expect(screen.getByLabelText("Entity type")).toBeInTheDocument();
    expect(screen.getByLabelText("Field slug")).toBeInTheDocument();
  });

  it("adds a required field reference", async () => {
    const user = userEvent.setup();
    render(<TemplateEditorForm slug="acme" template={{ ...TEMPLATE, requiredContextFields: [] }} />);

    await user.type(screen.getByLabelText("Field slug"), "email");
    await user.click(screen.getByRole("button", { name: "Add" }));

    expect(screen.getByText("project.email")).toBeInTheDocument();
  });

  it("removes a required field reference", async () => {
    const user = userEvent.setup();
    render(<TemplateEditorForm slug="acme" template={{ ...TEMPLATE, requiredContextFields: [{ entity: "project", field: "name" }] }} />);

    expect(screen.getByText("project.name")).toBeInTheDocument();
    await user.click(screen.getByLabelText("Remove project.name"));
    expect(screen.queryByText("project.name")).not.toBeInTheDocument();
  });
});
