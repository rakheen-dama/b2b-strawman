import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SaveAsTemplateDialog } from "@/components/templates/SaveAsTemplateDialog";
import type { Task, TagResponse } from "@/lib/types";

const mockSaveAsTemplate = vi.fn();

vi.mock(
  "@/app/(app)/org/[slug]/settings/project-templates/actions",
  () => ({
    saveAsTemplateAction: (...args: unknown[]) =>
      mockSaveAsTemplate(...args),
    deleteTemplateAction: vi.fn(),
    duplicateTemplateAction: vi.fn(),
    createProjectTemplateAction: vi.fn(),
    updateProjectTemplateAction: vi.fn(),
    instantiateTemplateAction: vi.fn(),
  }),
);

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

const SAMPLE_TASKS: Task[] = [
  {
    id: "task-1",
    title: "Collect documents",
    description: null,
    status: "OPEN",
    projectId: "proj-1",
    assigneeId: null,
    createdBy: "m-1",
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  },
  {
    id: "task-2",
    title: "Review finances",
    description: null,
    status: "OPEN",
    projectId: "proj-1",
    assigneeId: null,
    createdBy: "m-1",
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  },
];

const SAMPLE_TAGS: TagResponse[] = [
  { id: "tag-1", name: "Bookkeeping", slug: "bookkeeping", color: "#3B82F6" },
];

describe("SaveAsTemplateDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders trigger and opens dialog on click", async () => {
    const user = userEvent.setup();
    render(
      <SaveAsTemplateDialog
        slug="acme"
        projectId="proj-1"
        projectTasks={SAMPLE_TASKS}
        projectTags={SAMPLE_TAGS}
      >
        <button>Save as Template trigger</button>
      </SaveAsTemplateDialog>,
    );
    await user.click(screen.getByText("Save as Template trigger"));
    expect(screen.getByText("Template name")).toBeInTheDocument();
  });

  it("shows task checkboxes from project", async () => {
    const user = userEvent.setup();
    render(
      <SaveAsTemplateDialog
        slug="acme"
        projectId="proj-1"
        projectTasks={SAMPLE_TASKS}
        projectTags={SAMPLE_TAGS}
      >
        <button>Open SaveDialog</button>
      </SaveAsTemplateDialog>,
    );
    await user.click(screen.getByText("Open SaveDialog"));
    expect(screen.getByText("Collect documents")).toBeInTheDocument();
    expect(screen.getByText("Review finances")).toBeInTheDocument();
  });

  it("shows role selector when task is checked", async () => {
    const user = userEvent.setup();
    render(
      <SaveAsTemplateDialog
        slug="acme"
        projectId="proj-1"
        projectTasks={SAMPLE_TASKS}
        projectTags={[]}
      >
        <button>Open SaveDialog</button>
      </SaveAsTemplateDialog>,
    );
    await user.click(screen.getByText("Open SaveDialog"));
    const checkboxes = screen.getAllByRole("checkbox");
    await user.click(checkboxes[0]); // Check first task
    // Role selector should appear
    expect(screen.getByDisplayValue("Unassigned")).toBeInTheDocument();
  });

  it("calls saveAsTemplateAction with correct data on submit", async () => {
    mockSaveAsTemplate.mockResolvedValue({
      success: true,
      data: { id: "tpl-new", name: "Test Template" },
    });
    const user = userEvent.setup();
    render(
      <SaveAsTemplateDialog
        slug="acme"
        projectId="proj-1"
        projectTasks={SAMPLE_TASKS}
        projectTags={SAMPLE_TAGS}
      >
        <button>Open SaveDialog</button>
      </SaveAsTemplateDialog>,
    );
    await user.click(screen.getByText("Open SaveDialog"));
    await user.type(screen.getByLabelText("Template name"), "Test Template");
    // fireEvent.change used because userEvent.type interprets { and } as special keys
    fireEvent.change(screen.getByLabelText("Name pattern"), {
      target: { value: "{customer} — Test {year}" },
    });
    await user.click(
      screen.getByRole("button", { name: "Save as Template" }),
    );
    await waitFor(() => {
      expect(mockSaveAsTemplate).toHaveBeenCalledWith(
        "acme",
        "proj-1",
        expect.objectContaining({
          name: "Test Template",
          namePattern: "{customer} — Test {year}",
        }),
      );
    });
  });

  it("shows success state after save", async () => {
    mockSaveAsTemplate.mockResolvedValue({
      success: true,
      data: { id: "tpl-new", name: "Test Template" },
    });
    const user = userEvent.setup();
    render(
      <SaveAsTemplateDialog
        slug="acme"
        projectId="proj-1"
        projectTasks={SAMPLE_TASKS}
        projectTags={[]}
      >
        <button>Open SaveDialog</button>
      </SaveAsTemplateDialog>,
    );
    await user.click(screen.getByText("Open SaveDialog"));
    await user.type(screen.getByLabelText("Template name"), "Test Template");
    fireEvent.change(screen.getByLabelText("Name pattern"), {
      target: { value: "{customer} — {year}" },
    });
    await user.click(
      screen.getByRole("button", { name: "Save as Template" }),
    );
    await waitFor(() => {
      expect(screen.getByText(/saved successfully/)).toBeInTheDocument();
    });
  });

  it("shows error message on failure", async () => {
    mockSaveAsTemplate.mockResolvedValue({
      success: false,
      error: "Permission denied",
    });
    const user = userEvent.setup();
    render(
      <SaveAsTemplateDialog
        slug="acme"
        projectId="proj-1"
        projectTasks={SAMPLE_TASKS}
        projectTags={[]}
      >
        <button>Open SaveDialog</button>
      </SaveAsTemplateDialog>,
    );
    await user.click(screen.getByText("Open SaveDialog"));
    await user.type(screen.getByLabelText("Template name"), "Test Template");
    fireEvent.change(screen.getByLabelText("Name pattern"), {
      target: { value: "{customer}" },
    });
    await user.click(
      screen.getByRole("button", { name: "Save as Template" }),
    );
    await waitFor(() => {
      expect(screen.getByText("Permission denied")).toBeInTheDocument();
    });
  });
});
