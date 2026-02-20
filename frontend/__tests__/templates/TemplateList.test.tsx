import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TemplateList } from "@/components/templates/TemplateList";
import type { ProjectTemplateResponse } from "@/lib/api/templates";

const mockDeleteTemplate = vi.fn();
const mockDuplicateTemplate = vi.fn();

vi.mock(
  "@/app/(app)/org/[slug]/settings/project-templates/actions",
  () => ({
    deleteTemplateAction: (...args: unknown[]) => mockDeleteTemplate(...args),
    duplicateTemplateAction: (...args: unknown[]) => mockDuplicateTemplate(...args),
    createProjectTemplateAction: vi.fn(),
    updateProjectTemplateAction: vi.fn(),
  }),
);

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    refresh: vi.fn(),
  }),
}));

const SAMPLE_TEMPLATE: ProjectTemplateResponse = {
  id: "pt-1",
  name: "Monthly Accounting Package",
  namePattern: "{customer} — {month} {year}",
  description: "Standard monthly accounting workflow",
  billableDefault: true,
  source: "MANUAL",
  sourceProjectId: null,
  active: true,
  taskCount: 3,
  tagCount: 2,
  tasks: [],
  tags: [],
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const FROM_PROJECT_TEMPLATE: ProjectTemplateResponse = {
  id: "pt-2",
  name: "From Project Template",
  namePattern: "{customer} — {year}",
  description: null,
  billableDefault: false,
  source: "FROM_PROJECT",
  sourceProjectId: "proj-1",
  active: false,
  taskCount: 1,
  tagCount: 0,
  tasks: [],
  tags: [],
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

describe("TemplateList", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders empty state when no templates", () => {
    render(
      <TemplateList slug="acme" templates={[]} canManage={true} />,
    );
    expect(screen.getByText("No project templates yet.")).toBeInTheDocument();
    expect(
      screen.getByText(/Create your first template/),
    ).toBeInTheDocument();
  });

  it("renders table with template rows", () => {
    render(
      <TemplateList slug="acme" templates={[SAMPLE_TEMPLATE]} canManage={true} />,
    );
    expect(screen.getByText("Monthly Accounting Package")).toBeInTheDocument();
    expect(screen.getByText("Standard monthly accounting workflow")).toBeInTheDocument();
  });

  it("shows MANUAL source badge", () => {
    render(
      <TemplateList slug="acme" templates={[SAMPLE_TEMPLATE]} canManage={true} />,
    );
    expect(screen.getByText("Manual")).toBeInTheDocument();
  });

  it("shows FROM_PROJECT source badge", () => {
    render(
      <TemplateList slug="acme" templates={[FROM_PROJECT_TEMPLATE]} canManage={true} />,
    );
    expect(screen.getByText("From Project")).toBeInTheDocument();
  });

  it("shows active/inactive badge", () => {
    render(
      <TemplateList
        slug="acme"
        templates={[SAMPLE_TEMPLATE, FROM_PROJECT_TEMPLATE]}
        canManage={true}
      />,
    );
    expect(screen.getByText("Active")).toBeInTheDocument();
    expect(screen.getByText("Inactive")).toBeInTheDocument();
  });

  it("shows action buttons when canManage is true", () => {
    render(
      <TemplateList slug="acme" templates={[SAMPLE_TEMPLATE]} canManage={true} />,
    );
    expect(screen.getByTitle("Edit template")).toBeInTheDocument();
    expect(screen.getByTitle("Duplicate template")).toBeInTheDocument();
    expect(screen.getByTitle("Delete template")).toBeInTheDocument();
  });

  it("hides action buttons when canManage is false", () => {
    render(
      <TemplateList slug="acme" templates={[SAMPLE_TEMPLATE]} canManage={false} />,
    );
    expect(screen.queryByTitle("Edit template")).not.toBeInTheDocument();
    expect(screen.queryByTitle("Duplicate template")).not.toBeInTheDocument();
    expect(screen.queryByTitle("Delete template")).not.toBeInTheDocument();
  });

  it("calls duplicateTemplateAction when Duplicate is clicked", async () => {
    mockDuplicateTemplate.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    render(
      <TemplateList slug="acme" templates={[SAMPLE_TEMPLATE]} canManage={true} />,
    );
    await user.click(screen.getByTitle("Duplicate template"));
    expect(mockDuplicateTemplate).toHaveBeenCalledWith("acme", "pt-1");
  });
});
