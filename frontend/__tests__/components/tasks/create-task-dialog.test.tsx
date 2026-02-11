import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CreateTaskDialog } from "@/components/tasks/create-task-dialog";

const mockCreateTask = vi.fn();

vi.mock("@/app/(app)/org/[slug]/projects/[id]/task-actions", () => ({
  createTask: (...args: unknown[]) => mockCreateTask(...args),
}));

describe("CreateTaskDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("opens dialog and shows form fields", async () => {
    const user = userEvent.setup();

    render(
      <CreateTaskDialog slug="acme" projectId="p1">
        <button>Open Create Task Dialog</button>
      </CreateTaskDialog>,
    );

    await user.click(screen.getByText("Open Create Task Dialog"));

    expect(screen.getByLabelText("Title")).toBeInTheDocument();
    expect(screen.getByText("Description")).toBeInTheDocument();
    expect(screen.getByLabelText("Priority")).toBeInTheDocument();
  });

  it("calls createTask on successful submit", async () => {
    mockCreateTask.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    render(
      <CreateTaskDialog slug="acme" projectId="p1">
        <button>Open Create Task Dialog</button>
      </CreateTaskDialog>,
    );

    await user.click(screen.getByText("Open Create Task Dialog"));
    await user.type(screen.getByLabelText("Title"), "My new task");
    // Use getByRole to target the submit button specifically
    await user.click(screen.getByRole("button", { name: "Create Task" }));

    await waitFor(() => {
      expect(mockCreateTask).toHaveBeenCalledWith("acme", "p1", expect.any(FormData));
    });
  });

  it("displays error when createTask fails", async () => {
    mockCreateTask.mockResolvedValue({ success: false, error: "Server error" });
    const user = userEvent.setup();

    render(
      <CreateTaskDialog slug="acme" projectId="p1">
        <button>Open Create Task Dialog</button>
      </CreateTaskDialog>,
    );

    await user.click(screen.getByText("Open Create Task Dialog"));
    await user.type(screen.getByLabelText("Title"), "My task");
    await user.click(screen.getByRole("button", { name: "Create Task" }));

    await waitFor(() => {
      expect(screen.getByText("Server error")).toBeInTheDocument();
    });
  });
});
