import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TemplateEditor } from "@/components/templates/TemplateEditor";
import type { ProjectTemplateResponse } from "@/lib/api/templates";
import type { TagResponse } from "@/lib/types";

const mockCreateTemplate = vi.fn();
const mockUpdateTemplate = vi.fn();
const mockRouterPush = vi.fn();

vi.mock(
  "@/app/(app)/org/[slug]/settings/project-templates/actions",
  () => ({
    deleteTemplateAction: vi.fn(),
    duplicateTemplateAction: vi.fn(),
    createProjectTemplateAction: (...args: unknown[]) => mockCreateTemplate(...args),
    updateProjectTemplateAction: (...args: unknown[]) => mockUpdateTemplate(...args),
  }),
);

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: mockRouterPush,
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
  taskCount: 1,
  tagCount: 0,
  tasks: [
    {
      id: "task-1",
      name: "Prepare financials",
      description: "Prepare monthly financials",
      estimatedHours: 2,
      sortOrder: 0,
      billable: true,
      assigneeRole: "PROJECT_LEAD",
      items: [
        { id: "item-1", title: "Gather bank statements", sortOrder: 0 },
        { id: "item-2", title: "Reconcile accounts", sortOrder: 1 },
      ],
    },
  ],
  tags: [],
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const AVAILABLE_TAGS: TagResponse[] = [
  { id: "tag-1", name: "Accounting", slug: "accounting", color: "#22c55e" },
  { id: "tag-2", name: "Monthly", slug: "monthly", color: null },
];

describe("TemplateEditor", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders empty form for new template", () => {
    render(
      <TemplateEditor slug="acme" availableTags={AVAILABLE_TAGS} />,
    );
    expect(screen.getByRole("button", { name: /create template/i })).toBeInTheDocument();
    const nameInput = screen.getByPlaceholderText("e.g. Monthly Accounting Package");
    expect(nameInput).toHaveValue("");
    expect(screen.getByText("No tasks yet. Add a task to define the project structure.")).toBeInTheDocument();
  });

  it("pre-fills form fields when editing existing template", () => {
    render(
      <TemplateEditor slug="acme" template={SAMPLE_TEMPLATE} availableTags={AVAILABLE_TAGS} />,
    );
    expect(screen.getByRole("button", { name: /save changes/i })).toBeInTheDocument();
    const nameInput = screen.getByPlaceholderText("e.g. Monthly Accounting Package");
    expect(nameInput).toHaveValue("Monthly Accounting Package");
    // Task name is in an input field; check its display value
    expect(screen.getByDisplayValue("Prepare financials")).toBeInTheDocument();
  });

  it("can add a new task row", async () => {
    const user = userEvent.setup();
    render(
      <TemplateEditor slug="acme" availableTags={[]} />,
    );
    expect(screen.getByText("No tasks yet. Add a task to define the project structure.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /add task/i }));
    expect(screen.queryByText("No tasks yet. Add a task to define the project structure.")).not.toBeInTheDocument();
    expect(screen.getByPlaceholderText("Task name")).toBeInTheDocument();
  });

  it("can remove a task row", async () => {
    const user = userEvent.setup();
    render(
      <TemplateEditor slug="acme" template={SAMPLE_TEMPLATE} availableTags={[]} />,
    );
    expect(screen.getByDisplayValue("Prepare financials")).toBeInTheDocument();
    await user.click(screen.getByTitle("Remove task"));
    expect(screen.queryByDisplayValue("Prepare financials")).not.toBeInTheDocument();
    expect(screen.getByText("No tasks yet. Add a task to define the project structure.")).toBeInTheDocument();
  });

  it("calls createProjectTemplateAction on save for new template", async () => {
    mockCreateTemplate.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    render(
      <TemplateEditor slug="acme" availableTags={[]} />,
    );
    const nameInput = screen.getByPlaceholderText("e.g. Monthly Accounting Package");
    const patternInput = screen.getByPlaceholderText("e.g. {customer} — {month} {year}");
    await user.type(nameInput, "Test Template");
    // Use fireEvent.change to avoid userEvent treating {} as keyboard shortcut
    fireEvent.change(patternInput, { target: { value: "customer-year" } });
    await user.click(screen.getByRole("button", { name: /create template/i }));
    expect(mockCreateTemplate).toHaveBeenCalledWith(
      "acme",
      expect.objectContaining({ name: "Test Template", namePattern: "customer-year" }),
    );
  });

  it("calls updateProjectTemplateAction on save for existing template", async () => {
    mockUpdateTemplate.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    render(
      <TemplateEditor slug="acme" template={SAMPLE_TEMPLATE} availableTags={[]} />,
    );
    await user.click(screen.getByRole("button", { name: /save changes/i }));
    expect(mockUpdateTemplate).toHaveBeenCalledWith(
      "acme",
      "pt-1",
      expect.objectContaining({ name: "Monthly Accounting Package" }),
    );
  });

  it("displays sub-tasks when template is loaded with task items", () => {
    render(
      <TemplateEditor slug="acme" template={SAMPLE_TEMPLATE} availableTags={[]} />,
    );
    // Items are expanded by default when they exist
    expect(screen.getByText("Sub-tasks (2)")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Gather bank statements")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Reconcile accounts")).toBeInTheDocument();
  });

  it("can add a new sub-task item", async () => {
    const user = userEvent.setup();
    render(
      <TemplateEditor slug="acme" template={SAMPLE_TEMPLATE} availableTags={[]} />,
    );
    expect(screen.getByText("Sub-tasks (2)")).toBeInTheDocument();
    await user.click(screen.getByText("Add sub-task"));
    expect(screen.getByText("Sub-tasks (3)")).toBeInTheDocument();
    const inputs = screen.getAllByPlaceholderText("Sub-task title");
    expect(inputs).toHaveLength(3);
  });

  it("can delete a sub-task item", async () => {
    const user = userEvent.setup();
    render(
      <TemplateEditor slug="acme" template={SAMPLE_TEMPLATE} availableTags={[]} />,
    );
    expect(screen.getByDisplayValue("Gather bank statements")).toBeInTheDocument();
    const removeButtons = screen.getAllByTitle("Remove sub-task");
    await user.click(removeButtons[0]);
    expect(screen.queryByDisplayValue("Gather bank statements")).not.toBeInTheDocument();
    expect(screen.getByText("Sub-tasks (1)")).toBeInTheDocument();
  });

  it("includes items in the save payload", async () => {
    mockUpdateTemplate.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    render(
      <TemplateEditor slug="acme" template={SAMPLE_TEMPLATE} availableTags={[]} />,
    );
    await user.click(screen.getByRole("button", { name: /save changes/i }));
    expect(mockUpdateTemplate).toHaveBeenCalledWith(
      "acme",
      "pt-1",
      expect.objectContaining({
        tasks: expect.arrayContaining([
          expect.objectContaining({
            name: "Prepare financials",
            items: [
              { title: "Gather bank statements", sortOrder: 0 },
              { title: "Reconcile accounts", sortOrder: 1 },
            ],
          }),
        ]),
      }),
    );
  });
});
