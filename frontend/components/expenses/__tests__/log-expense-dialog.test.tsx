import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { LogExpenseDialog } from "@/components/expenses/log-expense-dialog";
import { CATEGORY_LABELS } from "@/components/expenses/expense-category-badge";
import { makeExpense } from "./fixtures";

// Mock server actions
const mockCreateExpense = vi.fn();
const mockUpdateExpense = vi.fn();

vi.mock("@/app/(app)/org/[slug]/projects/[id]/expense-actions", () => ({
  createExpense: (...args: unknown[]) => mockCreateExpense(...args),
  updateExpense: (...args: unknown[]) => mockUpdateExpense(...args),
}));

vi.mock("@/app/(app)/org/[slug]/projects/[id]/actions", () => ({
  initiateUpload: vi.fn(),
  confirmUpload: vi.fn(),
  cancelUpload: vi.fn(),
}));

const defaultProps = {
  slug: "acme",
  projectId: "p1",
  tasks: [
    { id: "t1", title: "Task One" },
    { id: "t2", title: "Task Two" },
  ],
};

describe("LogExpenseDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("opens dialog and shows 'Log Expense' title in create mode", async () => {
    const user = userEvent.setup();

    render(
      <LogExpenseDialog {...defaultProps}>
        <button>Open Expense Dialog</button>
      </LogExpenseDialog>
    );

    await user.click(screen.getByText("Open Expense Dialog"));

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "Log Expense" })
      ).toBeInTheDocument();
    });

    expect(
      screen.getByText("Record a disbursement against this project.")
    ).toBeInTheDocument();
  });

  it("shows 'Edit Expense' title when expenseToEdit is provided", async () => {
    const user = userEvent.setup();
    const expense = makeExpense();

    render(
      <LogExpenseDialog {...defaultProps} expenseToEdit={expense}>
        <button>Open Edit Dialog</button>
      </LogExpenseDialog>
    );

    await user.click(screen.getByText("Open Edit Dialog"));

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "Edit Expense" })
      ).toBeInTheDocument();
    });

    expect(
      screen.getByText("Update the details of this expense.")
    ).toBeInTheDocument();
  });

  it("billable checkbox defaults to checked in create mode", async () => {
    const user = userEvent.setup();

    render(
      <LogExpenseDialog {...defaultProps}>
        <button>Open Create Dialog</button>
      </LogExpenseDialog>
    );

    await user.click(screen.getByText("Open Create Dialog"));

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "Log Expense" })
      ).toBeInTheDocument();
    });

    const checkbox = screen.getByRole("checkbox", { name: /billable/i });
    expect(checkbox).toBeChecked();
  });

  it("shows category dropdown with all 8 categories", async () => {
    const user = userEvent.setup();

    render(
      <LogExpenseDialog {...defaultProps}>
        <button>Open Category Dialog</button>
      </LogExpenseDialog>
    );

    await user.click(screen.getByText("Open Category Dialog"));

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "Log Expense" })
      ).toBeInTheDocument();
    });

    const categorySelect = screen.getByLabelText("Category");
    const options = categorySelect.querySelectorAll("option");
    const expectedLabels = Object.values(CATEGORY_LABELS);
    expect(options).toHaveLength(expectedLabels.length);

    const categoryLabels = Array.from(options).map((opt) => opt.textContent);
    expect(categoryLabels).toEqual(expectedLabels);
  });

  it("submit button text differs between create and edit modes", async () => {
    const user = userEvent.setup();

    // Create mode
    const { unmount } = render(
      <LogExpenseDialog {...defaultProps}>
        <button>Open Submit Create</button>
      </LogExpenseDialog>
    );

    await user.click(screen.getByText("Open Submit Create"));

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "Log Expense" })
      ).toBeInTheDocument();
    });

    expect(
      screen.getByRole("button", { name: "Log Expense" })
    ).toBeInTheDocument();

    unmount();
    cleanup();

    // Edit mode
    const expense = makeExpense();
    render(
      <LogExpenseDialog {...defaultProps} expenseToEdit={expense}>
        <button>Open Submit Edit</button>
      </LogExpenseDialog>
    );

    await user.click(screen.getByText("Open Submit Edit"));

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "Edit Expense" })
      ).toBeInTheDocument();
    });

    expect(
      screen.getByRole("button", { name: "Update Expense" })
    ).toBeInTheDocument();
  });
});
