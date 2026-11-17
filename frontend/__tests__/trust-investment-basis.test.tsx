import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, fireEvent, waitFor, within } from "@testing-library/react";
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

// -- Mock interest run actions for wizard ---------------------------------
const mockCreateInterestRun = vi.fn();
const mockCalculateInterest = vi.fn();
const mockFetchInterestRunDetail = vi.fn();

vi.mock("@/app/(app)/org/[slug]/trust-accounting/interest/actions", () => ({
  createInterestRun: (...args: unknown[]) => mockCreateInterestRun(...args),
  calculateInterest: (...args: unknown[]) => mockCalculateInterest(...args),
  fetchInterestRunDetail: (...args: unknown[]) => mockFetchInterestRunDetail(...args),
}));

// -- Mock parent trust actions --------------------------------------------
const mockCreateTrustAccount = vi.fn();

const { account, FIRM_ACCOUNT, CLIENT_ACCOUNT } = vi.hoisted(() => {
  const account = (overrides: Record<string, unknown> = {}) => ({
    id: "acc-x",
    accountName: "Account",
    bankName: "Investec",
    branchCode: "580105",
    accountNumber: "1234567890",
    accountType: "GENERAL",
    investmentBasis: null,
    section: "S86_2",
    isPrimary: false,
    requireDualApproval: false,
    paymentApprovalThreshold: null,
    status: "ACTIVE",
    openedDate: "2025-01-01",
    closedDate: null,
    notes: null,
    createdAt: "2025-01-01T00:00:00Z",
    updatedAt: "2025-01-01T00:00:00Z",
    ...overrides,
  });
  return {
    account,
    FIRM_ACCOUNT: "f1f1f1f1-0000-4000-8000-000000000001",
    CLIENT_ACCOUNT: "c4c4c4c4-0000-4000-8000-000000000004",
  };
});

vi.mock("@/app/(app)/org/[slug]/trust-accounting/actions", () => ({
  createTrustAccount: (...args: unknown[]) => mockCreateTrustAccount(...args),
  fetchTrustAccounts: vi.fn().mockResolvedValue([
    account({ id: "acc-1", accountName: "Main Trust", accountType: "GENERAL", isPrimary: true }),
    account({
      id: FIRM_ACCOUNT,
      accountName: "Section 86(3) Call Account",
      accountType: "INVESTMENT",
      investmentBasis: "FIRM_DISCRETION",
      section: "S86_3",
    }),
    account({
      id: CLIENT_ACCOUNT,
      accountName: "Section 86(4) Fixed Deposit",
      accountType: "INVESTMENT",
      investmentBasis: "CLIENT_INSTRUCTION",
      section: "S86_4",
    }),
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
import { CreateTrustAccountDialog } from "@/components/trust/CreateTrustAccountDialog";
import type { TrustAccount } from "@/lib/types/trust";

// -- Helpers --------------------------------------------------------------

function makeInvestment(overrides: Record<string, unknown> = {}) {
  return {
    id: "inv-1",
    trustAccountId: "acc-1",
    investmentAccountId: FIRM_ACCOUNT,
    investmentAccountName: "Section 86(3) Call Account",
    clientId: null,
    clientName: null,
    matterId: null,
    institution: "FNB Money Market",
    accountNumber: "INV-001",
    principal: 500000,
    interestRate: 0.075,
    depositDate: "2026-01-15",
    maturityDate: "2026-07-15",
    interestEarned: 0,
    balance: 500000,
    status: "ACTIVE",
    awaitingApproval: false,
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

const INVESTMENT_ACCOUNTS = [
  account({
    id: FIRM_ACCOUNT,
    accountName: "Section 86(3) Call Account",
    accountType: "INVESTMENT",
    investmentBasis: "FIRM_DISCRETION",
    section: "S86_3",
  }),
  account({
    id: CLIENT_ACCOUNT,
    accountName: "Section 86(4) Fixed Deposit",
    accountType: "INVESTMENT",
    investmentBasis: "CLIENT_INSTRUCTION",
    section: "S86_4",
  }),
] as TrustAccount[];

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

async function renderInvestmentsPage(searchParams: Record<string, string> = {}) {
  const result = await InvestmentsPage({
    params: Promise.resolve({ slug: "acme" }),
    searchParams: Promise.resolve(searchParams),
  });
  render(result);
}

// -- Tests ----------------------------------------------------------------

describe("Epic 455 / 648A — Investment Basis and investment accounts", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  // 648A (ADR-379): the basis comes first and is worded as the Act's sections
  it("Place Investment dialog asks for the basis in the Act's terms", () => {
    renderPlaceDialog();

    expect(screen.getByText("Investment basis")).toBeInTheDocument();
    expect(
      screen.getByLabelText("Section 86(3) — the firm invests surplus trust money")
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText("Section 86(4) — invested on a client's instruction")
    ).toBeInTheDocument();
    expect(screen.queryByText(/FIRM_DISCRETION|CLIENT_INSTRUCTION/)).not.toBeInTheDocument();
  });

  it("shows who the interest belongs to for each basis", () => {
    renderPlaceDialog();

    // Default is section 86(3)
    const helpText = screen.getByTestId("investment-basis-help");
    expect(helpText.textContent).toContain("Legal Practitioners");
    expect(helpText.textContent).toContain("Fidelity Fund");

    fireEvent.click(screen.getByLabelText("Section 86(4) — invested on a client's instruction"));

    expect(helpText.textContent).toContain("5% to the LPFF (Section 86(5))");
  });

  it("renders Section 86(6) advisory note in Place Investment dialog", () => {
    renderPlaceDialog();

    expect(screen.getByTestId("section-86-6-advisory")).toBeInTheDocument();
    expect(
      screen.getByText(/Legal Practitioners Fidelity Fund \(Section 86\(6\)\)/)
    ).toBeInTheDocument();
  });

  it("the investment account picker offers only the accounts of the chosen basis", async () => {
    const user = userEvent.setup();
    renderPlaceDialog();

    await user.click(screen.getByTestId("investment-account-select"));
    expect(
      screen.getByRole("option", { name: /Section 86\(3\) Call Account/ })
    ).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: /Section 86\(4\) Fixed Deposit/ })).toBeNull();
    await user.keyboard("{Escape}");

    await user.click(screen.getByLabelText("Section 86(4) — invested on a client's instruction"));
    await user.click(screen.getByTestId("investment-account-select"));
    expect(
      screen.getByRole("option", { name: /Section 86\(4\) Fixed Deposit/ })
    ).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: /Section 86\(3\) Call Account/ })).toBeNull();
  });

  it("asks for the client and matter only for a section 86(4) investment", async () => {
    const user = userEvent.setup();
    renderPlaceDialog();

    expect(screen.queryByPlaceholderText("Client UUID")).not.toBeInTheDocument();
    expect(screen.queryByPlaceholderText("Matter UUID")).not.toBeInTheDocument();

    await user.click(screen.getByLabelText("Section 86(4) — invested on a client's instruction"));
    expect(screen.getByPlaceholderText("Client UUID")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("Matter UUID")).toBeInTheDocument();

    await user.click(screen.getByLabelText("Section 86(3) — the firm invests surplus trust money"));
    expect(screen.queryByPlaceholderText("Client UUID")).not.toBeInTheDocument();
  });

  it('"Add investment account" opens the account dialog preset to the basis and selects the new account', async () => {
    const user = userEvent.setup();
    const NEW_ACCOUNT = "a0a0a0a0-0000-4000-8000-00000000000a";
    mockCreateTrustAccount.mockResolvedValue({
      success: true,
      account: account({
        id: NEW_ACCOUNT,
        accountName: "New Section 86(4) Account",
        accountType: "INVESTMENT",
        investmentBasis: "CLIENT_INSTRUCTION",
        section: "S86_4",
      }),
    });
    renderPlaceDialog();

    await user.click(screen.getByLabelText("Section 86(4) — invested on a client's instruction"));
    await user.click(screen.getByRole("button", { name: "Add investment account" }));

    const form = await screen.findByTestId("create-trust-account-form");
    expect(within(form).getByTestId("trust-account-type-select")).toHaveTextContent("Investment");
    expect(within(form).getByTestId("trust-account-type-select")).toBeDisabled();
    expect(within(form).getByTestId("trust-account-basis-select")).toHaveTextContent(
      "Section 86(4) — invested on a client's instruction"
    );
    expect(within(form).getByTestId("trust-account-basis-select")).toBeDisabled();

    await user.type(within(form).getByLabelText("Account Name"), "New Section 86(4) Account");
    await user.type(within(form).getByLabelText("Bank Name"), "Investec");
    await user.type(within(form).getByLabelText("Branch Code"), "580105");
    await user.type(within(form).getByLabelText("Account Number"), "7770009999");
    await user.click(within(form).getByRole("button", { name: "Create Account" }));

    await waitFor(() =>
      expect(mockCreateTrustAccount).toHaveBeenCalledWith(
        expect.objectContaining({
          accountType: "INVESTMENT",
          investmentBasis: "CLIENT_INSTRUCTION",
          isPrimary: false,
        })
      )
    );
    await waitFor(() =>
      expect(screen.getByTestId("investment-account-select")).toHaveTextContent(
        "New Section 86(4) Account"
      )
    );
  });

  it("register shows the investment account, the client or the firm, the balance and readable statuses", async () => {
    mockFetchInvestments.mockResolvedValue([
      makeInvestment({ id: "inv-firm" }),
      makeInvestment({
        id: "inv-client",
        investmentAccountId: CLIENT_ACCOUNT,
        investmentAccountName: "Section 86(4) Fixed Deposit",
        clientId: "cust-1",
        clientName: "Client Instruction Co",
        matterId: "mat-1",
        investmentBasis: "CLIENT_INSTRUCTION",
        principal: 200000,
        balance: 201250,
        status: "MATURED",
      }),
    ]);

    await renderInvestmentsPage();

    expect(screen.getByText("Investment account")).toBeInTheDocument();
    expect(screen.getByText("Balance")).toBeInTheDocument();

    const firmRow = screen.getByTestId("investment-row-inv-firm");
    expect(within(firmRow).getByText("Firm (section 86(3))")).toBeInTheDocument();
    expect(within(firmRow).getByText("Section 86(3) Call Account")).toBeInTheDocument();
    expect(within(firmRow).getByText("Section 86(3)")).toBeInTheDocument();
    expect(within(firmRow).getByText("Active")).toBeInTheDocument();

    const clientRow = screen.getByTestId("investment-row-inv-client");
    expect(within(clientRow).getByText("Client Instruction Co")).toBeInTheDocument();
    expect(within(clientRow).getByText("Section 86(4) Fixed Deposit")).toBeInTheDocument();
    expect(within(clientRow).getByText("Section 86(4)")).toBeInTheDocument();
    expect(within(clientRow).getByText("Matured")).toBeInTheDocument();
    expect(within(clientRow).getByText("5% (statutory)")).toBeInTheDocument();
    expect(clientRow.textContent).toMatch(/201[\s\u00a0]250,00/);

    const table = screen.getByTestId("investments-table");
    expect(table.textContent).not.toMatch(/ACTIVE|MATURED|FIRM_DISCRETION|CLIENT_INSTRUCTION/);
  });

  it("a placement awaiting approval offers no withdrawal", async () => {
    mockFetchInvestments.mockResolvedValue([
      makeInvestment({ id: "inv-pending", balance: 0, awaitingApproval: true }),
    ]);

    await renderInvestmentsPage();

    const row = screen.getByTestId("investment-row-inv-pending");
    expect(within(row).getByText("Awaiting approval")).toBeInTheDocument();
    expect(within(row).queryByRole("button", { name: "Withdraw" })).toBeNull();
  });

  // 648A review: "awaiting approval" is the server's flag, never inferred from a zero balance
  it("an approved investment is not labelled awaiting approval, whatever its balance", async () => {
    mockFetchInvestments.mockResolvedValue([
      makeInvestment({ id: "inv-zero", balance: 0, awaitingApproval: false }),
    ]);

    await renderInvestmentsPage();

    const row = screen.getByTestId("investment-row-inv-zero");
    expect(within(row).queryByText("Awaiting approval")).toBeNull();
    expect(within(row).getByText("Active")).toBeInTheDocument();
  });

  // The backend withdraws an ACTIVE or MATURED investment; interest is recorded on ACTIVE only
  it("a matured investment can be withdrawn but takes no interest entry", async () => {
    mockFetchInvestments.mockResolvedValue([
      makeInvestment({ id: "inv-matured", status: "MATURED" }),
    ]);

    await renderInvestmentsPage();

    const row = screen.getByTestId("investment-row-inv-matured");
    expect(within(row).getByRole("button", { name: "Withdraw" })).toBeInTheDocument();
    expect(within(row).queryByRole("button", { name: "Record Interest" })).toBeNull();
  });

  it("filter dropdown renders and investmentBasis flows to fetchInvestments", async () => {
    mockFetchInvestments.mockResolvedValue([makeInvestment()]);

    await renderInvestmentsPage({ investmentBasis: "CLIENT_INSTRUCTION" });

    expect(screen.getByTestId("investment-basis-filter")).toBeInTheDocument();
    // the funding (primary banking) account, never an investment account
    expect(mockFetchInvestments).toHaveBeenCalledWith("acc-1", "CLIENT_INSTRUCTION");
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
          clientId: "cust-1111-2222-3333-444444444444",
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
      <InterestRunWizard accountId="acc-1" open={true} onOpenChange={vi.fn()} currency="ZAR" />
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
        screen.getByText(/Section 86\(5\): client-instructed investments/)
      ).toBeInTheDocument();
    });
  });

  // Test 8: Interest allocation shows the LPFF arrangement rate for 86(3) allocations (G89-F10)
  it("interest allocation shows the LPFF arrangement rate for non-statutory rate allocations", () => {
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
          clientId: "cust-2222-3333-4444-555555555555",
          clientName: "Arrangement Client",
          averageDailyBalance: 200000,
          daysInPeriod: 90,
          grossInterest: 5000,
          lpffShare: 2500,
          clientShare: 2500,
          trustTransactionId: null,
          lpffRateId: "rate-1",
          statutoryRateApplied: false,
          lpffSharePercent: 0.5,
          createdAt: "2026-04-01T09:00:00Z",
        },
      ],
    });

    render(
      <InterestRunWizard accountId="acc-1" open={true} onOpenChange={vi.fn()} currency="ZAR" />
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

      const arrangementCell = await screen.findByText("LPFF arrangement rate 50.00%");
      expect(arrangementCell).toBeInTheDocument();
      expect(screen.getByText("Arrangement Client")).toBeInTheDocument();
    });
  });

  // 648A (E2E day 60): stacked over Place Investment on a 720-px viewport the account form (with
  // the basis select and its help) is taller than the screen — its content must scroll, or
  // "Create Account" sits outside the viewport
  it("the account dialog's content scrolls within the viewport", () => {
    render(<CreateTrustAccountDialog open={true} onOpenChange={vi.fn()} />);

    // DialogContent puts the testid on its positioned wrapper and the className on the panel
    const panel = screen.getByTestId("create-trust-account-dialog").firstElementChild;
    expect(panel).toHaveClass("max-h-[90vh]");
    expect(panel).toHaveClass("overflow-y-auto");
  });

  // 648C (ADR-379): an investment account carries its basis — section 86(3) or 86(4)
  it("the account dialog asks for the basis only for an Investment account", async () => {
    const user = userEvent.setup();
    render(<CreateTrustAccountDialog open={true} onOpenChange={vi.fn()} />);

    // General (the default): no basis select
    expect(screen.queryByTestId("trust-account-basis-select")).not.toBeInTheDocument();

    await user.click(screen.getByTestId("trust-account-type-select"));
    await user.click(screen.getByRole("option", { name: "Investment" }));
    await user.click(screen.getByTestId("trust-account-basis-select"));
    expect(
      screen.getByRole("option", {
        name: "Section 86(3) — the firm invests surplus trust money",
      })
    ).toBeInTheDocument();
    expect(
      screen.getByRole("option", { name: "Section 86(4) — invested on a client's instruction" })
    ).toBeInTheDocument();
    await user.click(
      screen.getByRole("option", { name: "Section 86(4) — invested on a client's instruction" })
    );

    // Back to General: the select is gone
    await user.click(screen.getByTestId("trust-account-type-select"));
    await user.click(screen.getByRole("option", { name: "General" }));
    expect(screen.queryByTestId("trust-account-basis-select")).not.toBeInTheDocument();
  });

  it("an Investment account cannot be created without its basis; with one, the basis is sent", async () => {
    const user = userEvent.setup();
    mockCreateTrustAccount.mockResolvedValue({ success: true, account: { id: "acc-new" } });
    render(<CreateTrustAccountDialog open={true} onOpenChange={vi.fn()} />);

    await user.type(screen.getByLabelText("Account Name"), "Call Account");
    await user.type(screen.getByLabelText("Bank Name"), "Investec");
    await user.type(screen.getByLabelText("Branch Code"), "580105");
    await user.type(screen.getByLabelText("Account Number"), "7770001234");
    await user.click(screen.getByTestId("trust-account-type-select"));
    await user.click(screen.getByRole("option", { name: "Investment" }));

    await user.click(screen.getByRole("button", { name: "Create Account" }));
    expect(await screen.findByText("Choose the investment basis")).toBeInTheDocument();
    expect(mockCreateTrustAccount).not.toHaveBeenCalled();

    await user.click(screen.getByTestId("trust-account-basis-select"));
    await user.click(
      screen.getByRole("option", { name: "Section 86(4) — invested on a client's instruction" })
    );
    await user.click(screen.getByRole("button", { name: "Create Account" }));

    await waitFor(() => expect(mockCreateTrustAccount).toHaveBeenCalledTimes(1));
    expect(mockCreateTrustAccount).toHaveBeenCalledWith(
      expect.objectContaining({ accountType: "INVESTMENT", investmentBasis: "CLIENT_INSTRUCTION" })
    );
  });
});
