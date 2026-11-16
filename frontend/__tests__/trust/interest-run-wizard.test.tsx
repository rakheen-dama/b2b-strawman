import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, fireEvent, within } from "@testing-library/react";

// 630B (G88-F39): InterestRunWizard resumes a persisted run (initialRun) at step 2. Phase 92 645A:
// the wizard has no approve or post step (section 86(5)).

vi.mock("server-only", () => ({}));

const mockCreateInterestRun = vi.fn();
const mockCalculateInterest = vi.fn();
const mockDeleteInterestRun = vi.fn();
const mockFetchInterestRunDetail = vi.fn();

vi.mock("@/app/(app)/org/[slug]/trust-accounting/interest/actions", () => ({
  fetchInterestRuns: vi.fn(),
  createInterestRun: (...args: unknown[]) => mockCreateInterestRun(...args),
  calculateInterest: (...args: unknown[]) => mockCalculateInterest(...args),
  deleteInterestRun: (...args: unknown[]) => mockDeleteInterestRun(...args),
  fetchInterestRunDetail: (...args: unknown[]) => mockFetchInterestRunDetail(...args),
  fetchLpffRates: vi.fn(),
  addLpffRate: vi.fn(),
}));

const mockRefresh = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    refresh: mockRefresh,
    replace: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

import { InterestRunWizard } from "@/components/trust/InterestRunWizard";
import type { InterestAllocation, InterestRun } from "@/lib/types/trust";

const CREATOR = "member-creator";
const OTHER = "member-other";

function makeRun(overrides: Partial<InterestRun> = {}): InterestRun {
  return {
    id: "run-1",
    trustAccountId: "acc-1",
    periodStart: "2026-01-01",
    periodEnd: "2026-01-31",
    lpffRateId: "rate-1",
    totalInterest: 100,
    totalLpffShare: 50,
    totalClientShare: 50,
    status: "DRAFT",
    createdBy: CREATOR,
    approvedBy: null,
    postedAt: null,
    createdAt: "2026-02-01T09:00:00Z",
    updatedAt: "2026-02-01T09:00:00Z",
    allocationCount: 0,
    ...overrides,
  };
}

const ALLOCATION: InterestAllocation = {
  id: "alloc-1",
  interestRunId: "run-1",
  clientId: "cust-1",
  clientName: "Moroka Family Trust",
  averageDailyBalance: 1000,
  daysInPeriod: 31,
  grossInterest: 100,
  lpffShare: 50,
  clientShare: 50,
  trustTransactionId: null,
  lpffRateId: "rate-1",
  statutoryRateApplied: false,
  lpffSharePercent: 0.5,
  createdAt: "2026-02-01T09:00:00Z",
};

interface WizardProps {
  run?: InterestRun;
  open?: boolean;
}

function wizard({ run, open = true }: WizardProps) {
  return (
    <InterestRunWizard
      accountId="acc-1"
      open={open}
      onOpenChange={vi.fn()}
      currency="ZAR"
      initialRun={run}
    />
  );
}

function resumeWith(run: InterestRun, allocations: InterestAllocation[]) {
  mockFetchInterestRunDetail.mockResolvedValue({ run, allocations });
}

describe("InterestRunWizard — resume a persisted run (630B)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("a never-calculated draft resumes at step 2 (Calculate), never the create form", async () => {
    const run = makeRun();
    resumeWith(run, []);
    render(wizard({ run }));

    expect(screen.queryByLabelText("Period Start")).toBeNull();
    await waitFor(() => expect(screen.getByText("Step 2 of 2 — Calculate")).toBeInTheDocument());
    expect(mockFetchInterestRunDetail).toHaveBeenCalledWith("run-1");
    expect(screen.getByRole("button", { name: "Calculate Interest" })).toBeInTheDocument();
    expect(screen.queryByLabelText("Period Start")).toBeNull();
  });

  it("a calculated draft resumes at step 2 with its allocations, and offers no approve step", async () => {
    const run = makeRun({ allocationCount: 1 });
    resumeWith(run, [ALLOCATION]);
    render(wizard({ run }));

    await waitFor(() => expect(screen.getByText("Step 2 of 2 — Calculate")).toBeInTheDocument());
    expect(screen.getByTestId("allocations-table")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Calculate Interest" })).toBeNull();
    expect(screen.queryAllByRole("button", { name: /approve|post|review/i })).toEqual([]);
  });

  it("an approved run (approved before 645A) opens read-only at step 2 — no Post to Ledger", async () => {
    const run = makeRun({ status: "APPROVED", approvedBy: OTHER, allocationCount: 1 });
    resumeWith(run, [ALLOCATION]);
    render(wizard({ run }));

    await waitFor(() => expect(screen.getByText("Step 2 of 2 — Calculate")).toBeInTheDocument());
    expect(screen.getByText("Approved")).toBeInTheDocument(); // status badge, label map
    expect(screen.queryByRole("button", { name: "Post to Ledger" })).toBeNull();
    expect(screen.queryAllByRole("button", { name: /approve|post/i })).toEqual([]);
  });

  it("after calculating, a new run shows its allocations and offers no approve or post step", async () => {
    const created = makeRun({ id: "run-new" });
    mockCreateInterestRun.mockResolvedValue({ success: true, run: created });
    mockCalculateInterest.mockResolvedValue({
      success: true,
      run: { ...created, allocationCount: 1 },
    });
    mockFetchInterestRunDetail.mockResolvedValue({
      run: { ...created, allocationCount: 1 },
      allocations: [{ ...ALLOCATION, interestRunId: "run-new" }],
    });
    render(wizard({}));

    fireEvent.change(screen.getByLabelText("Period Start"), { target: { value: "2026-01-01" } });
    fireEvent.change(screen.getByLabelText("Period End"), { target: { value: "2026-01-31" } });
    fireEvent.click(screen.getByRole("button", { name: "Create Run" }));
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Calculate Interest" })).toBeInTheDocument()
    );
    fireEvent.click(screen.getByRole("button", { name: "Calculate Interest" }));
    await waitFor(() => expect(screen.getByTestId("allocations-table")).toBeInTheDocument());

    expect(screen.getByText("Step 2 of 2 — Calculate")).toBeInTheDocument();
    expect(screen.queryAllByRole("button", { name: /approve|post|review/i })).toEqual([]);
    expect(mockRefresh).toHaveBeenCalled();
  });

  it("closing and re-opening reloads the run from the server", async () => {
    const run = makeRun({ allocationCount: 1 });
    resumeWith(run, [ALLOCATION]);
    const { rerender } = render(wizard({ run }));
    await waitFor(() => expect(mockFetchInterestRunDetail).toHaveBeenCalledTimes(1));

    rerender(wizard({ run, open: false }));
    rerender(wizard({ run, open: true }));

    await waitFor(() => expect(mockFetchInterestRunDetail).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(screen.getByText("Step 2 of 2 — Calculate")).toBeInTheDocument());
  });
});

describe("InterestRunWizard — allocations table (G89-F10)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("shows the client's name and the LPFF rate applied, never the client UUID", async () => {
    const MOROKA_ID = "6f1c2a9e-1b2c-4d3e-8f90-123456789abc";
    const QC_ID = "0a9b8c7d-6e5f-4a3b-9c2d-1e0f9a8b7c6d";
    const GONE_ID = "deadbeef-0000-4000-8000-000000000000";
    const created = makeRun({ id: "run-f10" });
    mockCreateInterestRun.mockResolvedValue({ success: true, run: created });
    mockCalculateInterest.mockResolvedValue({
      success: true,
      run: { ...created, allocationCount: 3 },
    });
    mockFetchInterestRunDetail.mockResolvedValue({
      run: { ...created, allocationCount: 3 },
      allocations: [
        {
          ...ALLOCATION,
          id: "a-moroka",
          interestRunId: "run-f10",
          clientId: MOROKA_ID,
          clientName: "Moroka Family Trust",
          lpffSharePercent: 0.5,
        },
        {
          ...ALLOCATION,
          id: "a-qc",
          interestRunId: "run-f10",
          clientId: QC_ID,
          clientName: "QuickCollect Services",
          lpffRateId: null,
          statutoryRateApplied: true,
          lpffSharePercent: 0.05,
        },
        {
          ...ALLOCATION,
          id: "a-gone",
          interestRunId: "run-f10",
          clientId: GONE_ID,
          clientName: null,
          lpffSharePercent: 0.5,
        },
      ],
    });
    render(wizard({}));

    fireEvent.change(screen.getByLabelText("Period Start"), { target: { value: "2026-01-01" } });
    fireEvent.change(screen.getByLabelText("Period End"), { target: { value: "2026-01-31" } });
    fireEvent.click(screen.getByRole("button", { name: "Create Run" }));
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Calculate Interest" })).toBeInTheDocument()
    );
    fireEvent.click(screen.getByRole("button", { name: "Calculate Interest" }));
    const table = await screen.findByTestId("allocations-table");

    expect(within(table).getByRole("columnheader", { name: "Client" })).toBeInTheDocument();
    expect(within(table).queryByRole("columnheader", { name: "Client ID" })).toBeNull();

    const moroka = within(table).getByRole("row", { name: /Moroka Family Trust/ });
    expect(moroka).toHaveTextContent("LPFF arrangement rate 50.00%");
    const qc = within(table).getByRole("row", { name: /QuickCollect Services/ });
    expect(qc).toHaveTextContent("5% (statutory)");

    // A missing client name falls back to a short id, never the full UUID
    expect(within(table).getByText("deadbeef")).toBeInTheDocument();
    for (const uuid of [MOROKA_ID, QC_ID, GONE_ID]) {
      expect(table).not.toHaveTextContent(uuid);
    }
    expect(within(table).queryByText("Arrangement")).toBeNull();
  });
});
