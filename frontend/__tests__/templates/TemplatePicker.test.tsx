import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TemplatePicker } from "@/components/templates/TemplatePicker";
import type { ProjectTemplateResponse } from "@/lib/api/templates";

const TEMPLATE_1: ProjectTemplateResponse = {
  id: "pt-1",
  name: "Monthly Bookkeeping",
  namePattern: "{customer} — {month} {year}",
  description: "Standard monthly",
  billableDefault: true,
  source: "MANUAL",
  sourceProjectId: null,
  active: true,
  taskCount: 3,
  tagCount: 2,
  tasks: [],
  tags: [
    { id: "tag-1", name: "Bookkeeping", color: null },
    { id: "tag-2", name: "Monthly", color: "#3B82F6" },
  ],
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const TEMPLATE_2: ProjectTemplateResponse = {
  id: "pt-2",
  name: "Annual Audit",
  namePattern: "{customer} — Annual Audit {year}",
  description: null,
  billableDefault: true,
  source: "FROM_PROJECT",
  sourceProjectId: "proj-1",
  active: true,
  taskCount: 5,
  tagCount: 0,
  tasks: [],
  tags: [],
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

describe("TemplatePicker", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders template names in the list", () => {
    render(
      <TemplatePicker
        templates={[TEMPLATE_1, TEMPLATE_2]}
        selectedId={null}
        onSelect={vi.fn()}
      />,
    );
    expect(screen.getByText("Monthly Bookkeeping")).toBeInTheDocument();
    expect(screen.getByText("Annual Audit")).toBeInTheDocument();
  });

  it("shows empty state when no templates", () => {
    render(
      <TemplatePicker templates={[]} selectedId={null} onSelect={vi.fn()} />,
    );
    expect(
      screen.getByText("No active templates found."),
    ).toBeInTheDocument();
  });

  it("shows task count and tags for a template", () => {
    render(
      <TemplatePicker
        templates={[TEMPLATE_1]}
        selectedId={null}
        onSelect={vi.fn()}
      />,
    );
    expect(screen.getByText(/3 tasks/)).toBeInTheDocument();
    // Tag names appear in the subtitle text alongside task count
    expect(screen.getByText(/Bookkeeping, Monthly/)).toBeInTheDocument();
  });

  it("calls onSelect with template id when item clicked", async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(
      <TemplatePicker
        templates={[TEMPLATE_1, TEMPLATE_2]}
        selectedId={null}
        onSelect={onSelect}
      />,
    );
    await user.click(screen.getByText("Monthly Bookkeeping"));
    expect(onSelect).toHaveBeenCalledWith("pt-1");
  });
});
