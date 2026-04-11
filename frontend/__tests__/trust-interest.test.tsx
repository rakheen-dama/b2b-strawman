import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, fireEvent } from "@testing-library/react";

// ── Mock server-only ─────────────────────────────────────────────
vi.mock("server-only", () => ({}));

// ── Mock server actions for interest ─────────────────────────────
const mockFetchInterestRuns = vi.fn();
const mockCreateInterestRun = vi.fn();
const mockCalculateInterest = vi.fn();
const mockApproveInterestRun = vi.fn();
const mockPostInterestRun = vi.fn();
const mockFetchInterestRunDetail = vi.fn();
const mockFetchLpffRates = vi.fn();
const mockAddLpffRate = vi.fn();

vi.mock("@/app/(app)/org/[slug]/trust-accounting/interest/actions", () => ({
  fetchInterestRuns: (...args: unknown[]) => mockFetchInterestRuns(...args),
  createInterestRun: (...args: unknown[]) => mockCreateInterestRun(...args),
  calculateInterest: (...args: unknown[]) => mockCalculateInterest(...args),
  approveInterestRun: (...args: unknown[]) => mockApproveInterestRun(...args),
  postInterestRun: (...args: unknown[]) => mockPostInterestRun(...args),
  fetchInterestRunDetail: (...args: unknown[]) => mockFetchInterestRunDetail(...args),
  fetchLpffRates: (...args: unknown[]) => mockFetchLpffRates(...args),
  addLpffRate: (...args: unknown[]) => mockAddLpffRate(...args),
}));

// ── Mock parent trust actions ────────────────────────────────────
vi.mock("@/app/(app)/org/[slug]/trust-accounting/actions", () => ({
  fetchTrustAccounts: vi.fn().mockResolvedValue([
    {
      id: "acc-1",
      accountName: "Main Trust",
      bankName: "FNB",
      branchCode: "250655",
      accountNumber: "1234567890",
      accountType: "GENERAL",
      isPrimary: true,
      requireDualApproval: false,
      paymentApprovalThreshold: null,
      status: "ACTIVE",
      openedDate: "2025-01-01",
      closedDate: null,
      notes: null,
      createdAt: "2025-01-01T00:00:00Z",
      updatedAt: "2025-01-01T00:00:00Z",
    },
  ]),
}));

// ── Mock org settings & capabilities ─────────────────────────────
vi.mock("@/lib/api/settings", () => ({
  getOrgSettings: vi.fn().mockResolvedValue({
    enabledModules: ["trust_accounting"],
    defaultCurrency: "ZAR",
  }),
}));

vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: vi.fn().mockResolvedValue({
    capabilities: ["VIEW_TRUST", "MANAGE_TRUST", "APPROVE_TRUST_PAYMENT"],
    role: "OWNER",
    isAdmin: false,
    isOwner: true,
  }),
}));

// ── Mock next/navigation ─────────────────────────────────────────
vi.mock("next/navigation", () => ({
  notFound: () => {
    throw new Error("NEXT_NOT_FOUND");
  },
  useRouter: () => ({
    push: vi.fn(),
    refresh: vi.fn(),
    replace: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

// ── Mock next/link ───────────────────────────────────────────────
vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

// ── Imports after mocks ──────────────────────────────────────────
import InterestPage from "@/app/(app)/org/[slug]/trust-accounting/interest/page";
import { InterestRunWizard } from "@/components/trust/InterestRunWizard";
import { LpffRateDialog } from "@/components/trust/LpffRateDialog";

// ── Helpers ──────────────────────────────────────────────────────

function makeRun(overrides: Record<string, unknown> = {}) {
  return {
    id: "run-1",
    trustAccountId: "acc-1",
    periodStart: "2026-01-01",
    periodEnd: "2026-03-31",
    lpffRateId: "rate-1",
    totalInterest: 12500,
    totalLpffShare: 6250,
    totalClientShare: 6250,
    status: "DRAFT",
    createdBy: "member-1",
    approvedBy: null,
    postedAt: null,
    createdAt: "2026-04-01T09:00:00Z",
    updatedAt: "2026-04-01T09:00:00Z",
    ...overrides,
  };
}

async function renderInterestPage() {
  const result = await InterestPage({
    params: Promise.resolve({ slug: "acme" }),
  });
  render(result);
}

// ── Tests ────────────────────────────────────────────────────────

describe("Trust Interest", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders interest runs table with mock data", async () => {
    mockFetchInterestRuns.mockResolvedValue([
      makeRun({
        id: "run-1",
        periodStart: "2026-01-01",
        periodEnd: "2026-03-31",
        status: "POSTED",
        postedAt: "2026-04-01T12:00:00Z",
      }),
      makeRun({
        id: "run-2",
        periodStart: "2026-04-01",
        periodEnd: "2026-06-30",
        status: "DRAFT",
        totalInterest: 8000,
        totalLpffShare: 4000,
        totalClientShare: 4000,
      }),
    ]);
    mockFetchLpffRates.mockResolvedValue([]);

    await renderInterestPage();

    expect(screen.getByTestId("interest-runs-table")).toBeInTheDocument();
    expect(screen.getByText("POSTED")).toBeInTheDocument();
    expect(screen.getByText("DRAFT")).toBeInTheDocument();
    expect(screen.getByText("2 runs found")).toBeInTheDocument();
  });

  it("wizard calculate step shows allocations table", async () => {
    mockCalculateInterest.mockResolvedValue({
      success: true,
      run: makeRun({ id: "run-calc", status: "DRAFT" }),
    });
    mockFetchInterestRunDetail.mockResolvedValue({
      run: makeRun({ id: "run-calc", status: "DRAFT" }),
      allocations: [
        {
          id: "alloc-1",
          interestRunId: "run-calc",
          customerId: "cust-001",
          averageDailyBalance: 100000,
          daysInPeriod: 90,
          grossInterest: 2500,
          lpffShare: 1250,
          clientShare: 1250,
          trustTransactionId: null,
          createdAt: "2026-04-01T09:00:00Z",
        },
      ],
    });

    render(
      <InterestRunWizard
        accountId="acc-1"
        open={true}
        onOpenChange={vi.fn()}
        onSuccess={vi.fn()}
        canApprove={true}
        currency="ZAR"
      />
    );

    // Wizard opens at step 1 by default; we need to create first to get to step 2.
    // Instead, let's mock createInterestRun and advance through step 1.
    mockCreateInterestRun.mockResolvedValue({
      success: true,
      run: makeRun({ id: "run-calc" }),
    });

    // Fill in the period fields
    const periodStartInput = screen.getByLabelText("Period Start");
    fireEvent.change(periodStartInput, { target: { value: "2026-01-01" } });

    const periodEndInput = screen.getByLabelText("Period End");
    fireEvent.change(periodEndInput, { target: { value: "2026-03-31" } });

    // Submit to advance to step 2
    const createButton = screen.getByRole("button", { name: "Create Run" });
    fireEvent.click(createButton);

    // Wait for step 2
    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Calculate Interest" })).toBeInTheDocument();
    });

    // Click "Calculate Interest"
    const calcButton = screen.getByRole("button", {
      name: "Calculate Interest",
    });
    fireEvent.click(calcButton);

    // Wait for allocations table
    await waitFor(() => {
      expect(screen.getByTestId("allocations-table")).toBeInTheDocument();
    });

    expect(screen.getByText("cust-001")).toBeInTheDocument();
  });

  it("LPFF rate dialog submits with valid data", async () => {
    mockAddLpffRate.mockResolvedValue({
      success: true,
      rate: {
        id: "rate-new",
        trustAccountId: "acc-1",
        effectiveFrom: "2026-04-01",
        ratePercent: 8.5,
        lpffSharePercent: 50,
        notes: "",
        createdAt: "2026-04-05T10:00:00Z",
      },
    });

    render(
      <LpffRateDialog accountId="acc-1" open={true} onOpenChange={vi.fn()} onSuccess={vi.fn()} />
    );

    const effectiveInput = screen.getByLabelText("Effective From");
    fireEvent.change(effectiveInput, { target: { value: "2026-04-01" } });

    const rateInput = screen.getByPlaceholderText("e.g. 8.5");
    fireEvent.change(rateInput, { target: { value: "8.5" } });

    const shareInput = screen.getByPlaceholderText("e.g. 50");
    fireEvent.change(shareInput, { target: { value: "50" } });

    const form = rateInput.closest("form")!;
    fireEvent.submit(form);

    await waitFor(() => {
      expect(mockAddLpffRate).toHaveBeenCalledWith(
        "acc-1",
        expect.objectContaining({
          effectiveFrom: "2026-04-01",
          ratePercent: 8.5,
          lpffSharePercent: 50,
        })
      );
    });
  });

  it("post action transitions run to POSTED", async () => {
    mockPostInterestRun.mockResolvedValue({
      success: true,
      run: makeRun({ id: "run-post", status: "POSTED", postedAt: "2026-04-05T12:00:00Z" }),
    });

    // We need to render the wizard and advance to step 4.
    // Mock the create, calculate, detail, and approve steps.
    mockCreateInterestRun.mockResolvedValue({
      success: true,
      run: makeRun({ id: "run-post" }),
    });
    mockCalculateInterest.mockResolvedValue({
      success: true,
      run: makeRun({ id: "run-post" }),
    });
    mockFetchInterestRunDetail.mockResolvedValue({
      run: makeRun({ id: "run-post" }),
      allocations: [
        {
          id: "alloc-p1",
          interestRunId: "run-post",
          customerId: "cust-001",
          averageDailyBalance: 50000,
          daysInPeriod: 90,
          grossInterest: 1250,
          lpffShare: 625,
          clientShare: 625,
          trustTransactionId: null,
          createdAt: "2026-04-01T09:00:00Z",
        },
      ],
    });
    mockApproveInterestRun.mockResolvedValue({
      success: true,
      run: makeRun({ id: "run-post", status: "APPROVED" }),
    });

    const onSuccess = vi.fn();

    render(
      <InterestRunWizard
        accountId="acc-1"
        open={true}
        onOpenChange={vi.fn()}
        onSuccess={onSuccess}
        canApprove={true}
        currency="ZAR"
      />
    );

    // Step 1: Create
    fireEvent.change(screen.getByLabelText("Period Start"), {
      target: { value: "2026-01-01" },
    });
    fireEvent.change(screen.getByLabelText("Period End"), {
      target: { value: "2026-03-31" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Create Run" }));

    // Step 2: Calculate
    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Calculate Interest" })).toBeInTheDocument();
    });
    fireEvent.click(screen.getByRole("button", { name: "Calculate Interest" }));

    // Wait for allocations, then click Approve to advance
    await waitFor(() => {
      expect(screen.getByTestId("allocations-table")).toBeInTheDocument();
    });
    fireEvent.click(screen.getByRole("button", { name: "Approve" }));

    // Step 3: Approve
    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Approve Run" })).toBeInTheDocument();
    });
    fireEvent.click(screen.getByRole("button", { name: "Approve Run" }));

    // Step 4: Post
    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Post to Ledger" })).toBeInTheDocument();
    });
    fireEvent.click(screen.getByRole("button", { name: "Post to Ledger" }));

    await waitFor(() => {
      expect(mockPostInterestRun).toHaveBeenCalledWith("run-post");
    });
  });
});
