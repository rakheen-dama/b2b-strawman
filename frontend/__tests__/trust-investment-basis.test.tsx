import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, fireEvent, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// -- Mock server-only -----------------------------------------------------
vi.mock("server-only", () => ({}));

// -- Mock server actions for investments ----------------------------------
const mockFetchInvestments = vi.fn();
const mockPlaceInvestment = vi.fn();
const mockRecordInvestmentInterest = vi.fn();
const mockFetchUnmatchedCreditLines = vi.fn();
const mockWithdrawInvestment = vi.fn();
const mockFetchMaturing = vi.fn();

vi.mock("@/app/(app)/org/[slug]/trust-accounting/investments/actions", () => ({
  fetchInvestments: (...args: unknown[]) => mockFetchInvestments(...args),
  placeInvestment: (...args: unknown[]) => mockPlaceInvestment(...args),
  recordInvestmentInterest: (...args: unknown[]) => mockRecordInvestmentInterest(...args),
  fetchUnmatchedCreditLines: (...args: unknown[]) => mockFetchUnmatchedCreditLines(...args),
  withdrawInvestment: (...args: unknown[]) => mockWithdrawInvestment(...args),
  fetchMaturing: (...args: unknown[]) => mockFetchMaturing(...args),
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
import { CreateTrustAccountDialog } from "@/components/trust/CreateTrustAccountDialog";
import type { TrustAccount } from "@/lib/types/trust";
import { todayInSA } from "@b2mash/shared/date";

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
    grossInterest: 0,
    fundShare: 0,
    netInterest: 0,
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
        }),
        expect.any(String)
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

  // 648A gate W-1: the 13-column register was 1344 px wide, so at 1280 Balance, Status and
  // Actions sat behind a horizontal scroll. Eight columns with the secondary facts stacked, and
  // every Badge and the actions allowed to wrap, keep it inside the card.
  it("register fits the card: eight columns, secondary facts stacked, badges and actions wrap", async () => {
    mockFetchInvestments.mockResolvedValue([
      makeInvestment({ id: "inv-held" }),
      makeInvestment({ id: "inv-pending", balance: 0, awaitingApproval: true }),
    ]);

    await renderInvestmentsPage();

    const table = screen.getByTestId("investments-table");
    const headers = Array.from(table.querySelectorAll("thead th")).map((th) => th.textContent);
    expect(headers).toEqual([
      "Client",
      "Investment account",
      "Rate",
      "Term",
      "Principal",
      "Balance",
      "Status",
      "Actions",
    ]);

    const held = screen.getByTestId("investment-row-inv-held");
    expect(held.querySelectorAll("td")).toHaveLength(8);
    // The basis under the client; the institution and account number under the investment account
    const clientCell = within(held).getByTestId("investment-client-cell");
    expect(within(clientCell).getByText("Firm (section 86(3))")).toBeInTheDocument();
    expect(within(clientCell).getByText("Section 86(3)")).toBeInTheDocument();
    const accountCell = within(held).getByTestId("investment-account-cell");
    expect(within(accountCell).getByText("Section 86(3) Call Account")).toBeInTheDocument();
    expect(within(accountCell).getByText("FNB Money Market")).toBeInTheDocument();
    expect(within(accountCell).getByText("INV-001")).toBeInTheDocument();
    // The LPFF share under the rate, the maturity under the deposit date; a section 86(3)
    // investment's interest is the Fund's, so the principal cell says where it is recorded (648B)
    expect(held.textContent).toContain("7.50%");
    expect(held.textContent).toContain("LPFF share Arrangement");
    expect(held.textContent).toMatch(/to 15 Jul 2026/);
    expect(within(held).getByTestId("investment-interest-fund-note")).toHaveTextContent(
      "Interest is the Fund's — record it from the investment account's statement"
    );
    expect(held.textContent).not.toMatch(/Gross interest/);

    // Shared Badge is whitespace-nowrap shrink-0 by default: the register's Badges must wrap
    for (const text of ["Section 86(3)", "Active"]) {
      const badge = within(held).getByText(text);
      expect(badge.className, text).toContain("whitespace-normal");
      expect(badge.className, text).toContain("shrink");
      expect(badge.className, text).not.toContain("whitespace-nowrap");
    }
    const pending = within(screen.getByTestId("investment-row-inv-pending")).getByText(
      "Awaiting approval"
    );
    expect(pending.className).toContain("whitespace-normal");
    expect(pending.className).not.toContain("whitespace-nowrap");

    // The action buttons wrap rather than widen the column; no cell forbids wrapping
    const withdraw = within(held).getByRole("button", { name: "Withdraw" });
    expect(withdraw.parentElement?.className).toContain("flex-wrap");
    for (const cell of Array.from(table.querySelectorAll("th, td"))) {
      expect(cell.className).not.toContain("whitespace-nowrap");
    }
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
      makeInvestment({
        id: "inv-matured",
        status: "MATURED",
        investmentBasis: "CLIENT_INSTRUCTION",
        investmentAccountId: CLIENT_ACCOUNT,
        clientId: "client-1",
        clientName: "Client Instruction Co",
      }),
    ]);

    await renderInvestmentsPage();

    const row = screen.getByTestId("investment-row-inv-matured");
    expect(within(row).getByRole("button", { name: "Withdraw" })).toBeInTheDocument();
    expect(within(row).queryByRole("button", { name: "Record interest" })).toBeNull();
  });

  it("filter dropdown renders and investmentBasis flows to fetchInvestments", async () => {
    mockFetchInvestments.mockResolvedValue([makeInvestment()]);

    await renderInvestmentsPage({ investmentBasis: "CLIENT_INSTRUCTION" });

    expect(screen.getByTestId("investment-basis-filter")).toBeInTheDocument();
    // the funding (primary banking) account, never an investment account
    expect(mockFetchInvestments).toHaveBeenCalledWith("acc-1", "CLIENT_INSTRUCTION");
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
      expect.objectContaining({ accountType: "INVESTMENT", investmentBasis: "CLIENT_INSTRUCTION" }),
      expect.any(String)
    );
  });

  // 648A CodeRabbit cycle 3: a placement is never future-dated (the server refuses it too)
  it("defaults the deposit date to today and refuses a future one", async () => {
    renderPlaceDialog();

    const dateInput = screen.getByLabelText("Deposit Date");
    expect(dateInput).toHaveValue(todayInSA());
    expect(dateInput).toHaveAttribute("max", todayInSA());

    fireEvent.change(dateInput, { target: { value: "2999-01-01" } });
    fireEvent.submit(dateInput.closest("form")!);
    expect(
      await screen.findByText("The deposit date cannot be in the future.")
    ).toBeInTheDocument();
    expect(mockPlaceInvestment).not.toHaveBeenCalled();
  });
});
