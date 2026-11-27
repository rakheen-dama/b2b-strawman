import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { formatCurrency } from "@/lib/format";

// Mock server-only
vi.mock("server-only", () => ({}));

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

// Mock server actions
const mockCancelBillingRun = vi.fn().mockResolvedValue({ success: true });
const mockBatchApprove = vi.fn().mockResolvedValue({ success: true });

vi.mock("@/app/(app)/org/[slug]/invoices/billing-runs/[id]/actions", () => ({
  cancelBillingRunAction: (...args: unknown[]) => mockCancelBillingRun(...args),
  batchApproveAction: (...args: unknown[]) => mockBatchApprove(...args),
  batchSendAction: vi.fn(),
}));

import { BillingRunSummaryCards } from "@/components/billing-runs/billing-run-summary-cards";
import { BillingRunItemsTable } from "@/components/billing-runs/billing-run-items-table";
import { BillingRunDetailActions } from "@/components/billing-runs/billing-run-detail-actions";
import type { BillingRun, BillingRunItem } from "@/lib/api/billing-runs";

afterEach(() => {
  cleanup();
});

const mockBillingRun: BillingRun = {
  id: "run-1",
  name: "March 2026 Billing",
  status: "COMPLETED",
  periodFrom: "2026-03-01",
  periodTo: "2026-03-31",
  currency: "ZAR",
  includeExpenses: true,
  totalClients: 5,
  totalInvoices: 4,
  totalAmount: 125000.0,
  totalSent: 2,
  totalFailed: 1,
  createdBy: "user-1",
  createdAt: "2026-03-01T10:00:00Z",
  updatedAt: "2026-03-01T12:00:00Z",
  completedAt: "2026-03-01T12:00:00Z",
};

const mockItems: BillingRunItem[] = [
  {
    id: "item-1",
    clientId: "cust-1",
    clientName: "Acme Corp",
    status: "GENERATED",
    unbilledTimeAmount: 50000,
    unbilledExpenseAmount: 5000,
    unbilledTimeCount: 10,
    unbilledExpenseCount: 3,
    totalUnbilledAmount: 55000,
    hasPrerequisiteIssues: false,
    prerequisiteIssueReason: null,
    invoiceId: "inv-1",
    failureReason: null,
  },
  {
    id: "item-2",
    clientId: "cust-2",
    clientName: "Beta LLC",
    status: "FAILED",
    unbilledTimeAmount: 30000,
    unbilledExpenseAmount: 0,
    unbilledTimeCount: 5,
    unbilledExpenseCount: 0,
    totalUnbilledAmount: 30000,
    hasPrerequisiteIssues: false,
    prerequisiteIssueReason: null,
    invoiceId: null,
    failureReason: "Missing billing address",
  },
];

describe("BillingRunSummaryCards", () => {
  it("renders correct summary counts and total amount", () => {
    render(<BillingRunSummaryCards billingRun={mockBillingRun} />);

    expect(screen.getByText("5")).toBeInTheDocument();
    expect(screen.getByText("4")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
    expect(screen.getByText("1")).toBeInTheDocument();
    expect(screen.getByText("Clients")).toBeInTheDocument();
    expect(screen.getByText("Fee notes generated")).toBeInTheDocument();
    expect(screen.getByText("Sent")).toBeInTheDocument();
    expect(screen.getByText("Failed")).toBeInTheDocument();
    expect(screen.getByText("Total Amount")).toBeInTheDocument();
  });

  // Phase 91 666A: five kit StatCards in a container-query grid (no 5-up at a 712px column).
  it("renders five kit StatCards labelled as before, in a container-query grid", () => {
    render(<BillingRunSummaryCards billingRun={mockBillingRun} />);
    const cards = screen.getAllByRole("group");
    expect(cards).toHaveLength(5);
    const total = screen.getByRole("group", { name: "Total Amount" });
    expect(total.querySelector('[data-slot="stat-card-value"]')?.textContent).toBe(
      formatCurrency(125000, "ZAR")
    );
    const grid = cards[0].parentElement;
    expect(grid).toHaveClass("@5xl:grid-cols-5");
    expect(grid).not.toHaveClass("lg:grid-cols-5");
    expect(grid?.parentElement).toHaveClass("@container");
  });
});

describe("BillingRunItemsTable", () => {
  it("renders customer rows with correct data", () => {
    render(<BillingRunItemsTable items={mockItems} currency="ZAR" slug="test-org" />);

    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    expect(screen.getByText("Beta LLC")).toBeInTheDocument();
    expect(screen.getByText("View fee note")).toBeInTheDocument();
  });

  it("shows failure reason for failed items", () => {
    render(<BillingRunItemsTable items={mockItems} currency="ZAR" slug="test-org" />);

    expect(screen.getByText("Missing billing address")).toBeInTheDocument();
  });

  it("shows empty state when no items", () => {
    render(<BillingRunItemsTable items={[]} currency="ZAR" slug="test-org" />);

    expect(screen.getByText("No items in this fee run.")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  // F-666A-A2: fits a 710px frame at 1024; the Time/Disbursement breakdown hides, Total stays.
  it("hides only the Time/Disbursement breakdown below a 67rem frame and floors the prose columns", () => {
    render(<BillingRunItemsTable items={mockItems} currency="ZAR" slug="test-org" />);
    const labels = screen.getAllByRole("columnheader").map((h) => h.textContent);
    expect(labels).toEqual([
      "Client",
      "Status",
      "Time Amount",
      "Disbursement Amount",
      "Total",
      "Fee note",
      "Failure Reason",
    ]);
    const cells = within(screen.getAllByRole("row")[1]).getAllByRole("cell");
    expect(cells[2]).toHaveClass("hidden", "@min-[67rem]:table-cell");
    expect(cells[3]).toHaveClass("hidden", "@min-[67rem]:table-cell");
    for (const index of [0, 1, 4, 5, 6]) {
      expect(cells[index]).not.toHaveClass("hidden");
    }
    expect(cells[0].style.minWidth).toBe("12rem");
    expect(cells[6].style.minWidth).toBe("10rem");
    expect(cells[6]).toHaveClass("whitespace-normal");
  });

  // K-34: the item link uses the accent token, not the hard-coded teal-600.
  it("links a generated item's fee note with the accent token", () => {
    render(<BillingRunItemsTable items={mockItems} currency="ZAR" slug="test-org" />);
    const link = screen.getByRole("link", { name: "View fee note" });
    expect(link).toHaveAttribute("href", "/org/test-org/invoices/inv-1");
    expect(link).toHaveClass("text-(--button-accent)");
    expect(link).not.toHaveClass("text-teal-600");
  });
});

describe("BillingRunDetailActions", () => {
  it("shows cancel and approve buttons for COMPLETED status", () => {
    render(<BillingRunDetailActions slug="test-org" billingRunId="run-1" status="COMPLETED" />);

    expect(screen.getByText("Cancel Run")).toBeInTheDocument();
    expect(screen.getByText("Approve All Generated")).toBeInTheDocument();
  });

  it("shows only cancel button for PREVIEW status", () => {
    render(<BillingRunDetailActions slug="test-org" billingRunId="run-1" status="PREVIEW" />);

    expect(screen.getByText("Cancel Run")).toBeInTheDocument();
    expect(screen.queryByText("Approve All Generated")).not.toBeInTheDocument();
  });

  it("renders nothing for CANCELLED status", () => {
    const { container } = render(
      <BillingRunDetailActions slug="test-org" billingRunId="run-1" status="CANCELLED" />
    );

    expect(container.innerHTML).toBe("");
  });
});
