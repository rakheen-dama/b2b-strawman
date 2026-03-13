import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TemplatesContent } from "@/app/(app)/org/[slug]/settings/templates/templates-content";
import type { TemplateListResponse } from "@/lib/types";

const mockCloneTemplate = vi.fn();
const mockDeactivateTemplate = vi.fn();
const mockResetTemplate = vi.fn();

vi.mock("@/app/(app)/org/[slug]/settings/templates/template-crud-actions", () => ({
  cloneTemplateAction: (...args: unknown[]) => mockCloneTemplate(...args),
  deactivateTemplateAction: (...args: unknown[]) =>
    mockDeactivateTemplate(...args),
  resetTemplateAction: (...args: unknown[]) => mockResetTemplate(...args),
}));

vi.mock("@/app/(app)/org/[slug]/settings/templates/template-support-actions", () => ({
  uploadLogoAction: vi.fn(),
  deleteLogoAction: vi.fn(),
  saveBrandingAction: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    refresh: vi.fn(),
  }),
}));

const TIPTAP_TEMPLATE: TemplateListResponse = {
  id: "tpl-1",
  name: "Standard Engagement Letter",
  slug: "standard-engagement-letter",
  description: "Default engagement letter template",
  category: "ENGAGEMENT_LETTER",
  primaryEntityType: "PROJECT",
  source: "PLATFORM",
  sourceTemplateId: null,
  active: true,
  sortOrder: 0,
  format: "TIPTAP",
  docxFileName: null,
  docxFileSize: null,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const DOCX_TEMPLATE: TemplateListResponse = {
  id: "tpl-2",
  name: "Word Engagement Letter",
  slug: "word-engagement-letter",
  description: "DOCX engagement letter",
  category: "ENGAGEMENT_LETTER",
  primaryEntityType: "PROJECT",
  source: "CUSTOM",
  sourceTemplateId: null,
  active: true,
  sortOrder: 1,
  format: "DOCX",
  docxFileName: "engagement-letter.docx",
  docxFileSize: 52480,
  createdAt: "2026-02-01T00:00:00Z",
  updatedAt: "2026-02-01T00:00:00Z",
};

afterEach(() => cleanup());

describe("Template List — Format Badges", () => {
  it("shows format badge for both Tiptap and DOCX templates", () => {
    render(
      <TemplatesContent
        slug="test-org"
        templates={[TIPTAP_TEMPLATE, DOCX_TEMPLATE]}
        settings={null}
        canManage={true}
      />,
    );

    expect(screen.getByText("Tiptap")).toBeInTheDocument();
    expect(screen.getByText("Word")).toBeInTheDocument();
  });

  it("shows DOCX file name and size for Word templates", () => {
    render(
      <TemplatesContent
        slug="test-org"
        templates={[DOCX_TEMPLATE]}
        settings={null}
        canManage={true}
      />,
    );

    expect(screen.getByText(/engagement-letter\.docx/)).toBeInTheDocument();
    expect(screen.getByText(/51 KB/)).toBeInTheDocument();
  });
});

describe("Template List — Format Filter", () => {
  it("filters templates by format when selecting Word", async () => {
    const user = userEvent.setup();

    render(
      <TemplatesContent
        slug="test-org"
        templates={[TIPTAP_TEMPLATE, DOCX_TEMPLATE]}
        settings={null}
        canManage={true}
      />,
    );

    // Both templates visible initially
    expect(screen.getByText("Standard Engagement Letter")).toBeInTheDocument();
    expect(screen.getByText("Word Engagement Letter")).toBeInTheDocument();

    // Open the format filter and select "Word"
    const filterTrigger = screen.getByTestId("format-filter");
    await user.click(filterTrigger);
    const wordOption = screen.getByRole("option", { name: "Word" });
    await user.click(wordOption);

    // Only DOCX template visible
    expect(screen.getByText("Word Engagement Letter")).toBeInTheDocument();
    expect(
      screen.queryByText("Standard Engagement Letter"),
    ).not.toBeInTheDocument();
  });

  it("shows all templates when All Formats is selected", async () => {
    const user = userEvent.setup();

    render(
      <TemplatesContent
        slug="test-org"
        templates={[TIPTAP_TEMPLATE, DOCX_TEMPLATE]}
        settings={null}
        canManage={true}
      />,
    );

    // Open filter, select "Word" first
    const filterTrigger = screen.getByTestId("format-filter");
    await user.click(filterTrigger);
    const wordOption = screen.getByRole("option", { name: "Word" });
    await user.click(wordOption);

    // Then select "All Formats"
    await user.click(filterTrigger);
    const allOption = screen.getByRole("option", { name: "All Formats" });
    await user.click(allOption);

    // Both visible again
    expect(screen.getByText("Standard Engagement Letter")).toBeInTheDocument();
    expect(screen.getByText("Word Engagement Letter")).toBeInTheDocument();
  });
});
