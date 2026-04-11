import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock server-only
vi.mock("server-only", () => ({}));

// Mock server actions
const mockGetUnbilledTime = vi.fn();
const mockGetUnbilledExpenses = vi.fn();
const mockUpdateSelections = vi.fn();
const mockExcludeCustomer = vi.fn();
const mockIncludeCustomer = vi.fn();
const mockGetRetainerPreview = vi.fn();

vi.mock("@/app/(app)/org/[slug]/invoices/billing-runs/new/billing-run-actions", () => ({
  createBillingRunAction: vi.fn(),
  loadPreviewAction: vi.fn(),
  getUnbilledSummaryAction: vi.fn().mockResolvedValue({ success: true }),
  getUnbilledTimeAction: (...args: unknown[]) => mockGetUnbilledTime(...args),
  getUnbilledExpensesAction: (...args: unknown[]) => mockGetUnbilledExpenses(...args),
  updateSelectionsAction: (...args: unknown[]) => mockUpdateSelections(...args),
  excludeCustomerAction: (...args: unknown[]) => mockExcludeCustomer(...args),
  includeCustomerAction: (...args: unknown[]) => mockIncludeCustomer(...args),
  getRetainerPreviewAction: (...args: unknown[]) => mockGetRetainerPreview(...args),
}));

import { CherryPickStep } from "@/components/billing-runs/cherry-pick-step";
import type { BillingRunItem, UnbilledTimeEntry, UnbilledExpense } from "@/lib/api/billing-runs";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const mockItems: BillingRunItem[] = [
  {
    id: "item-1",
    customerId: "cust-1",
    customerName: "Acme Corp",
    status: "PENDING",
    unbilledTimeAmount: 50000,
    unbilledExpenseAmount: 5000,
    unbilledTimeCount: 2,
    unbilledExpenseCount: 1,
    totalUnbilledAmount: 55000,
    hasPrerequisiteIssues: false,
    prerequisiteIssueReason: null,
    invoiceId: null,
    failureReason: null,
  },
  {
    id: "item-2",
    customerId: "cust-2",
    customerName: "Beta LLC",
    status: "PENDING",
    unbilledTimeAmount: 30000,
    unbilledExpenseAmount: 0,
    unbilledTimeCount: 1,
    unbilledExpenseCount: 0,
    totalUnbilledAmount: 30000,
    hasPrerequisiteIssues: false,
    prerequisiteIssueReason: null,
    invoiceId: null,
    failureReason: null,
  },
];

const mockTimeEntries: UnbilledTimeEntry[] = [
  {
    id: "te-1",
    taskId: "task-1",
    memberId: "member-1",
    date: "2026-03-01",
    durationMinutes: 120,
    description: "Development work",
    billable: true,
    billingRateSnapshot: 250,
    billingRateCurrency: "ZAR",
    billableValue: 500,
  },
  {
    id: "te-2",
    taskId: "task-2",
    memberId: "member-2",
    date: "2026-03-02",
    durationMinutes: 60,
    description: "Code review",
    billable: true,
    billingRateSnapshot: 300,
    billingRateCurrency: "ZAR",
    billableValue: 300,
  },
];

const mockExpenses: UnbilledExpense[] = [
  {
    id: "exp-1",
    projectId: "proj-1",
    memberId: "member-1",
    date: "2026-03-01",
    description: "Software license",
    amount: 500,
    currency: "ZAR",
    category: "SOFTWARE",
    billable: true,
    markupPercent: null,
    billableAmount: 500,
  },
];

describe("CherryPickStep", () => {
  it("renders accordion with customer sections", () => {
    render(
      <CherryPickStep
        slug="test-org"
        billingRunId="run-1"
        currency="ZAR"
        includeRetainers={false}
        items={mockItems}
        onBack={vi.fn()}
        onNext={vi.fn()}
      />
    );

    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    expect(screen.getByText("Beta LLC")).toBeInTheDocument();
    expect(screen.getByText("Review & Cherry-Pick")).toBeInTheDocument();
  });

  it("renders time entries table with checkboxes when section expanded", async () => {
    const user = userEvent.setup();

    mockGetUnbilledTime.mockResolvedValue({
      success: true,
      entries: mockTimeEntries,
    });
    mockGetUnbilledExpenses.mockResolvedValue({
      success: true,
      entries: mockExpenses,
    });

    render(
      <CherryPickStep
        slug="test-org"
        billingRunId="run-1"
        currency="ZAR"
        includeRetainers={false}
        items={mockItems}
        onBack={vi.fn()}
        onNext={vi.fn()}
      />
    );

    // Expand Acme Corp section
    await user.click(screen.getByText("Acme Corp"));

    // Wait for data to load
    await waitFor(() => {
      expect(screen.getByText("Development work")).toBeInTheDocument();
    });
    expect(screen.getByText("Code review")).toBeInTheDocument();
    expect(screen.getByText("Time Entries")).toBeInTheDocument();
    expect(screen.getByText("Expenses")).toBeInTheDocument();
    expect(screen.getByText("Software license")).toBeInTheDocument();

    // Check checkboxes are rendered
    expect(screen.getByLabelText("Include time entry Development work")).toBeInTheDocument();
    expect(screen.getByLabelText("Include expense Software license")).toBeInTheDocument();
  });

  it("entry toggle updates subtotal display", async () => {
    const user = userEvent.setup();

    mockGetUnbilledTime.mockResolvedValue({
      success: true,
      entries: mockTimeEntries,
    });
    mockGetUnbilledExpenses.mockResolvedValue({
      success: true,
      entries: [],
    });
    mockUpdateSelections.mockResolvedValue({
      success: true,
      item: mockItems[0],
    });

    render(
      <CherryPickStep
        slug="test-org"
        billingRunId="run-1"
        currency="ZAR"
        includeRetainers={false}
        items={mockItems}
        onBack={vi.fn()}
        onNext={vi.fn()}
      />
    );

    // Expand Acme Corp
    await user.click(screen.getByText("Acme Corp"));

    await waitFor(() => {
      expect(screen.getByText("Development work")).toBeInTheDocument();
    });

    // Initially subtotal should include both time entries (500 + 300 = 800)
    // locale-agnostic: accepts en-ZA "R 800,00" or en-US "R 800.00"
    expect(screen.getByText(/Subtotal:\s*R[\s\u00a0]800[,.]00/)).toBeInTheDocument();

    // Uncheck first time entry
    await user.click(screen.getByLabelText("Include time entry Development work"));

    // Subtotal should now be 300 only
    // locale-agnostic: accepts en-ZA "R 300,00" or en-US "R 300.00"
    expect(screen.getByText(/Subtotal:\s*R[\s\u00a0]300[,.]00/)).toBeInTheDocument();
  });

  it("exclude customer button updates status", async () => {
    const user = userEvent.setup();

    mockGetUnbilledTime.mockResolvedValue({
      success: true,
      entries: mockTimeEntries,
    });
    mockGetUnbilledExpenses.mockResolvedValue({
      success: true,
      entries: [],
    });
    mockExcludeCustomer.mockResolvedValue({
      success: true,
      item: { ...mockItems[0], status: "EXCLUDED" },
    });

    render(
      <CherryPickStep
        slug="test-org"
        billingRunId="run-1"
        currency="ZAR"
        includeRetainers={false}
        items={mockItems}
        onBack={vi.fn()}
        onNext={vi.fn()}
      />
    );

    // Expand Acme Corp
    await user.click(screen.getByText("Acme Corp"));

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Exclude Customer" })).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "Exclude Customer" }));

    await waitFor(() => {
      expect(screen.getByText("Excluded")).toBeInTheDocument();
    });
  });

  it("retainer section shows when includeRetainers is true", async () => {
    mockGetRetainerPreview.mockResolvedValue({
      success: true,
      retainers: [
        {
          agreementId: "ret-1",
          customerId: "cust-1",
          customerName: "Acme Corp",
          periodStart: "2026-03-01",
          periodEnd: "2026-03-31",
          consumedHours: 40.5,
          estimatedAmount: 10000,
        },
      ],
    });

    render(
      <CherryPickStep
        slug="test-org"
        billingRunId="run-1"
        currency="ZAR"
        includeRetainers={true}
        items={mockItems}
        onBack={vi.fn()}
        onNext={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByText("Retainer Agreements")).toBeInTheDocument();
    });

    expect(screen.getByText("40.5")).toBeInTheDocument();
    expect(screen.getByLabelText("Include retainer for Acme Corp")).toBeInTheDocument();
  });
});
