import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, within, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// 630B (G88-F39, §14.12 #2): the interest runs table's Actions column, status label map and the
// Delete draft confirmation. The backend stays the authority for every transition.

vi.mock("server-only", () => ({}));

const mockFetchInterestRuns = vi.fn();
const mockCreateInterestRun = vi.fn();
const mockCalculateInterest = vi.fn();
const mockApproveInterestRun = vi.fn();
const mockPostInterestRun = vi.fn();
const mockDeleteInterestRun = vi.fn();
const mockFetchInterestRunDetail = vi.fn();
const mockFetchLpffRates = vi.fn();
const mockAddLpffRate = vi.fn();

vi.mock("@/app/(app)/org/[slug]/trust-accounting/interest/actions", () => ({
  fetchInterestRuns: (...args: unknown[]) => mockFetchInterestRuns(...args),
  createInterestRun: (...args: unknown[]) => mockCreateInterestRun(...args),
  calculateInterest: (...args: unknown[]) => mockCalculateInterest(...args),
  approveInterestRun: (...args: unknown[]) => mockApproveInterestRun(...args),
  postInterestRun: (...args: unknown[]) => mockPostInterestRun(...args),
  deleteInterestRun: (...args: unknown[]) => mockDeleteInterestRun(...args),
  fetchInterestRunDetail: (...args: unknown[]) => mockFetchInterestRunDetail(...args),
  fetchLpffRates: (...args: unknown[]) => mockFetchLpffRates(...args),
  addLpffRate: (...args: unknown[]) => mockAddLpffRate(...args),
}));

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

const mockRefresh = vi.fn();
vi.mock("next/navigation", () => ({
  notFound: () => {
    throw new Error("NEXT_NOT_FOUND");
  },
  useRouter: () => ({
    push: vi.fn(),
    refresh: mockRefresh,
    replace: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

import InterestPage from "@/app/(app)/org/[slug]/trust-accounting/interest/page";

const CREATOR = "member-creator";
const OTHER = "member-other";
const ALL = ["VIEW_TRUST", "MANAGE_TRUST", "APPROVE_TRUST_PAYMENT"];

function caps(capabilities: string[], memberId: string) {
  return { capabilities, role: "member", isAdmin: false, isOwner: false, memberId };
}

function makeRun(overrides: Record<string, unknown> = {}) {
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

const D0 = makeRun({ id: "d0", periodStart: "2026-01-01", periodEnd: "2026-01-31" });
const D2 = makeRun({
  id: "d2",
  periodStart: "2026-02-01",
  periodEnd: "2026-02-28",
  allocationCount: 2,
});
const AP = makeRun({
  id: "ap",
  periodStart: "2026-03-01",
  periodEnd: "2026-03-31",
  status: "APPROVED",
  approvedBy: OTHER,
  allocationCount: 1,
});
const PO = makeRun({
  id: "po",
  periodStart: "2026-04-01",
  periodEnd: "2026-04-30",
  status: "POSTED",
  approvedBy: OTHER,
  postedAt: "2026-05-02T10:00:00Z",
  allocationCount: 1,
});

async function renderPage() {
  const result = await InterestPage({ params: Promise.resolve({ slug: "acme" }) });
  render(result);
}

const row = (id: string) => screen.getByTestId(`run-row-${id}`);
const buttonNames = (id: string) =>
  within(row(id))
    .queryAllByRole("button")
    .map((b) => b.textContent?.trim());

describe("Interest runs table — Actions column (630B)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchLpffRates.mockResolvedValue([]);
    mockFetchInterestRuns.mockResolvedValue([D0, D2, AP, PO]);
    mockFetchInterestRunDetail.mockResolvedValue({ run: D2, allocations: [] });
  });

  afterEach(() => {
    cleanup();
  });

  it("an approver who did not create the run gets the lifecycle actions per status", async () => {
    mockFetchMyCapabilities.mockResolvedValue(caps(ALL, OTHER));
    await renderPage();

    expect(buttonNames("d0")).toEqual(["Continue", "Delete draft"]);
    expect(buttonNames("d2")).toEqual(["Review & approve", "Delete draft"]);
    expect(within(row("d2")).queryByText("Awaiting approval by another member")).toBeNull();
    expect(buttonNames("ap")).toEqual(["Post"]);
    expect(buttonNames("po")).toEqual([]);
  });

  it("the creator sees Review with the awaiting-approval caption, never Review & approve", async () => {
    mockFetchMyCapabilities.mockResolvedValue(caps(ALL, CREATOR));
    await renderPage();

    expect(buttonNames("d2")).toEqual(["Review", "Delete draft"]);
    expect(within(row("d2")).getByTestId("awaiting-approval-d2")).toHaveTextContent(
      "Awaiting approval by another member"
    );
    // a never-calculated draft has nothing to approve yet — no caption
    expect(within(row("d0")).queryByText("Awaiting approval by another member")).toBeNull();
  });

  it("VIEW_TRUST only: Review on a calculated draft, no Continue / Post / Delete draft", async () => {
    mockFetchMyCapabilities.mockResolvedValue(caps(["VIEW_TRUST"], OTHER));
    await renderPage();

    expect(buttonNames("d0")).toEqual([]);
    expect(buttonNames("d2")).toEqual(["Review"]);
    expect(within(row("d2")).queryByText("Awaiting approval by another member")).toBeNull();
    expect(buttonNames("ap")).toEqual([]);
    expect(screen.queryByRole("button", { name: "Delete draft" })).toBeNull();
    expect(screen.queryByRole("button", { name: "New Interest Run" })).toBeNull();
  });

  it("APPROVE_TRUST_PAYMENT without MANAGE_TRUST: Review & approve, no Post or Delete draft", async () => {
    mockFetchMyCapabilities.mockResolvedValue(caps(["VIEW_TRUST", "APPROVE_TRUST_PAYMENT"], OTHER));
    await renderPage();

    expect(buttonNames("d2")).toEqual(["Review & approve"]);
    expect(buttonNames("ap")).toEqual([]);
    expect(screen.queryByRole("button", { name: "Delete draft" })).toBeNull();
  });

  it("the status cell renders the label map, never the raw enum", async () => {
    mockFetchMyCapabilities.mockResolvedValue(caps(ALL, OTHER));
    await renderPage();

    expect(within(row("d0")).getByTestId("run-status")).toHaveTextContent(/^Draft$/);
    expect(within(row("ap")).getByTestId("run-status")).toHaveTextContent(/^Approved$/);
    expect(within(row("po")).getByTestId("run-status")).toHaveTextContent(/^Posted$/);
    expect(screen.queryByText("DRAFT")).toBeNull();
    expect(screen.queryByText("APPROVED")).toBeNull();
    expect(screen.queryByText("POSTED")).toBeNull();
    expect(screen.getByRole("columnheader", { name: "Actions" })).toBeInTheDocument();
  });

  it("Delete draft → confirm deletes the run, refreshes, and the row is gone", async () => {
    const user = userEvent.setup();
    mockFetchMyCapabilities.mockResolvedValue(caps(ALL, CREATOR));
    mockDeleteInterestRun.mockResolvedValue({ success: true });
    await renderPage();

    await user.click(within(row("d0")).getByRole("button", { name: "Delete draft" }));
    const dialog = await screen.findByRole("alertdialog");
    expect(within(dialog).getByText("Delete draft interest run?")).toBeInTheDocument();
    expect(dialog).toHaveTextContent(
      "Its calculated allocations are discarded. Nothing has been posted."
    );
    await user.click(within(dialog).getByRole("button", { name: "Delete" }));

    await waitFor(() => expect(mockDeleteInterestRun).toHaveBeenCalledWith("d0"));
    await waitFor(() => expect(mockRefresh).toHaveBeenCalled());

    // The refreshed server render no longer lists the deleted draft
    cleanup();
    mockFetchInterestRuns.mockResolvedValue([D2, AP, PO]);
    await renderPage();
    expect(screen.queryByTestId("run-row-d0")).toBeNull();
    expect(screen.getByTestId("run-row-d2")).toBeInTheDocument();
  });

  it("Delete draft → cancel leaves the run untouched", async () => {
    const user = userEvent.setup();
    mockFetchMyCapabilities.mockResolvedValue(caps(ALL, OTHER));
    await renderPage();

    await user.click(within(row("d2")).getByRole("button", { name: "Delete draft" }));
    const dialog = await screen.findByRole("alertdialog");
    await user.click(within(dialog).getByRole("button", { name: "Cancel" }));

    await waitFor(() => expect(screen.queryByRole("alertdialog")).toBeNull());
    expect(mockDeleteInterestRun).not.toHaveBeenCalled();
    expect(mockRefresh).not.toHaveBeenCalled();
    expect(row("d2")).toBeInTheDocument();
  });

  it("a refused delete surfaces the backend detail verbatim", async () => {
    const user = userEvent.setup();
    mockFetchMyCapabilities.mockResolvedValue(caps(ALL, OTHER));
    mockDeleteInterestRun.mockResolvedValue({
      success: false,
      error: "Only DRAFT interest runs can be deleted",
    });
    await renderPage();

    await user.click(within(row("d0")).getByRole("button", { name: "Delete draft" }));
    const dialog = await screen.findByRole("alertdialog");
    await user.click(within(dialog).getByRole("button", { name: "Delete" }));

    expect(
      await within(dialog).findByText("Only DRAFT interest runs can be deleted")
    ).toBeInTheDocument();
    expect(mockRefresh).not.toHaveBeenCalled();
  });

  it("Review & approve opens the wizard on the persisted run", async () => {
    const user = userEvent.setup();
    mockFetchMyCapabilities.mockResolvedValue(caps(ALL, OTHER));
    mockFetchInterestRunDetail.mockResolvedValue({
      run: D2,
      allocations: [
        {
          id: "alloc-1",
          interestRunId: "d2",
          clientId: "cust-1",
          averageDailyBalance: 1000,
          daysInPeriod: 28,
          grossInterest: 50,
          lpffShare: 25,
          clientShare: 25,
          trustTransactionId: null,
          lpffRateId: "rate-1",
          statutoryRateApplied: false,
          createdAt: "2026-03-01T09:00:00Z",
        },
      ],
    });
    await renderPage();

    await user.click(within(row("d2")).getByRole("button", { name: "Review & approve" }));

    await waitFor(() => expect(mockFetchInterestRunDetail).toHaveBeenCalledWith("d2"));
    const wizard = await screen.findByTestId("interest-run-wizard");
    await waitFor(() => expect(wizard).toHaveTextContent("Step 3 of 4"));
    expect(within(wizard).getByRole("button", { name: "Approve Run" })).toBeEnabled();
  });
});
