import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";


// Must mock server-only before importing components that use it
vi.mock("server-only", () => ({}));

// Mock org settings API
const mockGetOrgSettings = vi.fn();
vi.mock("@/lib/api/settings", () => ({
  getOrgSettings: (...args: unknown[]) => mockGetOrgSettings(...args),
}));

// Mock capabilities API
const mockFetchMyCapabilities = vi.fn();
vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: (...args: unknown[]) =>
    mockFetchMyCapabilities(...args),
}));

// Mock the trust actions
const mockFetchTrustAccounts = vi.fn();
vi.mock("@/app/(app)/org/[slug]/trust-accounting/actions", () => ({
  fetchTrustAccounts: (...args: unknown[]) =>
    mockFetchTrustAccounts(...args),
}));

// Mock the interest actions
const mockFetchInterestRuns = vi.fn();
const mockFetchLpffRates = vi.fn();
const mockCreateInterestRun = vi.fn();
const mockCalculateInterest = vi.fn();
const mockApproveInterestRun = vi.fn();
const mockPostInterestRun = vi.fn();
const mockFetchInterestRunDetail = vi.fn();
const mockAddLpffRate = vi.fn();

vi.mock(
  "@/app/(app)/org/[slug]/trust-accounting/interest/actions",
  () => ({
    fetchInterestRuns: (...args: unknown[]) =>
      mockFetchInterestRuns(...args),
    fetchLpffRates: (...args: unknown[]) => mockFetchLpffRates(...args),
    createInterestRun: (...args: unknown[]) =>
      mockCreateInterestRun(...args),
    calculateInterest: (...args: unknown[]) =>
      mockCalculateInterest(...args),
    approveInterestRun: (...args: unknown[]) =>
      mockApproveInterestRun(...args),
    postInterestRun: (...args: unknown[]) => mockPostInterestRun(...args),
    fetchInterestRunDetail: (...args: unknown[]) =>
      mockFetchInterestRunDetail(...args),
    addLpffRate: (...args: unknown[]) => mockAddLpffRate(...args),
  }),
);

// Mock next/navigation
const mockNotFound = vi.fn();
vi.mock("next/navigation", () => ({
  notFound: () => {
    mockNotFound();
    throw new Error("NEXT_NOT_FOUND");
  },
}));

// Mock next/cache
vi.mock("next/cache", () => ({
  revalidatePath: vi.fn(),
}));

// Import page after mocks
import InterestPage from "@/app/(app)/org/[slug]/trust-accounting/interest/page";

// ── Helpers ────────────────────────────────────────────────────────

async function renderPage() {
  const result = await InterestPage({
    params: Promise.resolve({ slug: "acme" }),
  });
  render(result);
}

function setupDefaultMocks() {
  mockGetOrgSettings.mockResolvedValue({
    enabledModules: ["trust_accounting"],
    defaultCurrency: "ZAR",
  });
  mockFetchMyCapabilities.mockResolvedValue({
    isAdmin: true,
    isOwner: false,
    capabilities: ["VIEW_TRUST", "MANAGE_TRUST"],
  });
  mockFetchTrustAccounts.mockResolvedValue([
    {
      id: "acc-1",
      accountName: "Main Trust Account",
      bankName: "FNB",
      branchCode: "250655",
      accountNumber: "1234567890",
      accountType: "GENERAL",
      isPrimary: true,
      requireDualApproval: true,
      paymentApprovalThreshold: 50000,
      status: "ACTIVE",
      openedDate: "2025-01-01",
      closedDate: null,
      notes: null,
      createdAt: "2025-01-01T00:00:00Z",
      updatedAt: "2025-01-01T00:00:00Z",
    },
  ]);
  mockFetchInterestRuns.mockResolvedValue([
    {
      id: "run-1",
      trustAccountId: "acc-1",
      periodStart: "2026-01-01",
      periodEnd: "2026-03-31",
      lpffRateId: "rate-1",
      totalInterest: 15000,
      totalLpffShare: 11250,
      totalClientShare: 3750,
      status: "POSTED",
      createdBy: "member-1",
      approvedBy: "member-2",
      postedAt: "2026-04-01T10:00:00Z",
      createdAt: "2026-03-31T10:00:00Z",
      updatedAt: "2026-04-01T10:00:00Z",
    },
    {
      id: "run-2",
      trustAccountId: "acc-1",
      periodStart: "2026-04-01",
      periodEnd: "2026-06-30",
      lpffRateId: "rate-1",
      totalInterest: 0,
      totalLpffShare: 0,
      totalClientShare: 0,
      status: "DRAFT",
      createdBy: "member-1",
      approvedBy: null,
      postedAt: null,
      createdAt: "2026-06-30T10:00:00Z",
      updatedAt: "2026-06-30T10:00:00Z",
    },
  ]);
  mockFetchLpffRates.mockResolvedValue([
    {
      id: "rate-1",
      trustAccountId: "acc-1",
      effectiveFrom: "2026-01-01",
      ratePercent: 0.075,
      lpffSharePercent: 0.75,
      notes: "Standard LPFF rate",
      createdAt: "2026-01-01T00:00:00Z",
    },
  ]);
}

// ── Tests ──────────────────────────────────────────────────────────

describe("InterestPage", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders interest runs table with correct data", async () => {
    setupDefaultMocks();
    await renderPage();

    expect(screen.getByText("Interest Runs")).toBeInTheDocument();
    expect(screen.getByTestId("interest-runs-table")).toBeInTheDocument();

    // Check statuses
    expect(screen.getByText("POSTED")).toBeInTheDocument();
    expect(screen.getByText("DRAFT")).toBeInTheDocument();
  });

  it("renders LPFF rate history table", async () => {
    setupDefaultMocks();
    await renderPage();

    expect(screen.getByText("LPFF Rate History")).toBeInTheDocument();
    expect(screen.getByTestId("lpff-rates-table")).toBeInTheDocument();

    // Rate data
    expect(screen.getByText("7.50%")).toBeInTheDocument();
    expect(screen.getByText("75.00%")).toBeInTheDocument();
    expect(screen.getByText("Standard LPFF rate")).toBeInTheDocument();
  });

  it("calls notFound when trust_accounting module is disabled", async () => {
    mockGetOrgSettings.mockResolvedValue({
      enabledModules: [],
    });

    await expect(renderPage()).rejects.toThrow("NEXT_NOT_FOUND");
    expect(mockNotFound).toHaveBeenCalled();
  });

  it("shows action buttons when user has MANAGE_TRUST capability", async () => {
    setupDefaultMocks();
    await renderPage();

    expect(screen.getByText("New Interest Run")).toBeInTheDocument();
    expect(screen.getByText("Add Rate")).toBeInTheDocument();
  });
});
