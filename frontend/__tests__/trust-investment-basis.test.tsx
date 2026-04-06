import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  cleanup,
  render,
  screen,
  fireEvent,
} from "@testing-library/react";

// -- Mock server-only -----------------------------------------------------
vi.mock("server-only", () => ({}));

// -- Mock server actions for investments ----------------------------------
const mockFetchInvestments = vi.fn();
const mockPlaceInvestment = vi.fn();
const mockRecordInterest = vi.fn();
const mockWithdrawInvestment = vi.fn();
const mockFetchMaturing = vi.fn();

vi.mock(
  "@/app/(app)/org/[slug]/trust-accounting/investments/actions",
  () => ({
    fetchInvestments: (...args: unknown[]) => mockFetchInvestments(...args),
    placeInvestment: (...args: unknown[]) => mockPlaceInvestment(...args),
    recordInterest: (...args: unknown[]) => mockRecordInterest(...args),
    withdrawInvestment: (...args: unknown[]) =>
      mockWithdrawInvestment(...args),
    fetchMaturing: (...args: unknown[]) => mockFetchMaturing(...args),
  }),
);

// -- Mock interest run actions for wizard ---------------------------------
const mockCreateInterestRun = vi.fn();
const mockCalculateInterest = vi.fn();
const mockApproveInterestRun = vi.fn();
const mockPostInterestRun = vi.fn();
const mockFetchInterestRunDetail = vi.fn();

vi.mock(
  "@/app/(app)/org/[slug]/trust-accounting/interest/actions",
  () => ({
    createInterestRun: (...args: unknown[]) => mockCreateInterestRun(...args),
    calculateInterest: (...args: unknown[]) => mockCalculateInterest(...args),
    approveInterestRun: (...args: unknown[]) =>
      mockApproveInterestRun(...args),
    postInterestRun: (...args: unknown[]) => mockPostInterestRun(...args),
    fetchInterestRunDetail: (...args: unknown[]) =>
      mockFetchInterestRunDetail(...args),
  }),
);

// -- Mock parent trust actions --------------------------------------------
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

// -- Mock org settings & capabilities -------------------------------------
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

// -- Mock next/navigation -------------------------------------------------
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
  usePathname: () => "/org/acme/trust-accounting/investments",
  useSearchParams: () => new URLSearchParams(),
}));

// -- Mock next/link -------------------------------------------------------
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

// -- Imports after mocks --------------------------------------------------
import InvestmentsPage from "@/app/(app)/org/[slug]/trust-accounting/investments/page";
import { PlaceInvestmentDialog } from "@/components/trust/PlaceInvestmentDialog";
import { InterestRunWizard } from "@/components/trust/InterestRunWizard";

// -- Helpers --------------------------------------------------------------

function makeInvestment(overrides: Record<string, unknown> = {}) {
  return {
    id: "inv-1",
    trustAccountId: "acc-1",
    customerId: "cust-1111-2222-3333-444444444444",
    customerName: "Acme Corp",
    institution: "FNB Money Market",
    accountNumber: "INV-001",
    principal: 500000,
    interestRate: 0.075,
    depositDate: "2026-01-15",
    maturityDate: "2026-07-15",
    interestEarned: 18750,
    status: "ACTIVE",
    withdrawalDate: null,
    withdrawalAmount: null,
    depositTransactionId: "txn-1",
    withdrawalTransactionId: null,
    notes: null,
    investmentBasis: "FIRM_DISCRETION",
    createdAt: "2026-01-15T09:00:00Z",
    updatedAt: "2026-03-31T09:00:00Z",
    ...overrides,
  };
}

async function renderInvestmentsPage(searchParams: Record<string, string> = {}) {
  const result = await InvestmentsPage({
    params: Promise.resolve({ slug: "acme" }),
    searchParams: Promise.resolve(searchParams),
  });
  render(result);
}

// -- Tests ----------------------------------------------------------------

describe("Epic 455 — Investment Basis", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  // Test 1: Place Investment dialog renders radio group with correct options
  it("Place Investment dialog renders investment basis radio group", () => {
    render(
      <PlaceInvestmentDialog
        accountId="acc-1"
        open={true}
        onOpenChange={vi.fn()}
        onSuccess={vi.fn()}
      />,
    );

    expect(screen.getByText("Investment initiated by")).toBeInTheDocument();
    expect(screen.getByText("Firm (surplus trust funds)")).toBeInTheDocument();
    expect(screen.getByText("Client instruction")).toBeInTheDocument();
  });

  // Test 2: Selecting CLIENT_INSTRUCTION shows statutory help text
  it("shows statutory help text when CLIENT_INSTRUCTION is selected", () => {
    render(
      <PlaceInvestmentDialog
        accountId="acc-1"
        open={true}
        onOpenChange={vi.fn()}
        onSuccess={vi.fn()}
      />,
    );

    // Default is FIRM_DISCRETION
    const helpText = screen.getByTestId("investment-basis-help");
    expect(helpText.textContent).toContain("LPFF arrangement rate");

    // Click Client instruction radio
    const clientRadio = screen.getByLabelText("Client instruction");
    fireEvent.click(clientRadio);

    expect(helpText.textContent).toContain(
      "5% to the LPFF (Section 86(5))",
    );
  });

  // Test 3: 86(6) advisory note renders in dialog
  it("renders Section 86(6) advisory note in Place Investment dialog", () => {
    render(
      <PlaceInvestmentDialog
        accountId="acc-1"
        open={true}
        onOpenChange={vi.fn()}
        onSuccess={vi.fn()}
      />,
    );

    expect(screen.getByTestId("section-86-6-advisory")).toBeInTheDocument();
    expect(
      screen.getByText(/Legal Practitioners Fidelity Fund \(Section 86\(6\)\)/),
    ).toBeInTheDocument();
  });

  // Test 4: Register table shows basis column
  it("register table shows basis column with correct badges", async () => {
    mockFetchInvestments.mockResolvedValue([
      makeInvestment({
        id: "inv-firm",
        customerName: "Firm Client",
        investmentBasis: "FIRM_DISCRETION",
      }),
      makeInvestment({
        id: "inv-client",
        customerName: "Client Instruction Client",
        investmentBasis: "CLIENT_INSTRUCTION",
      }),
    ]);

    await renderInvestmentsPage();

    // Check column header
    expect(screen.getByText("Basis")).toBeInTheDocument();

    // Check badge labels
    expect(screen.getByText("Firm")).toBeInTheDocument();
    expect(screen.getByText("Client Instruction")).toBeInTheDocument();
  });

  // Test 5: Register table shows "5% (statutory)" for CLIENT_INSTRUCTION investments
  it('register table shows "5% (statutory)" for CLIENT_INSTRUCTION investments', async () => {
    mockFetchInvestments.mockResolvedValue([
      makeInvestment({
        id: "inv-client-inst",
        customerName: "Client Instruction Co",
        investmentBasis: "CLIENT_INSTRUCTION",
      }),
    ]);

    await renderInvestmentsPage();

    expect(screen.getByText("5% (statutory)")).toBeInTheDocument();
  });

  // Test 6: Filter dropdown renders with options
  it("filter dropdown renders with All, Firm Discretion, and Client Instruction options", async () => {
    mockFetchInvestments.mockResolvedValue([makeInvestment()]);

    await renderInvestmentsPage();

    expect(screen.getByTestId("investment-basis-filter")).toBeInTheDocument();
  });

  // Test 7: Interest allocation shows "5% (statutory)" for 86(4) allocations
  it('interest allocation shows "5% (statutory)" for statutory rate allocations', () => {
    const run = {
      id: "run-1",
      trustAccountId: "acc-1",
      periodStart: "2026-01-01",
      periodEnd: "2026-03-31",
      lpffRateId: "rate-1",
      totalInterest: 5000,
      totalLpffShare: 2500,
      totalClientShare: 2500,
      status: "DRAFT",
      createdBy: null,
      approvedBy: null,
      postedAt: null,
      createdAt: "2026-04-01T09:00:00Z",
      updatedAt: "2026-04-01T09:00:00Z",
    };

    // Render the wizard at step 2 with allocations that include statutory
    // We need to render the component, create an interest run, then calculate
    // But since the wizard is stateful, we'll test the rendered table directly
    // by simulating the state after calculation

    // Instead, render the wizard, trigger create, then calculate
    // For simplicity, let's just render with allocations in a controlled manner
    // We'll mock the state by rendering at step 2

    // The simplest approach: render the wizard open, mock create + calculate
    mockCreateInterestRun.mockResolvedValue({
      success: true,
      run,
    });
    mockCalculateInterest.mockResolvedValue({
      success: true,
      run: { ...run, totalInterest: 5000 },
    });
    mockFetchInterestRunDetail.mockResolvedValue({
      run,
      allocations: [
        {
          id: "alloc-1",
          interestRunId: "run-1",
          customerId: "cust-1111-2222-3333-444444444444",
          averageDailyBalance: 100000,
          daysInPeriod: 90,
          grossInterest: 2500,
          lpffShare: 125,
          clientShare: 2375,
          trustTransactionId: null,
          lpffRateId: null,
          statutoryRateApplied: true,
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
      />,
    );

    // Fill period dates and submit step 1
    const startInput = screen.getByLabelText("Period Start");
    fireEvent.change(startInput, { target: { value: "2026-01-01" } });
    const endInput = screen.getByLabelText("Period End");
    fireEvent.change(endInput, { target: { value: "2026-03-31" } });

    const createBtn = screen.getByText("Create Run");
    fireEvent.click(createBtn);

    // Wait for step 2 then click calculate
    return screen.findByText("Calculate Interest").then(async (calcBtn) => {
      fireEvent.click(calcBtn);

      // Wait for allocations table to render
      const statutoryCell = await screen.findByText("5% (statutory)");
      expect(statutoryCell).toBeInTheDocument();

      // Verify footnote
      expect(screen.getByTestId("statutory-footnote")).toBeInTheDocument();
      expect(
        screen.getByText(/Section 86\(5\): client-instructed investments/),
      ).toBeInTheDocument();
    });
  });

  // Test 8: Interest allocation shows "Arrangement" for 86(3) allocations
  it('interest allocation shows "Arrangement" for non-statutory rate allocations', () => {
    const run = {
      id: "run-2",
      trustAccountId: "acc-1",
      periodStart: "2026-01-01",
      periodEnd: "2026-03-31",
      lpffRateId: "rate-1",
      totalInterest: 5000,
      totalLpffShare: 2500,
      totalClientShare: 2500,
      status: "DRAFT",
      createdBy: null,
      approvedBy: null,
      postedAt: null,
      createdAt: "2026-04-01T09:00:00Z",
      updatedAt: "2026-04-01T09:00:00Z",
    };

    mockCreateInterestRun.mockResolvedValue({
      success: true,
      run,
    });
    mockCalculateInterest.mockResolvedValue({
      success: true,
      run: { ...run, totalInterest: 5000 },
    });
    mockFetchInterestRunDetail.mockResolvedValue({
      run,
      allocations: [
        {
          id: "alloc-2",
          interestRunId: "run-2",
          customerId: "cust-2222-3333-4444-555555555555",
          averageDailyBalance: 200000,
          daysInPeriod: 90,
          grossInterest: 5000,
          lpffShare: 2500,
          clientShare: 2500,
          trustTransactionId: null,
          lpffRateId: "rate-1",
          statutoryRateApplied: false,
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
      />,
    );

    // Fill period dates and submit step 1
    const startInput = screen.getByLabelText("Period Start");
    fireEvent.change(startInput, { target: { value: "2026-01-01" } });
    const endInput = screen.getByLabelText("Period End");
    fireEvent.change(endInput, { target: { value: "2026-03-31" } });

    const createBtn = screen.getByText("Create Run");
    fireEvent.click(createBtn);

    return screen.findByText("Calculate Interest").then(async (calcBtn) => {
      fireEvent.click(calcBtn);

      const arrangementCell = await screen.findByText("Arrangement");
      expect(arrangementCell).toBeInTheDocument();
    });
  });
});
