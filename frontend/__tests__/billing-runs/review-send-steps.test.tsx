import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock server-only
vi.mock("server-only", () => ({}));

// Mock next/navigation
const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, refresh: vi.fn() }),
}));

// Mock server actions — assign to const BEFORE vi.mock
const mockGenerate = vi.fn();
const mockGetItems = vi.fn();
const mockBatchApprove = vi.fn();
const mockBatchSend = vi.fn();
const mockGetBillingRun = vi.fn();

vi.mock("@/app/(app)/org/[slug]/invoices/billing-runs/new/billing-run-actions", () => ({
  createBillingRunAction: vi.fn(),
  loadPreviewAction: vi.fn(),
  getUnbilledSummaryAction: vi.fn().mockResolvedValue({ success: true }),
  getUnbilledTimeAction: vi.fn().mockResolvedValue({ success: true, entries: [] }),
  getUnbilledExpensesAction: vi.fn().mockResolvedValue({ success: true, entries: [] }),
  updateSelectionsAction: vi.fn().mockResolvedValue({ success: true }),
  excludeCustomerAction: vi.fn().mockResolvedValue({ success: true }),
  includeCustomerAction: vi.fn().mockResolvedValue({ success: true }),
  getRetainerPreviewAction: vi.fn().mockResolvedValue({ success: true, retainers: [] }),
}));

vi.mock("@/app/(app)/org/[slug]/invoices/billing-runs/new/billing-step-actions", () => ({
  generateAction: (...args: unknown[]) => mockGenerate(...args),
  getItemsAction: (...args: unknown[]) => mockGetItems(...args),
  batchApproveAction: (...args: unknown[]) => mockBatchApprove(...args),
  batchSendAction: (...args: unknown[]) => mockBatchSend(...args),
  getBillingRunAction: (...args: unknown[]) => mockGetBillingRun(...args),
}));

// Mock invoice actions
vi.mock("@/app/(app)/org/[slug]/invoices/invoice-crud-actions", () => ({
  updateInvoice: vi.fn().mockResolvedValue({ success: true }),
}));

import { ReviewDraftsStep } from "@/components/billing-runs/review-drafts-step";
import { SendStep } from "@/components/billing-runs/send-step";
import type { BillingRunItem } from "@/lib/api/billing-runs";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const mockGeneratedItems: BillingRunItem[] = [
  {
    id: "item-1",
    customerId: "cust-1",
    customerName: "Acme Corp",
    status: "GENERATED",
    unbilledTimeAmount: 50000,
    unbilledExpenseAmount: 5000,
    unbilledTimeCount: 3,
    unbilledExpenseCount: 1,
    totalUnbilledAmount: 55000,
    hasPrerequisiteIssues: false,
    prerequisiteIssueReason: null,
    invoiceId: "inv-abc-12345678",
    failureReason: null,
  },
  {
    id: "item-2",
    customerId: "cust-2",
    customerName: "Widget Inc",
    status: "FAILED",
    unbilledTimeAmount: 30000,
    unbilledExpenseAmount: 0,
    unbilledTimeCount: 2,
    unbilledExpenseCount: 0,
    totalUnbilledAmount: 30000,
    hasPrerequisiteIssues: false,
    prerequisiteIssueReason: null,
    invoiceId: null,
    failureReason: "Customer has no billing address",
  },
];

// After batchApprove, the backend still returns items with status GENERATED —
// the approval is tracked at the billing run level, not per-item status.
const mockApprovedItems: BillingRunItem[] = [
  {
    id: "item-1",
    customerId: "cust-1",
    customerName: "Acme Corp",
    status: "GENERATED",
    unbilledTimeAmount: 50000,
    unbilledExpenseAmount: 5000,
    unbilledTimeCount: 3,
    unbilledExpenseCount: 1,
    totalUnbilledAmount: 55000,
    hasPrerequisiteIssues: false,
    prerequisiteIssueReason: null,
    invoiceId: "inv-abc-12345678",
    failureReason: null,
  },
];

describe("ReviewDraftsStep", () => {
  it("shows generated invoices after generation completes", async () => {
    mockGenerate.mockResolvedValue({ success: true, billingRun: { id: "run-1" } });
    mockGetItems.mockResolvedValue({ success: true, items: mockGeneratedItems });

    render(
      <ReviewDraftsStep
        slug="test-org"
        billingRunId="run-1"
        currency="ZAR"
        onBack={vi.fn()}
        onNext={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    });
    expect(screen.getByText("Widget Inc")).toBeInTheDocument();
  });

  it("shows failed items with failure reason", async () => {
    mockGenerate.mockResolvedValue({ success: true, billingRun: { id: "run-1" } });
    mockGetItems.mockResolvedValue({ success: true, items: mockGeneratedItems });

    render(
      <ReviewDraftsStep
        slug="test-org"
        billingRunId="run-1"
        currency="ZAR"
        onBack={vi.fn()}
        onNext={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByText("Failed Invoices")).toBeInTheDocument();
    });
    expect(screen.getByText("Widget Inc: Customer has no billing address")).toBeInTheDocument();
  });

  it("calls batchApprove and advances on Approve All click", async () => {
    mockGenerate.mockResolvedValue({ success: true, billingRun: { id: "run-1" } });
    mockGetItems.mockResolvedValue({
      success: true,
      items: [mockGeneratedItems[0]],
    });
    mockBatchApprove.mockResolvedValue({
      success: true,
      result: { successCount: 1, failureCount: 0, failures: [] },
    });

    const onNext = vi.fn();

    render(
      <ReviewDraftsStep
        slug="test-org"
        billingRunId="run-1"
        currency="ZAR"
        onBack={vi.fn()}
        onNext={onNext}
      />
    );

    await waitFor(() => {
      expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    });

    const approveBtn = screen.getByRole("button", {
      name: /approve all/i,
    });
    await userEvent.click(approveBtn);

    await waitFor(() => {
      expect(mockBatchApprove).toHaveBeenCalledWith("run-1");
    });

    await waitFor(() => {
      expect(onNext).toHaveBeenCalled();
    });
  });
});

describe("SendStep", () => {
  it("shows approved invoices table", async () => {
    mockGetItems.mockResolvedValue({ success: true, items: mockApprovedItems });

    render(<SendStep slug="test-org" billingRunId="run-1" currency="ZAR" onBack={vi.fn()} />);

    await waitFor(() => {
      expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    });
    expect(screen.getByText("Approved")).toBeInTheDocument();
  });

  it("shows send confirmation dialog when Send All is clicked", async () => {
    mockGetItems.mockResolvedValue({ success: true, items: mockApprovedItems });

    render(<SendStep slug="test-org" billingRunId="run-1" currency="ZAR" onBack={vi.fn()} />);

    await waitFor(() => {
      expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    });

    const sendBtn = screen.getByRole("button", { name: /send all/i });
    await userEvent.click(sendBtn);

    await waitFor(() => {
      expect(screen.getByText("Confirm Send")).toBeInTheDocument();
    });
  });

  it("navigates to billing runs list on Done click", async () => {
    mockGetItems.mockResolvedValue({ success: true, items: mockApprovedItems });
    mockBatchSend.mockResolvedValue({
      success: true,
      result: { successCount: 1, failureCount: 0, failures: [] },
    });
    mockGetBillingRun.mockResolvedValue({
      success: true,
      billingRun: { id: "run-1" },
    });

    render(<SendStep slug="test-org" billingRunId="run-1" currency="ZAR" onBack={vi.fn()} />);

    // Wait for items to load
    await waitFor(() => {
      expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    });

    // Open confirmation dialog and confirm send
    const sendBtn = screen.getByRole("button", { name: /send all/i });
    await userEvent.click(sendBtn);

    await waitFor(() => {
      expect(screen.getByText("Confirm Send")).toBeInTheDocument();
    });

    const confirmBtn = screen.getByRole("button", {
      name: /send 1 invoices/i,
    });
    await userEvent.click(confirmBtn);

    // Wait for completion and click Done
    await waitFor(() => {
      expect(screen.getByText("Billing Run Complete")).toBeInTheDocument();
    });

    const doneBtn = screen.getByRole("button", { name: /done/i });
    await userEvent.click(doneBtn);

    expect(mockPush).toHaveBeenCalledWith("/org/test-org/invoices/billing-runs");
  });
});
