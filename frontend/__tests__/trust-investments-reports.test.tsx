import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  cleanup,
  render,
  screen,
  waitFor,
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

// -- Mock report actions --------------------------------------------------
vi.mock(
  "@/app/(app)/org/[slug]/reports/[reportSlug]/actions",
  () => ({
    executeReportAction: vi.fn(),
    exportReportCsvAction: vi.fn(),
    exportReportPdfAction: vi.fn(),
    fetchEntityOptionsAction: vi.fn(),
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
import TrustReportsPage from "@/app/(app)/org/[slug]/trust-accounting/reports/page";
import { PlaceInvestmentDialog } from "@/components/trust/PlaceInvestmentDialog";

// -- Helpers --------------------------------------------------------------

function makeInvestment(overrides: Record<string, unknown> = {}) {
  return {
    id: "inv-1",
    trustAccountId: "acc-1",
    customerId: "cust-1111-2222-3333-444444444444",
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
    createdAt: "2026-01-15T09:00:00Z",
    updatedAt: "2026-03-31T09:00:00Z",
    ...overrides,
  };
}

async function renderInvestmentsPage() {
  const result = await InvestmentsPage({
    params: Promise.resolve({ slug: "acme" }),
  });
  render(result);
}

async function renderReportsPage() {
  const result = await TrustReportsPage({
    params: Promise.resolve({ slug: "acme" }),
  });
  render(result);
}

// -- Tests ----------------------------------------------------------------

describe("Trust Investments & Reports", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders investment table with mock data", async () => {
    mockFetchInvestments.mockResolvedValue([
      makeInvestment({
        id: "inv-1",
        institution: "FNB Money Market",
        status: "ACTIVE",
      }),
      makeInvestment({
        id: "inv-2",
        institution: "Nedbank Call Account",
        principal: 250000,
        interestRate: 0.065,
        status: "MATURED",
      }),
    ]);

    await renderInvestmentsPage();

    expect(screen.getByTestId("investments-table")).toBeInTheDocument();
    expect(screen.getByText("FNB Money Market")).toBeInTheDocument();
    expect(screen.getByText("Nedbank Call Account")).toBeInTheDocument();
    expect(screen.getByText("2 investments found")).toBeInTheDocument();
  });

  it("place investment dialog submits with valid data", async () => {
    mockPlaceInvestment.mockResolvedValue({
      success: true,
      investment: makeInvestment(),
    });

    render(
      <PlaceInvestmentDialog
        accountId="acc-1"
        open={true}
        onOpenChange={vi.fn()}
        onSuccess={vi.fn()}
      />,
    );

    const clientInput = screen.getByPlaceholderText("Client UUID");
    fireEvent.change(clientInput, {
      target: { value: "a1b2c3d4-e5f6-7890-abcd-ef1234567890" },
    });

    const institutionInput = screen.getByPlaceholderText(
      "e.g. FNB Money Market",
    );
    fireEvent.change(institutionInput, {
      target: { value: "ABSA Fixed Deposit" },
    });

    const accountInput = screen.getByPlaceholderText(
      "Investment account number",
    );
    fireEvent.change(accountInput, { target: { value: "INV-999" } });

    const principalInput = screen.getByLabelText("Principal");
    fireEvent.change(principalInput, { target: { value: "100000" } });

    const rateInput = screen.getByLabelText("Interest Rate %");
    fireEvent.change(rateInput, { target: { value: "8.5" } });

    const form = clientInput.closest("form")!;
    fireEvent.submit(form);

    await waitFor(() => {
      expect(mockPlaceInvestment).toHaveBeenCalledWith(
        "acc-1",
        expect.objectContaining({
          customerId: "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
          institution: "ABSA Fixed Deposit",
          accountNumber: "INV-999",
          principal: 100000,
          interestRate: 8.5,
        }),
      );
    });
  });

  it("maturity alert highlights investments maturing within 30 days", async () => {
    // Set maturity date to within 30 days from now
    const soon = new Date();
    soon.setDate(soon.getDate() + 15);
    const soonStr = soon.toISOString().slice(0, 10);

    // Set maturity date far in the future (not maturing)
    const later = new Date();
    later.setDate(later.getDate() + 90);
    const laterStr = later.toISOString().slice(0, 10);

    mockFetchInvestments.mockResolvedValue([
      makeInvestment({
        id: "inv-maturing",
        institution: "Maturing Soon Bank",
        maturityDate: soonStr,
        status: "ACTIVE",
      }),
      makeInvestment({
        id: "inv-safe",
        institution: "Far Away Bank",
        maturityDate: laterStr,
        status: "ACTIVE",
      }),
    ]);

    await renderInvestmentsPage();

    // The maturing investment should have the maturity-alert testid
    const maturityAlerts = screen.getAllByTestId("maturity-alert");
    expect(maturityAlerts).toHaveLength(1);

    // The maturing row should have amber highlight class
    const alertRow = maturityAlerts[0];
    expect(alertRow.className).toContain("bg-amber-50");
  });

  it("reports page lists all 7 report types", async () => {
    await renderReportsPage();

    expect(screen.getByTestId("trust-reports-page")).toBeInTheDocument();

    // Verify all 7 report cards
    const expectedSlugs = [
      "TRUST_RECEIPTS_PAYMENTS",
      "CLIENT_TRUST_BALANCES",
      "CLIENT_LEDGER_STATEMENT",
      "INVESTMENT_REGISTER",
      "INTEREST_ALLOCATION",
      "TRUST_RECONCILIATION",
      "SECTION_35_DATA_PACK",
    ];

    for (const slug of expectedSlugs) {
      expect(screen.getByTestId(`report-card-${slug}`)).toBeInTheDocument();
    }

    // Verify report names are visible
    expect(
      screen.getByText("Receipts & Payments Journal"),
    ).toBeInTheDocument();
    expect(screen.getByText("Client Trust Balances")).toBeInTheDocument();
    expect(screen.getByText("Client Ledger Statement")).toBeInTheDocument();
    expect(screen.getByText("Investment Register")).toBeInTheDocument();
    expect(screen.getByText("Interest Allocation")).toBeInTheDocument();
    expect(screen.getByText("Trust Reconciliation")).toBeInTheDocument();
    expect(screen.getByText("Section 35 Data Pack")).toBeInTheDocument();
  });
});
