import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, fireEvent } from "@testing-library/react";

// 630B (G88-F39): InterestRunWizard resumes a persisted run (initialRun) at the step its state
// implies, and hides self-approval from the run's creator (the backend still refuses it).

vi.mock("server-only", () => ({}));

const mockCreateInterestRun = vi.fn();
const mockCalculateInterest = vi.fn();
const mockApproveInterestRun = vi.fn();
const mockPostInterestRun = vi.fn();
const mockDeleteInterestRun = vi.fn();
const mockFetchInterestRunDetail = vi.fn();

vi.mock("@/app/(app)/org/[slug]/trust-accounting/interest/actions", () => ({
  fetchInterestRuns: vi.fn(),
  createInterestRun: (...args: unknown[]) => mockCreateInterestRun(...args),
  calculateInterest: (...args: unknown[]) => mockCalculateInterest(...args),
  approveInterestRun: (...args: unknown[]) => mockApproveInterestRun(...args),
  postInterestRun: (...args: unknown[]) => mockPostInterestRun(...args),
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
  averageDailyBalance: 1000,
  daysInPeriod: 31,
  grossInterest: 100,
  lpffShare: 50,
  clientShare: 50,
  trustTransactionId: null,
  lpffRateId: "rate-1",
  statutoryRateApplied: false,
  createdAt: "2026-02-01T09:00:00Z",
};

interface WizardProps {
  run?: InterestRun;
  memberId?: string | null;
  canApprove?: boolean;
  canManage?: boolean;
  open?: boolean;
}

function wizard({ run, memberId, canApprove = true, canManage, open = true }: WizardProps) {
  return (
    <InterestRunWizard
      accountId="acc-1"
      open={open}
      onOpenChange={vi.fn()}
      onSuccess={vi.fn()}
      canApprove={canApprove}
      canManage={canManage}
      currency="ZAR"
      memberId={memberId}
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
    render(wizard({ run, memberId: OTHER }));

    expect(screen.queryByLabelText("Period Start")).toBeNull();
    await waitFor(() => expect(screen.getByText("Step 2 of 4 — Calculate")).toBeInTheDocument());
    expect(mockFetchInterestRunDetail).toHaveBeenCalledWith("run-1");
    expect(screen.getByRole("button", { name: "Calculate Interest" })).toBeInTheDocument();
    expect(screen.queryByLabelText("Period Start")).toBeNull();
  });

  it("a calculated draft resumes at step 3 for a second member, who can approve it", async () => {
    const run = makeRun({ allocationCount: 1 });
    resumeWith(run, [ALLOCATION]);
    mockApproveInterestRun.mockResolvedValue({
      success: true,
      run: makeRun({ status: "APPROVED", approvedBy: OTHER, allocationCount: 1 }),
    });
    render(wizard({ run, memberId: OTHER }));

    await waitFor(() => expect(screen.getByText("Step 3 of 4 — Approve")).toBeInTheDocument());
    const approve = screen.getByRole("button", { name: "Approve Run" });
    expect(approve).toBeEnabled();
    fireEvent.click(approve);

    await waitFor(() => expect(mockApproveInterestRun).toHaveBeenCalledWith("run-1"));
    await waitFor(() => expect(screen.getByText("Step 4 of 4 — Post")).toBeInTheDocument());
    expect(screen.getByText("Approved")).toBeInTheDocument();
    expect(mockRefresh).toHaveBeenCalled();
  });

  it("the run's creator sees Approve Run disabled with the awaiting-approval caption", async () => {
    const run = makeRun({ allocationCount: 1 });
    resumeWith(run, [ALLOCATION]);
    render(wizard({ run, memberId: CREATOR }));

    await waitFor(() => expect(screen.getByText("Step 3 of 4 — Approve")).toBeInTheDocument());
    const approve = screen.getByRole("button", { name: "Approve Run" });
    expect(approve).toBeDisabled();
    expect(screen.getByTestId("interest-run-awaiting-approval")).toHaveTextContent(
      "Awaiting approval by another member"
    );
    fireEvent.click(approve);
    expect(mockApproveInterestRun).not.toHaveBeenCalled();
  });

  it("an approved run resumes at step 4; Post is gated on MANAGE_TRUST", async () => {
    const run = makeRun({ status: "APPROVED", approvedBy: OTHER, allocationCount: 1 });
    resumeWith(run, [ALLOCATION]);
    const { unmount } = render(wizard({ run, memberId: CREATOR, canManage: true }));

    await waitFor(() => expect(screen.getByText("Step 4 of 4 — Post")).toBeInTheDocument());
    expect(screen.getByRole("button", { name: "Post to Ledger" })).toBeEnabled();
    unmount();

    render(wizard({ run, memberId: OTHER, canApprove: true, canManage: false }));
    await waitFor(() => expect(screen.getByText("Step 4 of 4 — Post")).toBeInTheDocument());
    expect(screen.queryByRole("button", { name: "Post to Ledger" })).toBeNull();
  });

  it("when the viewer is unknown the backend's self-approval refusal is shown verbatim", async () => {
    const run = makeRun({ allocationCount: 1 });
    resumeWith(run, [ALLOCATION]);
    mockApproveInterestRun.mockResolvedValue({
      success: false,
      error: "The interest run creator cannot approve their own interest run",
    });
    render(wizard({ run, memberId: undefined }));

    await waitFor(() => expect(screen.getByText("Step 3 of 4 — Approve")).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "Approve Run" }));

    expect(
      await screen.findByText("The interest run creator cannot approve their own interest run")
    ).toBeInTheDocument();
    expect(screen.getByText("Step 3 of 4 — Approve")).toBeInTheDocument();
  });

  it("a new run's creator is offered Review (not Approve) after calculating", async () => {
    const created = makeRun({ id: "run-new", createdBy: "me" });
    mockCreateInterestRun.mockResolvedValue({ success: true, run: created });
    mockCalculateInterest.mockResolvedValue({
      success: true,
      run: { ...created, allocationCount: 1 },
    });
    mockFetchInterestRunDetail.mockResolvedValue({
      run: { ...created, allocationCount: 1 },
      allocations: [{ ...ALLOCATION, interestRunId: "run-new" }],
    });
    render(wizard({ memberId: "me" }));

    fireEvent.change(screen.getByLabelText("Period Start"), { target: { value: "2026-01-01" } });
    fireEvent.change(screen.getByLabelText("Period End"), { target: { value: "2026-01-31" } });
    fireEvent.click(screen.getByRole("button", { name: "Create Run" }));
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Calculate Interest" })).toBeInTheDocument()
    );
    fireEvent.click(screen.getByRole("button", { name: "Calculate Interest" }));
    await waitFor(() => expect(screen.getByTestId("allocations-table")).toBeInTheDocument());

    expect(screen.getByRole("button", { name: "Review" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Approve" })).toBeNull();
    expect(mockRefresh).toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "Review" }));
    await waitFor(() => expect(screen.getByText("Step 3 of 4 — Approve")).toBeInTheDocument());
    expect(screen.getByRole("button", { name: "Approve Run" })).toBeDisabled();
  });

  it("closing and re-opening reloads the run from the server", async () => {
    const run = makeRun({ allocationCount: 1 });
    resumeWith(run, [ALLOCATION]);
    const { rerender } = render(wizard({ run, memberId: OTHER }));
    await waitFor(() => expect(mockFetchInterestRunDetail).toHaveBeenCalledTimes(1));

    rerender(wizard({ run, memberId: OTHER, open: false }));
    rerender(wizard({ run, memberId: OTHER, open: true }));

    await waitFor(() => expect(mockFetchInterestRunDetail).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(screen.getByText("Step 3 of 4 — Approve")).toBeInTheDocument());
  });
});
