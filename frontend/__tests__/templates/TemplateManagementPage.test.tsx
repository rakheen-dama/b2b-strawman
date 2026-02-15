import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TemplatesContent } from "@/app/(app)/org/[slug]/settings/templates/templates-content";
import type { TemplateListResponse } from "@/lib/types";

const mockCloneTemplate = vi.fn();
const mockDeactivateTemplate = vi.fn();
const mockResetTemplate = vi.fn();

vi.mock("@/app/(app)/org/[slug]/settings/templates/actions", () => ({
  cloneTemplateAction: (...args: unknown[]) => mockCloneTemplate(...args),
  deactivateTemplateAction: (...args: unknown[]) =>
    mockDeactivateTemplate(...args),
  resetTemplateAction: (...args: unknown[]) => mockResetTemplate(...args),
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

const PLATFORM_TEMPLATE: TemplateListResponse = {
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
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const CUSTOM_TEMPLATE: TemplateListResponse = {
  id: "tpl-2",
  name: "Custom SOW",
  slug: "custom-sow",
  description: "Customized statement of work",
  category: "STATEMENT_OF_WORK",
  primaryEntityType: "PROJECT",
  source: "ORG_CUSTOM",
  sourceTemplateId: "tpl-source-1",
  active: true,
  sortOrder: 0,
  createdAt: "2026-01-02T00:00:00Z",
  updatedAt: "2026-01-02T00:00:00Z",
};

describe("TemplatesContent", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders templates grouped by category", () => {
    render(
      <TemplatesContent
        slug="acme"
        templates={[PLATFORM_TEMPLATE, CUSTOM_TEMPLATE]}
        settings={null}
        canManage={true}
      />,
    );

    expect(screen.getByText("Engagement Letter")).toBeInTheDocument();
    expect(screen.getByText("Statement of Work")).toBeInTheDocument();
    expect(
      screen.getByText("Standard Engagement Letter"),
    ).toBeInTheDocument();
    expect(screen.getByText("Custom SOW")).toBeInTheDocument();
  });

  it("shows Platform badge for PLATFORM templates", () => {
    render(
      <TemplatesContent
        slug="acme"
        templates={[PLATFORM_TEMPLATE]}
        settings={null}
        canManage={true}
      />,
    );

    expect(screen.getByText("Platform")).toBeInTheDocument();
  });

  it("shows Custom badge for ORG_CUSTOM templates", () => {
    render(
      <TemplatesContent
        slug="acme"
        templates={[CUSTOM_TEMPLATE]}
        settings={null}
        canManage={true}
      />,
    );

    expect(screen.getByText("Custom")).toBeInTheDocument();
  });

  it("shows New Template button when canManage is true", () => {
    render(
      <TemplatesContent
        slug="acme"
        templates={[PLATFORM_TEMPLATE]}
        settings={null}
        canManage={true}
      />,
    );

    expect(
      screen.getByRole("button", { name: /new template/i }),
    ).toBeInTheDocument();
  });

  it("hides New Template button when canManage is false", () => {
    render(
      <TemplatesContent
        slug="acme"
        templates={[PLATFORM_TEMPLATE]}
        settings={null}
        canManage={false}
      />,
    );

    expect(
      screen.queryByRole("button", { name: /new template/i }),
    ).not.toBeInTheDocument();
  });

  it("shows empty state when no templates exist", () => {
    render(
      <TemplatesContent
        slug="acme"
        templates={[]}
        settings={null}
        canManage={true}
      />,
    );

    expect(screen.getByText(/no templates found/i)).toBeInTheDocument();
  });

  it("shows actions menu for PLATFORM template with Clone action", async () => {
    const user = userEvent.setup();

    render(
      <TemplatesContent
        slug="acme"
        templates={[PLATFORM_TEMPLATE]}
        settings={null}
        canManage={true}
      />,
    );

    // Find the actions button in the table row
    const row = screen
      .getByText("Standard Engagement Letter")
      .closest("tr")!;
    const actionsBtn = within(row).getByRole("button");
    await user.click(actionsBtn);

    expect(screen.getByText("Clone & Customize")).toBeInTheDocument();
  });

  it("shows actions menu for ORG_CUSTOM template with Edit and Reset actions", async () => {
    const user = userEvent.setup();

    render(
      <TemplatesContent
        slug="acme"
        templates={[CUSTOM_TEMPLATE]}
        settings={null}
        canManage={true}
      />,
    );

    const row = screen.getByText("Custom SOW").closest("tr")!;
    const actionsBtn = within(row).getByRole("button");
    await user.click(actionsBtn);

    expect(screen.getByText("Edit")).toBeInTheDocument();
    expect(screen.getByText("Reset to Default")).toBeInTheDocument();
  });
});
