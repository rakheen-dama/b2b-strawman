import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ExpenseList } from "@/components/expenses/expense-list";
import { makeExpense } from "./fixtures";
import type { ExpenseResponse } from "@/lib/types";

// Mock server actions
const mockDeleteExpense = vi.fn();
const mockWriteOffExpense = vi.fn();
const mockRestoreExpense = vi.fn();
const mockCreateExpense = vi.fn();
const mockUpdateExpense = vi.fn();

vi.mock("@/app/(app)/org/[slug]/projects/[id]/expense-actions", () => ({
  deleteExpense: (...args: unknown[]) => mockDeleteExpense(...args),
  writeOffExpense: (...args: unknown[]) => mockWriteOffExpense(...args),
  restoreExpense: (...args: unknown[]) => mockRestoreExpense(...args),
  createExpense: (...args: unknown[]) => mockCreateExpense(...args),
  updateExpense: (...args: unknown[]) => mockUpdateExpense(...args),
}));

// Mock upload actions used by LogExpenseDialog (rendered inside ExpenseList for edit)
vi.mock("@/app/(app)/org/[slug]/projects/[id]/actions", () => ({
  initiateUpload: vi.fn(),
  confirmUpload: vi.fn(),
  cancelUpload: vi.fn(),
}));

function makeExpenses(): ExpenseResponse[] {
  return [
    makeExpense({
      id: "exp1",
      description: "Court filing fee",
      amount: 250.0,
      category: "FILING_FEE",
      billingStatus: "UNBILLED",
      memberId: "m1",
      memberName: "Alice Johnson",
    }),
    makeExpense({
      id: "exp2",
      description: "Travel to client",
      amount: 1500.0,
      category: "TRAVEL",
      billingStatus: "BILLED",
      memberId: "m2",
      memberName: "Bob Smith",
      invoiceId: "inv1",
    }),
    makeExpense({
      id: "exp3",
      description: "Office supplies",
      amount: 80.0,
      category: "OTHER",
      billingStatus: "NON_BILLABLE",
      billable: false,
      memberId: "m1",
      memberName: "Alice Johnson",
    }),
  ];
}

const defaultProps = {
  slug: "acme",
  projectId: "p1",
  tasks: [{ id: "t1", title: "Task One" }],
  members: [
    { id: "m1", name: "Alice Johnson" },
    { id: "m2", name: "Bob Smith" },
  ],
  currentMemberId: "m1",
  orgRole: "org:owner",
};

describe("ExpenseList", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders empty state when no expenses", () => {
    render(<ExpenseList {...defaultProps} expenses={[]} />);

    expect(screen.getByText("No expenses logged")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Log disbursements like filing fees, travel, and courier costs against this project."
      )
    ).toBeInTheDocument();
  });

  it("renders expense rows with correct data", () => {
    const expenses = makeExpenses();
    render(<ExpenseList {...defaultProps} expenses={expenses} />);

    // Check descriptions render
    expect(screen.getByText("Court filing fee")).toBeInTheDocument();
    expect(screen.getByText("Travel to client")).toBeInTheDocument();
    expect(screen.getByText("Office supplies")).toBeInTheDocument();

    // Check category badges render (use getAllByText since filter buttons also show category names)
    expect(screen.getAllByText("Filing Fee").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Travel").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Other").length).toBeGreaterThanOrEqual(1);

    // Check expense count badge
    expect(screen.getByText("3 expenses")).toBeInTheDocument();
  });

  it("filters expenses by billing status", async () => {
    const expenses = makeExpenses();
    const user = userEvent.setup();

    render(<ExpenseList {...defaultProps} expenses={expenses} />);

    // Initially all 3 expenses shown (1 header + 3 data rows)
    expect(screen.getAllByRole("row")).toHaveLength(4);

    // Click "Unbilled" filter
    const filterGroup = screen.getByRole("group", {
      name: "Billing status filter",
    });
    const unbilledBtn = within(filterGroup).getByText("Unbilled");
    await user.click(unbilledBtn);

    // Only 1 unbilled expense (exp1)
    expect(screen.getAllByRole("row")).toHaveLength(2); // 1 header + 1 data

    // Click "Billed" filter
    const billedBtn = within(filterGroup).getByText("Billed");
    await user.click(billedBtn);

    // Only 1 billed expense (exp2)
    expect(screen.getAllByRole("row")).toHaveLength(2); // 1 header + 1 data
    expect(screen.getByText("Travel to client")).toBeInTheDocument();
  });

  it("shows action buttons for editable expenses owned by current member", () => {
    const expenses = [
      makeExpense({
        id: "exp1",
        billingStatus: "UNBILLED",
        memberId: "m1",
      }),
    ];

    render(
      <ExpenseList
        {...defaultProps}
        expenses={expenses}
        orgRole="org:member"
        currentMemberId="m1"
      />
    );

    // Should have edit and delete buttons (not disabled)
    const table = screen.getByRole("table");
    const tbody = table.querySelector("tbody")!;

    // The edit button is inside the LogExpenseDialog trigger
    const editButtons = within(tbody).getAllByRole("button");
    // At minimum there should be edit (Pencil) and delete (Trash2) buttons
    expect(editButtons.length).toBeGreaterThanOrEqual(2);
  });

  it("shows disabled buttons with tooltips for BILLED expenses alongside editable ones", () => {
    // Need at least one editable expense so the actions column renders
    const expenses = [
      makeExpense({
        id: "exp-unbilled",
        billingStatus: "UNBILLED",
        memberId: "m1",
      }),
      makeExpense({
        id: "exp-billed",
        description: "Billed travel",
        billingStatus: "BILLED",
        invoiceId: "inv1",
        memberId: "m1",
      }),
    ];

    render(<ExpenseList {...defaultProps} expenses={expenses} />);

    // BILLED expense row should have disabled edit and delete buttons
    const disabledButtons = screen
      .getAllByRole("button")
      .filter((btn) => btn.hasAttribute("disabled"));
    expect(disabledButtons.length).toBeGreaterThanOrEqual(2);
  });
});
