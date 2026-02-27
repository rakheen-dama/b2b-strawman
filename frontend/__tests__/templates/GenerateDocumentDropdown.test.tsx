import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { GenerateDocumentDropdown } from "@/components/templates/GenerateDocumentDropdown";
import type { TemplateListResponse } from "@/lib/types";

// Mock server-only (imported transitively via template-clause-actions -> api)
vi.mock("server-only", () => ({}));

const mockPreviewTemplate = vi.fn();
const mockGenerateDocument = vi.fn();

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => "/org/acme/projects/proj-1",
}));

vi.mock("@/app/(app)/org/[slug]/settings/templates/actions", () => ({
  previewTemplateAction: (...args: unknown[]) => mockPreviewTemplate(...args),
  generateDocumentAction: (...args: unknown[]) => mockGenerateDocument(...args),
}));

vi.mock("@/lib/actions/template-clause-actions", () => ({
  getTemplateClauses: vi.fn().mockResolvedValue([]),
}));

const TEMPLATES: TemplateListResponse[] = [
  {
    id: "tpl-1",
    name: "Engagement Letter",
    slug: "engagement-letter",
    description: "Standard engagement letter",
    category: "ENGAGEMENT_LETTER",
    primaryEntityType: "PROJECT",
    source: "PLATFORM",
    sourceTemplateId: null,
    active: true,
    sortOrder: 0,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  },
  {
    id: "tpl-2",
    name: "NDA",
    slug: "nda",
    description: "Non-disclosure agreement",
    category: "NDA",
    primaryEntityType: "PROJECT",
    source: "PLATFORM",
    sourceTemplateId: null,
    active: true,
    sortOrder: 1,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  },
];

describe("GenerateDocumentDropdown", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockPreviewTemplate.mockResolvedValue({
      success: true,
      html: "<h1>Preview</h1>",
    });
  });

  afterEach(() => {
    cleanup();
  });

  it("renders Generate Document dropdown button when templates exist", () => {
    render(
      <GenerateDocumentDropdown
        templates={TEMPLATES}
        entityId="proj-1"
        entityType="PROJECT"
      />,
    );

    const button = screen.getByRole("button", { name: /Generate Document/i });
    expect(button).toBeInTheDocument();
    expect(button).not.toBeDisabled();
  });

  it("renders disabled button when no templates available", () => {
    render(
      <GenerateDocumentDropdown
        templates={[]}
        entityId="proj-1"
        entityType="PROJECT"
      />,
    );

    const button = screen.getByRole("button", { name: /Generate Document/i });
    expect(button).toBeDisabled();
  });

  it("clicking template item opens GenerateDocumentDialog with correct props", async () => {
    const user = userEvent.setup();

    render(
      <GenerateDocumentDropdown
        templates={TEMPLATES}
        entityId="proj-1"
        entityType="PROJECT"
      />,
    );

    // Open dropdown
    await user.click(
      screen.getByRole("button", { name: /Generate Document/i }),
    );

    // Click on Engagement Letter
    await waitFor(() => {
      expect(screen.getByText("Engagement Letter")).toBeInTheDocument();
    });
    await user.click(screen.getByText("Engagement Letter"));

    // Dialog should open with the template name
    await waitFor(() => {
      expect(
        screen.getByText("Generate: Engagement Letter"),
      ).toBeInTheDocument();
    });

    // Preview should be called with correct args
    await waitFor(() => {
      expect(mockPreviewTemplate).toHaveBeenCalledWith("tpl-1", "proj-1", undefined);
    });
  });
});
