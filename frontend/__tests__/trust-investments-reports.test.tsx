import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, fireEvent, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SWRConfig, useSWRConfig } from "swr";

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
import { RecordInvestmentInterestDialog } from "@/components/trust/RecordInvestmentInterestDialog";
import type { InvestmentBasis, TrustAccount } from "@/lib/types/trust";
import { todayInSA } from "@b2mash/shared/date";

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
    lockedThrough: null,
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
        depositDate="2026-01-15"
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

  // 648A review cycle 4: the date is held to the server's rules — never in the future, never
  // before the deposit date — with the server's own sentences
  it("withdraw dialog refuses a future date and a date before the deposit date", async () => {
    const user = userEvent.setup();
    render(
      <WithdrawInvestmentDialog
        investmentId="inv-1"
        balance={201250}
        depositDate="2026-01-15"
        currency="ZAR"
        open={true}
        onOpenChange={vi.fn()}
        onSuccess={vi.fn()}
      />
    );

    const dialog = screen.getByTestId("withdraw-investment-dialog");
    const date = within(dialog).getByLabelText("Withdrawal date") as HTMLInputElement;
    expect(date.value).toBe(todayInSA());
    expect(date.min).toBe("2026-01-15");
    expect(date.max).toBe(todayInSA());

    fireEvent.change(date, { target: { value: "2999-01-01" } });
    fireEvent.submit(date.closest("form")!);
    expect(
      await within(dialog).findByText("The withdrawal date cannot be in the future.")
    ).toBeInTheDocument();

    fireEvent.change(date, { target: { value: "2026-01-14" } });
    fireEvent.submit(date.closest("form")!);
    expect(
      await within(dialog).findByText(
        "The withdrawal date cannot be before the investment's deposit date."
      )
    ).toBeInTheDocument();
    expect(mockWithdrawInvestment).not.toHaveBeenCalled();

    // the deposit date itself is allowed
    mockWithdrawInvestment.mockResolvedValue({ success: true, investment: makeInvestment() });
    fireEvent.change(date, { target: { value: "2026-01-15" } });
    await user.click(within(dialog).getByRole("button", { name: "Confirm Withdrawal" }));
    await waitFor(() =>
      expect(mockWithdrawInvestment).toHaveBeenCalledWith("inv-1", { withdrawalDate: "2026-01-15" })
    );
  });

  // --- 648B: section 86(4) interest entries ------------------------------------------------

  it("a section 86(3) investment offers no interest action and points to its account's statement", async () => {
    mockFetchInvestments.mockResolvedValue([
      makeInvestment({
        id: "inv-s864",
        clientName: "Client Instruction Co",
        grossInterest: 400,
        fundShare: 20,
        netInterest: 380,
        balance: 500380,
      }),
      makeInvestment({
        id: "inv-s863",
        clientId: null,
        clientName: null,
        matterId: null,
        investmentAccountId: FIRM_ACCOUNT,
        investmentAccountName: "Section 86(3) Call Account",
        institution: "Investec Call",
        investmentBasis: "FIRM_DISCRETION",
      }),
    ]);

    await renderInvestmentsPage();

    const s864 = screen.getByText("Client Instruction Co").closest("tr") as HTMLElement;
    expect(within(s864).getByRole("button", { name: "Record interest" })).toBeInTheDocument();
    expect(within(s864).getByText(/Gross interest/).textContent).toMatch(/400,00/);
    expect(within(s864).getByText(/Fund 5%/).textContent).toMatch(/20,00/);
    expect(within(s864).getByText(/Net interest/).textContent).toMatch(/380,00/);

    const s863 = screen.getByText("Investec Call").closest("tr") as HTMLElement;
    expect(within(s863).queryByRole("button", { name: "Record interest" })).toBeNull();
    expect(within(s863).getByRole("button", { name: "Withdraw" })).toBeInTheDocument();
    expect(within(s863).getByTestId("investment-interest-fund-note")).toHaveTextContent(
      "Interest is the Fund's — record it from the investment account's statement"
    );
    expect(within(s863).queryByText(/Gross interest/)).toBeNull();
  });

  /** Refetches the dialog's lines as SWR would on focus/reconnect (another user changed them). */
  function RevalidateLines() {
    const { mutate } = useSWRConfig();
    return (
      <button type="button" onClick={() => void mutate(`unmatched-credit-lines-${CLIENT_ACCOUNT}`)}>
        revalidate-lines
      </button>
    );
  }

  const swrProvider = () => new Map();

  function interestDialogTree(open: boolean) {
    // A fresh SWR cache per test: the lines are keyed by account, which every test shares
    return (
      <SWRConfig value={{ provider: swrProvider, dedupingInterval: 0, shouldRetryOnError: false }}>
        <RevalidateLines />
        <RecordInvestmentInterestDialog
          investmentId="inv-1"
          investmentAccountId={CLIENT_ACCOUNT}
          depositDate="2026-01-15"
          currency="ZAR"
          open={open}
          onOpenChange={vi.fn()}
          onSuccess={vi.fn()}
        />
      </SWRConfig>
    );
  }

  function renderInterestDialog() {
    return render(interestDialogTree(true));
  }

  const INTEREST_LINE = {
    id: "line-400",
    lineNumber: 3,
    transactionDate: "2026-10-31",
    description: "Interest capitalised",
    reference: "INT-OCT",
    amount: 400,
    runningBalance: null,
    matchStatus: "UNMATCHED",
    trustTransactionId: null,
    matchConfidence: null,
    classification: null,
    classificationLabel: null,
    suggestedClassification: "INVESTMENT_INTEREST",
    createdTransactionIds: [],
  };

  it("interest dialog records a gross credit from its statement line: Fund R20,00, client R380,00", async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    mockFetchUnmatchedCreditLines.mockResolvedValue([INTEREST_LINE]);
    mockRecordInvestmentInterest.mockResolvedValue({ success: true });
    renderInterestDialog();

    const dialog = screen.getByTestId("record-investment-interest-dialog");
    await waitFor(() => expect(mockFetchUnmatchedCreditLines).toHaveBeenCalledWith(CLIENT_ACCOUNT));
    const lineSelect = await within(dialog).findByLabelText("Statement line");
    await within(dialog).findByRole("option", { name: /Line #3/ });
    await user.selectOptions(lineSelect, "line-400");
    // The question names its radio group
    expect(
      within(dialog).getByRole("radiogroup", { name: "How did the bank credit it?" })
    ).toBeInTheDocument();
    await user.click(
      within(dialog).getByRole("radio", {
        name: "Gross — the Fund's 5% leaves in a separate debit",
      })
    );

    expect((within(dialog).getByLabelText("Date credited") as HTMLInputElement).value).toBe(
      "2026-10-31"
    );
    expect((within(dialog).getByLabelText("Gross interest") as HTMLInputElement).value).toBe("400");
    // Shown from the line, not typed: not marked as fields the firm must fill
    expect(within(dialog).getByLabelText("Date credited")).not.toBeRequired();
    expect(within(dialog).getByLabelText("Gross interest")).not.toBeRequired();
    expect(within(dialog).getByTestId("interest-fund-share").textContent).toMatch(/20,00/);
    expect(within(dialog).getByTestId("interest-client-share").textContent).toMatch(/380,00/);
    expect(within(dialog).queryByText(/GROSS|NET|INVESTMENT_INTEREST/)).toBeNull();

    await user.click(within(dialog).getByRole("button", { name: "Record interest" }));
    await waitFor(() =>
      expect(mockRecordInvestmentInterest).toHaveBeenCalledWith("inv-1", {
        entryDate: "2026-10-31",
        grossAmount: 400,
        presentation: "GROSS",
        bankStatementLineId: "line-400",
      })
    );
  });

  it("interest dialog drops the consumed statement line from its cache after a record", async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    mockFetchUnmatchedCreditLines.mockResolvedValueOnce([INTEREST_LINE]).mockResolvedValue([]);
    mockRecordInvestmentInterest.mockResolvedValue({ success: true });
    renderInterestDialog();

    const dialog = screen.getByTestId("record-investment-interest-dialog");
    const lineSelect = await within(dialog).findByLabelText("Statement line");
    await within(dialog).findByRole("option", { name: /Line #3/ });
    await user.selectOptions(lineSelect, "line-400");
    await user.click(
      within(dialog).getByRole("radio", {
        name: "Gross — the Fund's 5% leaves in a separate debit",
      })
    );
    await user.click(within(dialog).getByRole("button", { name: "Record interest" }));

    await waitFor(() => expect(mockRecordInvestmentInterest).toHaveBeenCalled());
    await waitFor(() =>
      expect(within(dialog).queryByRole("option", { name: /Line #3/ })).toBeNull()
    );
    await waitFor(() => expect(mockFetchUnmatchedCreditLines).toHaveBeenCalledTimes(2));
  });

  it("interest dialog blocks submit when the picked line disappears on a refetch — never a silent bank advice", async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    // The second fetch: another user excluded the line
    mockFetchUnmatchedCreditLines.mockResolvedValueOnce([INTEREST_LINE]).mockResolvedValue([]);
    mockRecordInvestmentInterest.mockResolvedValue({ success: true });
    renderInterestDialog();

    const dialog = screen.getByTestId("record-investment-interest-dialog");
    const lineSelect = (await within(dialog).findByLabelText(
      "Statement line"
    )) as HTMLSelectElement;
    await within(dialog).findByRole("option", { name: /Line #3/ });
    await user.selectOptions(lineSelect, "line-400");
    await user.click(
      within(dialog).getByRole("radio", {
        name: "Gross — the Fund's 5% leaves in a separate debit",
      })
    );
    expect(within(dialog).queryByTestId("interest-line-gone")).toBeNull();

    // Outside the modal (aria-hidden while it is open): click it directly
    fireEvent.click(screen.getByText("revalidate-lines"));
    await waitFor(() =>
      expect(within(dialog).queryByRole("option", { name: /Line #3/ })).toBeNull()
    );

    // The select still shows the lost line, not "Bank advice"; the firm is told and cannot submit
    expect(lineSelect.value).toBe("line-400");
    expect(lineSelect.selectedOptions[0].textContent).toBe("Selected line no longer available");
    expect(within(dialog).getByTestId("interest-line-gone")).toHaveTextContent(
      "The selected statement line is no longer available — choose another line or record a bank advice."
    );
    expect(within(dialog).queryByTestId("interest-bank-advice-note")).toBeNull();
    // The invalid select itself carries the recovery instruction, beside its regular description
    const gone = within(dialog).getByTestId("interest-line-gone");
    const description = within(dialog).getByText(
      "An unmatched credit on the investment account's statement."
    );
    expect(gone.id).not.toBe("");
    expect(lineSelect).toHaveAttribute("aria-invalid", "true");
    expect(lineSelect).toHaveAttribute("aria-errormessage", gone.id);
    expect(lineSelect.getAttribute("aria-describedby")?.split(" ")).toEqual([
      description.id,
      gone.id,
    ]);
    const submit = within(dialog).getByRole("button", { name: "Record interest" });
    expect(submit).toBeDisabled();
    fireEvent.submit(submit.closest("form")!);
    await new Promise((r) => setTimeout(r, 50));
    expect(mockRecordInvestmentInterest).not.toHaveBeenCalled();

    // An explicit choice of bank advice clears the state and records NET without a line
    await user.selectOptions(lineSelect, "");
    expect(within(dialog).queryByTestId("interest-line-gone")).toBeNull();
    expect(within(dialog).getByTestId("interest-bank-advice-note")).toBeInTheDocument();
    expect(
      within(dialog).queryByRole("option", { name: "Selected line no longer available" })
    ).toBeNull();
    expect(lineSelect).not.toHaveAttribute("aria-errormessage");
    expect(lineSelect.getAttribute("aria-describedby")).toBe(description.id);
    // The lost line's date does not carry over to the advice: the firm enters the advice date
    const date = within(dialog).getByLabelText("Date credited") as HTMLInputElement;
    expect(date.value).toBe("");
    expect(date.readOnly).toBe(false);
    fireEvent.change(date, { target: { value: "2026-11-02" } });
    await user.clear(within(dialog).getByLabelText("Gross interest"));
    await user.type(within(dialog).getByLabelText("Gross interest"), "100");
    await user.click(within(dialog).getByRole("button", { name: "Record interest" }));
    await waitFor(() =>
      expect(mockRecordInvestmentInterest).toHaveBeenCalledWith(
        "inv-1",
        expect.objectContaining({ entryDate: "2026-11-02", grossAmount: 100, presentation: "NET" })
      )
    );
    expect(mockRecordInvestmentInterest.mock.calls[0][1]).not.toHaveProperty("bankStatementLineId");
  });

  it("interest dialog clears a line's date and a typed Fund share when the source changes", async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    const otherLine = {
      ...INTEREST_LINE,
      id: "line-600",
      lineNumber: 4,
      transactionDate: "2026-11-05",
      amount: 600,
    };
    mockFetchUnmatchedCreditLines.mockResolvedValue([INTEREST_LINE, otherLine]);
    mockRecordInvestmentInterest.mockResolvedValue({ success: true });
    renderInterestDialog();

    const dialog = screen.getByTestId("record-investment-interest-dialog");
    const lineSelect = await within(dialog).findByLabelText("Statement line");
    const date = within(dialog).getByLabelText("Date credited") as HTMLInputElement;
    const fundShare = within(dialog).getByLabelText(
      "Fund's 5% as the bank shows it"
    ) as HTMLInputElement;
    // No line yet: a bank advice keeps its default date
    expect(date.value).toBe(todayInSA());
    await within(dialog).findByRole("option", { name: /Line #3/ });

    // Line → line: the Fund share typed for R400,00 (R20,01) must not ride onto R600,00
    await user.selectOptions(lineSelect, "line-400");
    await user.click(
      within(dialog).getByRole("radio", {
        name: "Gross — the Fund's 5% leaves in a separate debit",
      })
    );
    await user.type(fundShare, "20.01");
    await user.selectOptions(lineSelect, "line-600");
    expect(date.value).toBe("2026-11-05");
    expect(fundShare.value).toBe("");

    // Line → bank advice: neither the line's date nor a typed Fund share carries over
    await user.type(fundShare, "30.01");
    await user.selectOptions(lineSelect, "");
    expect(date.value).toBe("");
    expect(fundShare.value).toBe("");
    expect((within(dialog).getByLabelText("Gross interest") as HTMLInputElement).value).toBe("");

    // The advice date is required: nothing posts until the firm enters it
    await user.type(within(dialog).getByLabelText("Gross interest"), "100");
    await user.click(within(dialog).getByRole("button", { name: "Record interest" }));
    expect(await within(dialog).findByText("Date must be YYYY-MM-DD")).toBeInTheDocument();
    expect(mockRecordInvestmentInterest).not.toHaveBeenCalled();

    fireEvent.change(date, { target: { value: "2026-11-06" } });
    await user.click(within(dialog).getByRole("button", { name: "Record interest" }));
    await waitFor(() =>
      expect(mockRecordInvestmentInterest).toHaveBeenCalledWith("inv-1", {
        entryDate: "2026-11-06",
        grossAmount: 100,
        presentation: "NET",
      })
    );
  });

  it("interest dialog opened after midnight defaults to that day, and a refetch keeps what the firm typed", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    try {
      // Mounted closed at 23:30 SAST on 17 November
      vi.setSystemTime(new Date("2026-11-17T21:30:00Z"));
      mockFetchUnmatchedCreditLines.mockResolvedValue([]);
      const { rerender } = render(interestDialogTree(false));

      // Opened at 00:30 SAST on 18 November: the default is that day, not the day it mounted
      vi.setSystemTime(new Date("2026-11-17T22:30:00Z"));
      rerender(interestDialogTree(true));
      const dialog = await screen.findByTestId("record-investment-interest-dialog");
      const date = within(dialog).getByLabelText("Date credited") as HTMLInputElement;
      await waitFor(() => expect(date.value).toBe("2026-11-18"));

      // A refetch while open is not a reopen: the firm's entries stay
      fireEvent.change(date, { target: { value: "2026-11-02" } });
      fireEvent.change(within(dialog).getByLabelText("Gross interest"), {
        target: { value: "100" },
      });
      const fetches = mockFetchUnmatchedCreditLines.mock.calls.length;
      fireEvent.click(screen.getByText("revalidate-lines"));
      await waitFor(() =>
        expect(mockFetchUnmatchedCreditLines.mock.calls.length).toBeGreaterThan(fetches)
      );
      expect(date.value).toBe("2026-11-02");
      expect((within(dialog).getByLabelText("Gross interest") as HTMLInputElement).value).toBe(
        "100"
      );
    } finally {
      vi.useRealTimers();
    }
  });

  it("interest dialog keeps a gone line's date and gross read-only, so its values survive the line's return", async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    // Gone on the second fetch, back on the third
    mockFetchUnmatchedCreditLines
      .mockResolvedValueOnce([INTEREST_LINE])
      .mockResolvedValueOnce([])
      .mockResolvedValue([INTEREST_LINE]);
    mockRecordInvestmentInterest.mockResolvedValue({ success: true });
    renderInterestDialog();

    const dialog = screen.getByTestId("record-investment-interest-dialog");
    const lineSelect = await within(dialog).findByLabelText("Statement line");
    await within(dialog).findByRole("option", { name: /Line #3/ });
    await user.selectOptions(lineSelect, "line-400");
    await user.click(
      within(dialog).getByRole("radio", {
        name: "Gross — the Fund's 5% leaves in a separate debit",
      })
    );
    const date = within(dialog).getByLabelText("Date credited") as HTMLInputElement;
    const gross = within(dialog).getByLabelText("Gross interest") as HTMLInputElement;

    fireEvent.click(screen.getByText("revalidate-lines"));
    await within(dialog).findByTestId("interest-line-gone");
    // The selection is retained, so its line-derived values stay locked
    expect(date.readOnly).toBe(true);
    expect(gross.readOnly).toBe(true);
    expect(date.value).toBe("2026-10-31");
    expect(gross.value).toBe("400");

    fireEvent.click(screen.getByText("revalidate-lines"));
    await within(dialog).findByRole("option", { name: /Line #3/ });
    expect(within(dialog).queryByTestId("interest-line-gone")).toBeNull();
    expect(date.value).toBe("2026-10-31");
    expect(gross.value).toBe("400");
    await user.click(within(dialog).getByRole("button", { name: "Record interest" }));
    await waitFor(() =>
      expect(mockRecordInvestmentInterest).toHaveBeenCalledWith("inv-1", {
        entryDate: "2026-10-31",
        grossAmount: 400,
        presentation: "GROSS",
        bankStatementLineId: "line-400",
      })
    );
  });

  it("interest dialog clears a line's before-deposit date error when the firm switches to bank advice", async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    const earlyLine = { ...INTEREST_LINE, id: "line-early", transactionDate: "2026-01-10" };
    mockFetchUnmatchedCreditLines.mockResolvedValue([earlyLine]);
    renderInterestDialog();

    const dialog = screen.getByTestId("record-investment-interest-dialog");
    const lineSelect = await within(dialog).findByLabelText("Statement line");
    await within(dialog).findByRole("option", { name: /Line #3/ });
    await user.selectOptions(lineSelect, "line-early");
    await user.click(
      within(dialog).getByRole("radio", {
        name: "Gross — the Fund's 5% leaves in a separate debit",
      })
    );
    await user.click(within(dialog).getByRole("button", { name: "Record interest" }));
    expect(
      await within(dialog).findByText(
        "The entry date cannot be before the investment's deposit date."
      )
    ).toBeInTheDocument();

    // The blanked advice date is not the line's: its error goes with it, until the next submit
    await user.selectOptions(lineSelect, "");
    expect(
      within(dialog).queryByText("The entry date cannot be before the investment's deposit date.")
    ).toBeNull();
    expect(within(dialog).getByLabelText("Date credited")).not.toHaveAttribute(
      "aria-invalid",
      "true"
    );
    expect(mockRecordInvestmentInterest).not.toHaveBeenCalled();
  });

  it("interest dialog records a bank advice net of the Fund's 5%: gross R100,00 → R5,00 / R95,00", async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    mockFetchUnmatchedCreditLines.mockResolvedValue([]);
    mockRecordInvestmentInterest.mockResolvedValue({ success: true });
    renderInterestDialog();

    const dialog = screen.getByTestId("record-investment-interest-dialog");
    await waitFor(() => expect(mockFetchUnmatchedCreditLines).toHaveBeenCalled());
    // No line: the presentation is fixed to net, with the reason
    expect(
      within(dialog).getByRole("radio", {
        name: "Gross — the Fund's 5% leaves in a separate debit",
      })
    ).toBeDisabled();
    expect(within(dialog).getByRole("radio", { name: "Net of the Fund's 5%" })).toBeChecked();
    expect(within(dialog).getByTestId("interest-bank-advice-note")).toBeInTheDocument();
    // A bank advice's date and gross are the firm's to enter, and say so; the Fund share is optional
    expect(within(dialog).getByLabelText("Date credited")).toBeRequired();
    expect(within(dialog).getByLabelText("Gross interest")).toBeRequired();
    expect(within(dialog).getByLabelText("Fund's 5% as the bank shows it")).not.toBeRequired();

    fireEvent.change(within(dialog).getByLabelText("Date credited"), {
      target: { value: "2026-11-01" },
    });
    await user.type(within(dialog).getByLabelText("Gross interest"), "100");
    expect(within(dialog).getByTestId("interest-fund-share").textContent).toMatch(/5,00/);
    expect(within(dialog).getByTestId("interest-client-share").textContent).toMatch(/95,00/);

    await user.click(within(dialog).getByRole("button", { name: "Record interest" }));
    await waitFor(() =>
      expect(mockRecordInvestmentInterest).toHaveBeenCalledWith("inv-1", {
        entryDate: "2026-11-01",
        grossAmount: 100,
        presentation: "NET",
      })
    );
  });

  it("interest dialog shows a failed statement-line lookup and retries it", async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    mockFetchUnmatchedCreditLines
      .mockRejectedValueOnce(new Error("network"))
      .mockResolvedValue([INTEREST_LINE]);
    renderInterestDialog();

    const dialog = screen.getByTestId("record-investment-interest-dialog");
    const alert = await within(dialog).findByTestId("interest-lines-error");
    expect(alert).toHaveTextContent("The statement lines could not be loaded");
    expect(within(dialog).queryByRole("option", { name: /Line #3/ })).toBeNull();

    await user.click(within(alert).getByRole("button", { name: "Retry" }));
    expect(await within(dialog).findByRole("option", { name: /Line #3/ })).toBeInTheDocument();
    expect(within(dialog).queryByTestId("interest-lines-error")).toBeNull();
    expect(mockFetchUnmatchedCreditLines).toHaveBeenCalledTimes(2);
  });

  it("interest dialog refuses a typed Fund share more than R0,01 from 5% of the gross", async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    mockFetchUnmatchedCreditLines.mockResolvedValue([INTEREST_LINE]);
    renderInterestDialog();

    const dialog = screen.getByTestId("record-investment-interest-dialog");
    const lineSelect = await within(dialog).findByLabelText("Statement line");
    await within(dialog).findByRole("option", { name: /Line #3/ });
    await user.selectOptions(lineSelect, "line-400");
    await user.click(
      within(dialog).getByRole("radio", {
        name: "Gross — the Fund's 5% leaves in a separate debit",
      })
    );
    await user.type(within(dialog).getByLabelText("Fund's 5% as the bank shows it"), "21.50");
    await user.click(within(dialog).getByRole("button", { name: "Record interest" }));

    expect(
      await within(dialog).findByText(/The Fund's share must be within R0,01 of 5% of the gross/)
    ).toBeInTheDocument();
    expect(mockRecordInvestmentInterest).not.toHaveBeenCalled();
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
