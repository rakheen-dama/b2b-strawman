import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

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
  includeRetainers: false,
  totalCustomers: 5,
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
    customerId: "cust-1",
    customerName: "Acme Corp",
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
    customerId: "cust-2",
    customerName: "Beta LLC",
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
    expect(screen.getByText("Customers")).toBeInTheDocument();
    expect(screen.getByText("Invoices Generated")).toBeInTheDocument();
    expect(screen.getByText("Sent")).toBeInTheDocument();
    expect(screen.getByText("Failed")).toBeInTheDocument();
    expect(screen.getByText("Total Amount")).toBeInTheDocument();
  });
});

describe("BillingRunItemsTable", () => {
  it("renders customer rows with correct data", () => {
    render(<BillingRunItemsTable items={mockItems} currency="ZAR" slug="test-org" />);

    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    expect(screen.getByText("Beta LLC")).toBeInTheDocument();
    expect(screen.getByText("View Invoice")).toBeInTheDocument();
  });

  it("shows failure reason for failed items", () => {
    render(<BillingRunItemsTable items={mockItems} currency="ZAR" slug="test-org" />);

    expect(screen.getByText("Missing billing address")).toBeInTheDocument();
  });

  it("shows empty state when no items", () => {
    render(<BillingRunItemsTable items={[]} currency="ZAR" slug="test-org" />);

    expect(screen.getByText("No items in this billing run.")).toBeInTheDocument();
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
