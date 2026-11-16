import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, fireEvent } from "@testing-library/react";

// ── Mock server-only ─────────────────────────────────────────────
vi.mock("server-only", () => ({}));

// ── Mock server actions for interest ─────────────────────────────
const mockFetchInterestRuns = vi.fn();
const mockCreateInterestRun = vi.fn();
const mockCalculateInterest = vi.fn();
const mockDeleteInterestRun = vi.fn();
const mockFetchInterestRunDetail = vi.fn();
const mockFetchLpffRates = vi.fn();
const mockAddLpffRate = vi.fn();

vi.mock("@/app/(app)/org/[slug]/trust-accounting/interest/actions", () => ({
  fetchInterestRuns: (...args: unknown[]) => mockFetchInterestRuns(...args),
  createInterestRun: (...args: unknown[]) => mockCreateInterestRun(...args),
  calculateInterest: (...args: unknown[]) => mockCalculateInterest(...args),
  deleteInterestRun: (...args: unknown[]) => mockDeleteInterestRun(...args),
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

const mockFetchMyCapabilities = vi.fn();
vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: (...args: unknown[]) => mockFetchMyCapabilities(...args),
}));

const OWNER_CAPS = {
  capabilities: ["VIEW_TRUST", "MANAGE_TRUST", "APPROVE_TRUST_PAYMENT"],
  role: "OWNER",
  isAdmin: false,
  isOwner: true,
  memberId: "member-viewer",
};

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
    allocationCount: 0,
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
    mockFetchMyCapabilities.mockResolvedValue(OWNER_CAPS);
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
    // ADR-362: the status cell renders the label map, never the raw enum
    expect(screen.getByText("Posted")).toBeInTheDocument();
    expect(screen.getByText("Draft")).toBeInTheDocument();
    expect(screen.queryByText("POSTED")).toBeNull();
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
          clientId: "cust-001",
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
      <InterestRunWizard accountId="acc-1" open={true} onOpenChange={vi.fn()} currency="ZAR" />
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

  // ── Phase 92 645A (§16.6.2): no control approves or posts a run; the page carries the notice ──

  const NOTICE =
    "Calculated interest is not posted. Interest on trust money vests in the Legal Practitioners' Fidelity Fund (section 86(5) of the Legal Practice Act). Record interest from the bank statement when you reconcile.";

  // Every run status the page can list: never-calculated draft, calculated draft, approved, posted
  const ALL_STATUSES = [
    makeRun({ id: "d0", periodStart: "2025-01-01", periodEnd: "2025-01-31" }),
    makeRun({ id: "d2", periodStart: "2025-02-01", periodEnd: "2025-02-28", allocationCount: 2 }),
    makeRun({
      id: "ap",
      periodStart: "2025-03-01",
      periodEnd: "2025-03-31",
      status: "APPROVED",
      approvedBy: "member-2",
      allocationCount: 1,
    }),
    makeRun({
      id: "po",
      periodStart: "2025-04-01",
      periodEnd: "2025-04-30",
      status: "POSTED",
      approvedBy: "member-2",
      postedAt: "2025-05-02T10:00:00Z",
      allocationCount: 1,
    }),
  ];

  // Names the posting controls had before Phase 92 645A (and anything else that would post or approve)
  const POSTING_CONTROL = /post|approve/i;

  it("carries the section 86(5) notice, verbatim", async () => {
    mockFetchInterestRuns.mockResolvedValue([]);
    mockFetchLpffRates.mockResolvedValue([]);
    await renderInterestPage();
    expect(screen.getByTestId("interest-posting-notice")).toHaveTextContent(NOTICE);
  });

  it.each([
    ["owner", { ...OWNER_CAPS }],
    ["admin", { ...OWNER_CAPS, role: "ADMIN", isAdmin: true, isOwner: false }],
    [
      "member with MANAGE_TRUST",
      {
        capabilities: ["VIEW_TRUST", "MANAGE_TRUST"],
        role: "member",
        isAdmin: false,
        isOwner: false,
        memberId: "m-1",
      },
    ],
    [
      "member with APPROVE_TRUST_PAYMENT",
      {
        capabilities: ["VIEW_TRUST", "APPROVE_TRUST_PAYMENT"],
        role: "member",
        isAdmin: false,
        isOwner: false,
        memberId: "m-2",
      },
    ],
    [
      "member with VIEW_TRUST only",
      {
        capabilities: ["VIEW_TRUST"],
        role: "member",
        isAdmin: false,
        isOwner: false,
        memberId: "m-3",
      },
    ],
  ])(
    "no Post, Post to Ledger, Approve or Review & approve control for any run status — %s",
    async (_role, caps) => {
      mockFetchMyCapabilities.mockResolvedValue(caps);
      mockFetchInterestRuns.mockResolvedValue(ALL_STATUSES);
      mockFetchLpffRates.mockResolvedValue([]);
      await renderInterestPage();

      expect(screen.getByTestId("interest-runs-table")).toBeInTheDocument();
      for (const name of ["Post", "Post to Ledger", "Approve", "Approve Run", "Review & approve"]) {
        expect(screen.queryByRole("button", { name })).toBeNull();
      }
      expect(screen.queryAllByRole("button", { name: POSTING_CONTROL })).toEqual([]);
      expect(screen.getByTestId("interest-posting-notice")).toHaveTextContent(NOTICE);
    }
  );

  it.each(["DRAFT", "APPROVED", "POSTED"] as const)(
    "the wizard on a calculated %s run stops at the calculation — no approve or post step",
    async (status) => {
      const run = makeRun({ id: `run-${status}`, status, allocationCount: 1 });
      mockFetchInterestRunDetail.mockResolvedValue({
        run,
        allocations: [
          {
            id: "alloc-w",
            interestRunId: run.id,
            clientId: "cust-001",
            averageDailyBalance: 1000,
            daysInPeriod: 31,
            grossInterest: 10,
            lpffShare: 5,
            clientShare: 5,
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
          currency="ZAR"
          initialRun={run as never}
        />
      );
      await waitFor(() => expect(screen.getByText("Step 2 of 2 — Calculate")).toBeInTheDocument());
      expect(screen.getByTestId("allocations-table")).toBeInTheDocument();
      expect(screen.queryAllByRole("button", { name: POSTING_CONTROL })).toEqual([]);
    }
  );
});
