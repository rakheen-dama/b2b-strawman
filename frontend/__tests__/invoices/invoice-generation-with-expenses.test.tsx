import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  cleanup,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { InvoiceGenerationDialog } from "@/components/invoices/invoice-generation-dialog";
import { InvoiceLineTable } from "@/components/invoices/invoice-line-table";
import type {
  UnbilledTimeResponse,
  InvoiceLineResponse,
  ExpenseCategory,
} from "@/lib/types";

const mockFetchUnbilledTime = vi.fn();
const mockCreateInvoiceDraft = vi.fn();
const mockValidateInvoiceGeneration = vi.fn();

vi.mock("@/app/(app)/org/[slug]/customers/[id]/invoice-actions", () => ({
  fetchUnbilledTime: (...args: unknown[]) => mockFetchUnbilledTime(...args),
  createInvoiceDraft: (...args: unknown[]) => mockCreateInvoiceDraft(...args),
  validateInvoiceGeneration: (...args: unknown[]) =>
    mockValidateInvoiceGeneration(...args),
}));

const dataWithExpenses: UnbilledTimeResponse = {
  customerId: "c1",
  customerName: "Acme Corp",
  projects: [
    {
      projectId: "p1",
      projectName: "Project Alpha",
      entries: [
        {
          id: "te1",
          taskTitle: "Development",
          memberName: "Alice",
          date: "2026-01-15",
          durationMinutes: 120,
          billingRateSnapshot: 100,
          billingRateCurrency: "USD",
          billableValue: 200,
          description: null,
        },
      ],
      totals: { USD: { hours: 2, amount: 200 } },
    },
  ],
  grandTotals: { USD: { hours: 2, amount: 200 } },
  unbilledExpenses: [
    {
      id: "exp1",
      projectId: "p1",
      projectName: "Project Alpha",
      date: "2026-01-20",
      description: "Software license",
      amount: 50,
      currency: "USD",
      category: "SOFTWARE" as ExpenseCategory,
      markupPercent: 10,
      billableAmount: 55,
      notes: null,
    },
    {
      id: "exp2",
      projectId: "p1",
      projectName: "Project Alpha",
      date: "2026-01-21",
      description: "Travel expense",
      amount: 100,
      currency: "ZAR",
      category: "TRAVEL" as ExpenseCategory,
      markupPercent: null,
      billableAmount: 100,
      notes: null,
    },
  ],
  unbilledExpenseTotals: { USD: 55, ZAR: 100 },
};

describe("Invoice Generation — expense selection", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockValidateInvoiceGeneration.mockResolvedValue({
      success: true,
      checks: [
        {
          name: "customer_required_fields",
          severity: "WARNING",
          passed: true,
          message: "All customer required fields are filled",
        },
      ],
    });
  });

  afterEach(() => {
    cleanup();
  });

  it("shows expense selection section after fetching unbilled data", async () => {
    mockFetchUnbilledTime.mockResolvedValue({
      success: true,
      data: dataWithExpenses,
    });
    const user = userEvent.setup();

    render(
      <InvoiceGenerationDialog
        customerId="c1"
        customerName="Acme Corp"
        slug="acme"
        defaultCurrency="USD"
      />,
    );

    await user.click(screen.getByText("New Invoice"));
    await user.click(screen.getByText("Fetch Unbilled Time"));

    await waitFor(() => {
      expect(
        screen.getByTestId("expense-selection-section"),
      ).toBeInTheDocument();
    });

    // USD expense is visible
    expect(screen.getByText("Software license")).toBeInTheDocument();
    // ZAR expense is visible but disabled (currency mismatch)
    expect(screen.getByText("Travel expense")).toBeInTheDocument();
  });

  it("auto-selects expenses matching selected currency", async () => {
    mockFetchUnbilledTime.mockResolvedValue({
      success: true,
      data: dataWithExpenses,
    });
    const user = userEvent.setup();

    render(
      <InvoiceGenerationDialog
        customerId="c1"
        customerName="Acme Corp"
        slug="acme"
        defaultCurrency="USD"
      />,
    );

    await user.click(screen.getByText("New Invoice"));
    await user.click(screen.getByText("Fetch Unbilled Time"));

    await waitFor(() => {
      expect(
        screen.getByTestId("expense-selection-section"),
      ).toBeInTheDocument();
    });

    // Running total should include time (200) + expense (55) = 255
    // 2 items: 1 time entry + 1 USD expense auto-selected
    expect(screen.getByText(/2 items selected/)).toBeInTheDocument();
  });

  it("sends expenseIds when creating draft with expenses selected", async () => {
    mockFetchUnbilledTime.mockResolvedValue({
      success: true,
      data: dataWithExpenses,
    });
    mockCreateInvoiceDraft.mockResolvedValue({ success: true, invoice: {} });
    const user = userEvent.setup();

    render(
      <InvoiceGenerationDialog
        customerId="c1"
        customerName="Acme Corp"
        slug="acme"
        defaultCurrency="USD"
      />,
    );

    await user.click(screen.getByText("New Invoice"));
    await user.click(screen.getByText("Fetch Unbilled Time"));

    await waitFor(() => {
      expect(
        screen.getByTestId("expense-selection-section"),
      ).toBeInTheDocument();
    });

    // Click validate then create
    await user.click(screen.getByText("Validate & Create Draft"));

    await waitFor(() => {
      expect(screen.getByText("Create Draft")).toBeInTheDocument();
    });

    await user.click(screen.getByText("Create Draft"));

    await waitFor(() => {
      expect(mockCreateInvoiceDraft).toHaveBeenCalledWith("acme", "c1", {
        customerId: "c1",
        currency: "USD",
        timeEntryIds: ["te1"],
        expenseIds: ["exp1"],
      });
    });
  });
});

describe("Invoice Line Table — lineType grouping", () => {
  afterEach(() => {
    cleanup();
  });

  const mixedLines: InvoiceLineResponse[] = [
    {
      id: "line-1",
      projectId: "p1",
      projectName: "Project Alpha",
      timeEntryId: "te1",
      expenseId: null,
      lineType: "TIME",
      description: "Development work",
      quantity: 2,
      unitPrice: 100,
      amount: 200,
      sortOrder: 0,
      taxRateId: null,
      taxRateName: null,
      taxRatePercent: null,
      taxAmount: null,
      taxExempt: false,
    },
    {
      id: "line-2",
      projectId: "p1",
      projectName: "Project Alpha",
      timeEntryId: null,
      expenseId: "exp1",
      lineType: "EXPENSE",
      description: "Court filing fee (20% markup)",
      quantity: 1,
      unitPrice: 300,
      amount: 300,
      sortOrder: 1,
      taxRateId: null,
      taxRateName: null,
      taxRatePercent: null,
      taxAmount: null,
      taxExempt: false,
    },
    {
      id: "line-3",
      projectId: null,
      projectName: null,
      timeEntryId: null,
      expenseId: null,
      lineType: "MANUAL",
      description: "Flat fee consulting",
      quantity: 1,
      unitPrice: 500,
      amount: 500,
      sortOrder: 2,
      taxRateId: null,
      taxRateName: null,
      taxRatePercent: null,
      taxAmount: null,
      taxExempt: false,
    },
  ];

  it("groups lines by lineType with section headers", () => {
    render(
      <InvoiceLineTable
        lines={mixedLines}
        currency="USD"
        editable={false}
      />,
    );

    expect(
      screen.getByTestId("section-header-Time Entries"),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId("section-header-Expenses"),
    ).toBeInTheDocument();
    expect(screen.getByTestId("section-header-Other")).toBeInTheDocument();

    expect(screen.getByText("Development work")).toBeInTheDocument();
    expect(
      screen.getByText("Court filing fee (20% markup)"),
    ).toBeInTheDocument();
    expect(screen.getByText("Flat fee consulting")).toBeInTheDocument();
  });

  it("does not show section headers when all lines are same type", () => {
    const timeOnlyLines: InvoiceLineResponse[] = [mixedLines[0]];

    render(
      <InvoiceLineTable
        lines={timeOnlyLines}
        currency="USD"
        editable={false}
      />,
    );

    expect(screen.getByText("Development work")).toBeInTheDocument();
    expect(
      screen.queryByTestId("section-header-Time Entries"),
    ).not.toBeInTheDocument();
  });

  it("renders expense lines with description from backend", () => {
    render(
      <InvoiceLineTable
        lines={mixedLines}
        currency="USD"
        editable={false}
      />,
    );

    expect(
      screen.getByText("Court filing fee (20% markup)"),
    ).toBeInTheDocument();
  });
});
