import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock server-only
vi.mock("server-only", () => ({}));

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

// Mock server actions
const mockCreateBillingRun = vi.fn();
const mockLoadPreview = vi.fn();

vi.mock(
  "@/app/(app)/org/[slug]/invoices/billing-runs/new/actions",
  () => ({
    createBillingRunAction: (...args: unknown[]) =>
      mockCreateBillingRun(...args),
    loadPreviewAction: (...args: unknown[]) => mockLoadPreview(...args),
    getUnbilledSummaryAction: vi.fn().mockResolvedValue({ success: true }),
  }),
);

import { BillingRunWizard } from "@/components/billing-runs/billing-run-wizard";
import { ConfigureStep } from "@/components/billing-runs/configure-step";
import { CustomerSelectionStep } from "@/components/billing-runs/customer-selection-step";
import type { BillingRunPreview } from "@/lib/api/billing-runs";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const mockPreview: BillingRunPreview = {
  billingRunId: "run-1",
  totalCustomers: 2,
  totalUnbilledAmount: 85000,
  items: [
    {
      id: "item-1",
      customerId: "cust-1",
      customerName: "Acme Corp",
      status: "PENDING",
      unbilledTimeAmount: 50000,
      unbilledExpenseAmount: 5000,
      unbilledTimeCount: 10,
      unbilledExpenseCount: 3,
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
      unbilledTimeCount: 5,
      unbilledExpenseCount: 0,
      totalUnbilledAmount: 30000,
      hasPrerequisiteIssues: false,
      prerequisiteIssueReason: null,
      invoiceId: null,
      failureReason: null,
    },
  ],
};

describe("BillingRunWizard", () => {
  it("renders step indicator with all 5 step labels", () => {
    render(<BillingRunWizard slug="test-org" />);

    expect(screen.getByText("Configure")).toBeInTheDocument();
    expect(screen.getByText("Select Customers")).toBeInTheDocument();
    expect(screen.getByText("Review & Cherry-Pick")).toBeInTheDocument();
    expect(screen.getByText("Review Drafts")).toBeInTheDocument();
    expect(screen.getByText("Send")).toBeInTheDocument();
  });
});

describe("ConfigureStep", () => {
  it("renders form fields for period, retainers, and notes", () => {
    const onNext = vi.fn();
    render(<ConfigureStep slug="test-org" onNext={onNext} />);

    expect(screen.getByLabelText("Period From")).toBeInTheDocument();
    expect(screen.getByLabelText("Period To")).toBeInTheDocument();
    expect(screen.getByLabelText("Include retainers")).toBeInTheDocument();
    expect(screen.getByLabelText("Notes (optional)")).toBeInTheDocument();
  });

  it("shows validation errors when dates are missing", async () => {
    const user = userEvent.setup();
    const onNext = vi.fn();
    render(<ConfigureStep slug="test-org" onNext={onNext} />);

    await user.click(screen.getByRole("button", { name: "Next" }));

    expect(
      screen.getByText("Period From is required."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Period To is required."),
    ).toBeInTheDocument();
    expect(mockCreateBillingRun).not.toHaveBeenCalled();
  });
});

describe("CustomerSelectionStep", () => {
  it("shows loading state then customer data", async () => {
    mockLoadPreview.mockResolvedValue({
      success: true,
      preview: mockPreview,
    });

    render(
      <CustomerSelectionStep
        slug="test-org"
        billingRunId="run-1"
        onBack={vi.fn()}
        onNext={vi.fn()}
      />,
    );

    // Wait for data to load
    await waitFor(() => {
      expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    });
    expect(screen.getByText("Beta LLC")).toBeInTheDocument();
  });

  it("checkbox toggles update selected count", async () => {
    const user = userEvent.setup();
    mockLoadPreview.mockResolvedValue({
      success: true,
      preview: mockPreview,
    });

    render(
      <CustomerSelectionStep
        slug="test-org"
        billingRunId="run-1"
        onBack={vi.fn()}
        onNext={vi.fn()}
      />,
    );

    // Wait for data to load
    await waitFor(() => {
      expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    });

    // Both selected by default
    expect(screen.getByText("2")).toBeInTheDocument();

    // Deselect one customer
    await user.click(screen.getByLabelText("Select Acme Corp"));

    expect(screen.getByText("1")).toBeInTheDocument();
  });

  it("summary bar shows correct totals", async () => {
    mockLoadPreview.mockResolvedValue({
      success: true,
      preview: mockPreview,
    });

    render(
      <CustomerSelectionStep
        slug="test-org"
        billingRunId="run-1"
        onBack={vi.fn()}
        onNext={vi.fn()}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    });

    // 2 customers selected with total shown in summary bar
    expect(screen.getByText("2")).toBeInTheDocument();
    expect(screen.getByText(/customers selected/)).toBeInTheDocument();
  });
});
