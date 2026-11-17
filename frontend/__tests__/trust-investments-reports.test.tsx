import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, fireEvent, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// -- Mock server-only -----------------------------------------------------
vi.mock("server-only", () => ({}));

// -- Mock server actions for investments ----------------------------------
const mockFetchInvestments = vi.fn();
const mockPlaceInvestment = vi.fn();
const mockRecordInterest = vi.fn();
const mockWithdrawInvestment = vi.fn();
const mockFetchMaturing = vi.fn();

vi.mock("@/app/(app)/org/[slug]/trust-accounting/investments/actions", () => ({
  fetchInvestments: (...args: unknown[]) => mockFetchInvestments(...args),
  placeInvestment: (...args: unknown[]) => mockPlaceInvestment(...args),
  recordInterest: (...args: unknown[]) => mockRecordInterest(...args),
  withdrawInvestment: (...args: unknown[]) => mockWithdrawInvestment(...args),
  fetchMaturing: (...args: unknown[]) => mockFetchMaturing(...args),
}));

// -- Mock report definitions API ------------------------------------------
const mockGetReportDefinitions = vi.fn();

vi.mock("@/lib/api/reports", () => ({
  getReportDefinitions: (...args: unknown[]) => mockGetReportDefinitions(...args),
}));

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
import TrustReportsPage from "@/app/(app)/org/[slug]/trust-accounting/reports/page";
import { PlaceInvestmentDialog } from "@/components/trust/PlaceInvestmentDialog";
import { WithdrawInvestmentDialog } from "@/components/trust/WithdrawInvestmentDialog";
import type { InvestmentBasis, TrustAccount } from "@/lib/types/trust";

// -- Helpers --------------------------------------------------------------

const FIRM_ACCOUNT = "f1f1f1f1-0000-4000-8000-000000000001";
const CLIENT_ACCOUNT = "c4c4c4c4-0000-4000-8000-000000000004";

function makeInvestment(overrides: Record<string, unknown> = {}) {
  return {
    id: "inv-1",
    trustAccountId: "acc-1",
    investmentAccountId: CLIENT_ACCOUNT,
    investmentAccountName: "Section 86(4) Fixed Deposit",
    clientId: "cust-1111-2222-3333-444444444444",
    clientName: "Acme Corp",
    matterId: "mat-1",
    institution: "FNB Money Market",
    accountNumber: "INV-001",
    principal: 500000,
    interestRate: 0.075,
    depositDate: "2026-01-15",
    maturityDate: "2026-07-15",
    interestEarned: 0,
    balance: 500000,
    status: "ACTIVE",
    withdrawalDate: null,
    withdrawalAmount: null,
    depositTransactionId: "txn-1",
    withdrawalTransactionId: null,
    notes: null,
    investmentBasis: "CLIENT_INSTRUCTION",
    createdAt: "2026-01-15T09:00:00Z",
    updatedAt: "2026-03-31T09:00:00Z",
    ...overrides,
  };
}

function investmentAccount(id: string, name: string, basis: InvestmentBasis): TrustAccount {
  return {
    id,
    accountName: name,
    bankName: "Investec",
    branchCode: "580105",
    accountNumber: "7770001234",
    accountType: "INVESTMENT",
    investmentBasis: basis,
    section: basis === "FIRM_DISCRETION" ? "S86_3" : "S86_4",
    isPrimary: false,
    requireDualApproval: false,
    paymentApprovalThreshold: null,
    status: "ACTIVE",
    openedDate: "2025-01-01",
    closedDate: null,
    notes: null,
    createdAt: "2025-01-01T00:00:00Z",
    updatedAt: "2025-01-01T00:00:00Z",
  };
}

const INVESTMENT_ACCOUNTS = [
  investmentAccount(FIRM_ACCOUNT, "Section 86(3) Call Account", "FIRM_DISCRETION"),
  investmentAccount(CLIENT_ACCOUNT, "Section 86(4) Fixed Deposit", "CLIENT_INSTRUCTION"),
];

function renderPlaceDialog() {
  return render(
    <PlaceInvestmentDialog
      accountId="acc-1"
      investmentAccounts={INVESTMENT_ACCOUNTS}
      open={true}
      onOpenChange={vi.fn()}
      onSuccess={vi.fn()}
    />
  );
}

async function renderInvestmentsPage() {
  const result = await InvestmentsPage({
    params: Promise.resolve({ slug: "acme" }),
    searchParams: Promise.resolve({}),
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
        clientName: "Smith & Associates",
        institution: "FNB Money Market",
        status: "ACTIVE",
      }),
      makeInvestment({
        id: "inv-2",
        clientName: "Jones Trust",
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
    expect(screen.getByText("Smith & Associates")).toBeInTheDocument();
    expect(screen.getByText("Jones Trust")).toBeInTheDocument();
    expect(screen.getByText("2 investments found")).toBeInTheDocument();
  });

  // G89-F87 (D41): a rejected placement cancels its investment; the register labels it Cancelled
  // and offers no actions on it.
  it("labels a cancelled investment Cancelled with no actions", async () => {
    mockFetchInvestments.mockResolvedValue([
      makeInvestment({ id: "inv-cxl", clientName: "Cancelled Client", status: "CANCELLED" }),
    ]);

    await renderInvestmentsPage();

    const row = screen.getByText("Cancelled Client").closest("tr") as HTMLElement;
    expect(within(row).getByText("Cancelled")).toBeInTheDocument();
    expect(within(row).queryByText("CANCELLED")).toBeNull();
    expect(within(row).queryByRole("button")).toBeNull();
  });

  it("place investment dialog submits a section 86(4) placement with its account, client and matter", async () => {
    const user = userEvent.setup();
    mockPlaceInvestment.mockResolvedValue({ success: true, investment: makeInvestment() });
    renderPlaceDialog();

    await user.click(screen.getByLabelText("Section 86(4) — invested on a client's instruction"));
    await user.click(screen.getByTestId("investment-account-select"));
    await user.click(screen.getByRole("option", { name: /Section 86\(4\) Fixed Deposit/ }));

    fireEvent.change(screen.getByPlaceholderText("Client UUID"), {
      target: { value: "a1b2c3d4-e5f6-4890-abcd-ef1234567890" },
    });
    // G89-F28: the invested money's matter is required
    fireEvent.change(screen.getByPlaceholderText("Matter UUID"), {
      target: { value: "b2c3d4e5-f6a7-4890-abcd-ef1234567890" },
    });
    fireEvent.change(screen.getByPlaceholderText("e.g. FNB Money Market"), {
      target: { value: "ABSA Fixed Deposit" },
    });
    fireEvent.change(screen.getByPlaceholderText("Investment account number"), {
      target: { value: "INV-999" },
    });
    fireEvent.change(screen.getByLabelText("Principal"), { target: { value: "100000" } });
    fireEvent.change(screen.getByLabelText("Interest Rate %"), { target: { value: "8.5" } });

    fireEvent.submit(screen.getByPlaceholderText("Client UUID").closest("form")!);

    await waitFor(() => {
      expect(mockPlaceInvestment).toHaveBeenCalledWith(
        "acc-1",
        expect.objectContaining({
          investmentBasis: "CLIENT_INSTRUCTION",
          investmentAccountId: CLIENT_ACCOUNT,
          customerId: "a1b2c3d4-e5f6-4890-abcd-ef1234567890",
          projectId: "b2c3d4e5-f6a7-4890-abcd-ef1234567890",
          institution: "ABSA Fixed Deposit",
          accountNumber: "INV-999",
          principal: 100000,
          interestRate: 8.5,
        })
      );
    });
  });

  it("place investment dialog submits a section 86(3) placement with no client or matter", async () => {
    const user = userEvent.setup();
    mockPlaceInvestment.mockResolvedValue({ success: true, investment: makeInvestment() });
    renderPlaceDialog();

    await user.click(screen.getByTestId("investment-account-select"));
    await user.click(screen.getByRole("option", { name: /Section 86\(3\) Call Account/ }));
    fireEvent.change(screen.getByPlaceholderText("e.g. FNB Money Market"), {
      target: { value: "Investec Call" },
    });
    fireEvent.change(screen.getByPlaceholderText("Investment account number"), {
      target: { value: "FD-1" },
    });
    fireEvent.change(screen.getByLabelText("Principal"), { target: { value: "50000" } });
    fireEvent.change(screen.getByLabelText("Interest Rate %"), { target: { value: "7" } });

    fireEvent.submit(screen.getByPlaceholderText("Investment account number").closest("form")!);

    await waitFor(() => expect(mockPlaceInvestment).toHaveBeenCalledTimes(1));
    const [, data] = mockPlaceInvestment.mock.calls[0];
    expect(data).toEqual(
      expect.objectContaining({
        investmentBasis: "FIRM_DISCRETION",
        investmentAccountId: FIRM_ACCOUNT,
      })
    );
    expect(data.customerId ?? "").toBe("");
    expect(data.projectId ?? "").toBe("");
  });

  it("place investment dialog refuses a missing investment account", async () => {
    renderPlaceDialog();

    fireEvent.change(screen.getByPlaceholderText("e.g. FNB Money Market"), {
      target: { value: "Investec Call" },
    });
    fireEvent.change(screen.getByPlaceholderText("Investment account number"), {
      target: { value: "FD-1" },
    });
    fireEvent.change(screen.getByLabelText("Principal"), { target: { value: "50000" } });
    fireEvent.submit(screen.getByPlaceholderText("Investment account number").closest("form")!);

    expect(await screen.findByText("Choose the investment account")).toBeInTheDocument();
    expect(mockPlaceInvestment).not.toHaveBeenCalled();
  });

  // PR #1757 review: the dialog is taller than short viewports (the Day 60 e2e had to submit with
  // Enter because the button was off-screen), so its content scrolls like the repo's other tall
  // dialogs (edit-customer-dialog, create-project-dialog).
  it("place investment dialog scrolls at short viewport heights", () => {
    renderPlaceDialog();

    // DialogContent puts its className on the panel inside the positioned (test id) element
    const dialog = screen.getByTestId("place-investment-dialog");
    expect(dialog.className).toContain("sm:max-w-lg");
    const panel = dialog.firstElementChild as HTMLElement;
    expect(panel.className).toContain("max-h-[90vh]");
    expect(panel.className).toContain("overflow-y-auto");
  });

  it("place investment dialog refuses a section 86(4) placement without a matter (G89-F28)", async () => {
    const user = userEvent.setup();
    renderPlaceDialog();

    await user.click(screen.getByLabelText("Section 86(4) — invested on a client's instruction"));
    await user.click(screen.getByTestId("investment-account-select"));
    await user.click(screen.getByRole("option", { name: /Section 86\(4\) Fixed Deposit/ }));
    fireEvent.change(screen.getByPlaceholderText("Client UUID"), {
      target: { value: "a1b2c3d4-e5f6-4890-abcd-ef1234567890" },
    });
    fireEvent.change(screen.getByPlaceholderText("e.g. FNB Money Market"), {
      target: { value: "ABSA Fixed Deposit" },
    });
    fireEvent.change(screen.getByPlaceholderText("Investment account number"), {
      target: { value: "INV-999" },
    });
    fireEvent.change(screen.getByLabelText("Principal"), { target: { value: "1000" } });
    fireEvent.submit(screen.getByPlaceholderText("Client UUID").closest("form")!);

    expect(
      await screen.findByText("A matter is required for client trust transactions")
    ).toBeInTheDocument();
    expect(mockPlaceInvestment).not.toHaveBeenCalled();
  });

  // 648A: the whole investment returns; the dialog shows the amount and takes only the date
  it("withdraw dialog shows the amount that will return and takes the date — nothing typed", async () => {
    const user = userEvent.setup();
    mockWithdrawInvestment.mockResolvedValue({ success: true, investment: makeInvestment() });
    render(
      <WithdrawInvestmentDialog
        investmentId="inv-1"
        balance={201250}
        currency="ZAR"
        open={true}
        onOpenChange={vi.fn()}
        onSuccess={vi.fn()}
      />
    );

    const dialog = screen.getByTestId("withdraw-investment-dialog");
    expect(within(dialog).getByTestId("withdrawal-amount").textContent).toMatch(
      /201[\s\u00a0]250,00/
    );
    // the only input is the date
    const date = within(dialog).getByLabelText("Withdrawal date") as HTMLInputElement;
    expect(date.type).toBe("date");
    expect(date.value).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(within(dialog).queryByRole("spinbutton")).toBeNull();
    expect(within(dialog).queryAllByRole("textbox")).toHaveLength(0);

    fireEvent.change(date, { target: { value: "2026-11-01" } });
    await user.click(within(dialog).getByRole("button", { name: "Confirm Withdrawal" }));

    await waitFor(() =>
      expect(mockWithdrawInvestment).toHaveBeenCalledWith("inv-1", { withdrawalDate: "2026-11-01" })
    );
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
        clientName: "Maturing Client",
        institution: "Maturing Soon Bank",
        maturityDate: soonStr,
        status: "ACTIVE",
      }),
      makeInvestment({
        id: "inv-safe",
        clientName: "Safe Client",
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

  it("reports page lists all 7 trust report types", async () => {
    mockGetReportDefinitions.mockResolvedValue({
      categories: [
        {
          category: "TRUST",
          label: "Trust Reports",
          reports: [
            {
              slug: "trust-receipts-payments",
              name: "Trust Receipts & Payments",
              description:
                "Chronological journal of all trust receipts and payments for a date range",
            },
            {
              slug: "client-trust-balances",
              name: "Client Trust Balances",
              description: "Point-in-time balances per client for a trust account",
            },
            {
              slug: "client-ledger-statement",
              name: "Client Ledger Statement",
              description: "Per-client transaction history with running balance",
            },
            {
              slug: "trust-reconciliation",
              name: "Trust Reconciliation",
              description: "Three-way reconciliation: bank vs cashbook vs client ledger",
            },
            {
              slug: "investment-register",
              name: "Investment Register",
              description:
                "List of all trust investments with status, principal, and interest earned",
            },
            {
              slug: "interest-allocation",
              name: "Interest Allocation",
              description: "Per-client interest allocation breakdown for a specific interest run",
            },
            {
              slug: "section-35-data-pack",
              name: "Section 35 Data Pack",
              description:
                "Composite report combining all trust sub-reports for Section 35 compliance",
            },
          ],
        },
      ],
    });

    await renderReportsPage();

    expect(screen.getByTestId("trust-reports-page")).toBeInTheDocument();

    // Verify all 7 report cards
    const expectedSlugs = [
      "trust-receipts-payments",
      "client-trust-balances",
      "client-ledger-statement",
      "trust-reconciliation",
      "investment-register",
      "interest-allocation",
      "section-35-data-pack",
    ];

    for (const slug of expectedSlugs) {
      expect(screen.getByTestId(`report-card-${slug}`)).toBeInTheDocument();
    }

    // Verify report names are visible
    expect(screen.getByText("Trust Receipts & Payments")).toBeInTheDocument();
    expect(screen.getByText("Client Trust Balances")).toBeInTheDocument();
    expect(screen.getByText("Client Ledger Statement")).toBeInTheDocument();
    expect(screen.getByText("Investment Register")).toBeInTheDocument();
    expect(screen.getByText("Interest Allocation")).toBeInTheDocument();
    expect(screen.getByText("Trust Reconciliation")).toBeInTheDocument();
    expect(screen.getByText("Section 35 Data Pack")).toBeInTheDocument();

    // Verify links point to existing report runner
    const links = screen.getAllByRole("link");
    const reportLinks = links.filter((l) =>
      l.getAttribute("href")?.startsWith("/org/acme/reports/")
    );
    expect(reportLinks).toHaveLength(7);
  });
});
