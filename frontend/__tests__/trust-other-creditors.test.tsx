import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { formatCurrency } from "@/lib/format";
import type {
  CreditorLedgerPage,
  TrustCreditorAccount,
  TrustTransaction,
  UnidentifiedDeposit,
  UnidentifiedDepositPage,
} from "@/lib/types";

// Must mock server-only before importing components that use it
vi.mock("server-only", () => ({}));

// ── Mock org settings & capabilities ─────────────────────────────
const mockGetOrgSettings = vi.fn();
vi.mock("@/lib/api/settings", () => ({
  getOrgSettings: (...args: unknown[]) => mockGetOrgSettings(...args),
}));

const mockFetchMyCapabilities = vi.fn();
vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: (...args: unknown[]) => mockFetchMyCapabilities(...args),
}));

// ── Mock parent trust actions ────────────────────────────────────
const mockFetchTrustAccounts = vi.fn();
vi.mock("@/app/(app)/org/[slug]/trust-accounting/actions", () => ({
  fetchTrustAccounts: (...args: unknown[]) => mockFetchTrustAccounts(...args),
}));

// ── Mock creditor ledger actions ─────────────────────────────────
const mockFetchCreditorAccounts = vi.fn();
const mockFetchCreditorLedgerRows = vi.fn();
const mockFetchUnidentifiedDeposits = vi.fn();
vi.mock("@/app/(app)/org/[slug]/trust-accounting/other-creditors/actions", () => ({
  fetchCreditorAccounts: (...args: unknown[]) => mockFetchCreditorAccounts(...args),
  fetchCreditorLedgerRows: (...args: unknown[]) => mockFetchCreditorLedgerRows(...args),
  fetchUnidentifiedDeposits: (...args: unknown[]) => mockFetchUnidentifiedDeposits(...args),
}));

// ── Mock next/navigation ─────────────────────────────────────────
vi.mock("next/navigation", () => ({
  notFound: () => {
    throw new Error("NEXT_NOT_FOUND");
  },
  // DataTable calls useRouter() unconditionally (FundLedgerTable)
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
}));

import OtherTrustCreditorsPage from "@/app/(app)/org/[slug]/trust-accounting/other-creditors/page";

const PRIMARY_ACCOUNT = {
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
};

function creditorAccounts(fund: { balance: number; inDebit: boolean }): TrustCreditorAccount[] {
  return [
    {
      id: "ca-fund",
      trustAccountId: "acc-1",
      accountType: "FUND_INTEREST_AND_CHARGES",
      name: "Legal Practitioners' Fidelity Fund — interest and charges",
      balance: fund.balance,
      inDebit: fund.inDebit,
      asAt: "2026-11-16",
    },
    {
      id: "ca-unidentified",
      trustAccountId: "acc-1",
      accountType: "UNIDENTIFIED_DEPOSITS",
      name: "Unidentified deposits",
      balance: 1250,
      inDebit: false,
      asAt: "2026-11-16",
    },
  ];
}

function fundRow(overrides: Partial<TrustTransaction> = {}): TrustTransaction {
  return {
    id: "tx-1",
    trustAccountId: "acc-1",
    transactionType: "FUND_INTEREST",
    typeLabel: "Interest received for the Legal Practitioners' Fidelity Fund",
    direction: "IN",
    ledger: "NON_CLIENT",
    amount: 1234.56,
    clientId: null as unknown as string,
    matterId: null,
    creditorAccountId: "ca-fund",
    investmentId: null,
    counterpartyClientId: null,
    invoiceId: null,
    reference: "INT-OCT",
    description: "Interest earned",
    transactionDate: "2026-10-20",
    status: "RECORDED",
    postedAt: "2026-10-20T10:00:00Z",
    approvedBy: null,
    approvedAt: null,
    secondApprovedBy: null,
    secondApprovedAt: null,
    rejectedBy: null,
    rejectedAt: null,
    rejectionReason: null,
    reversalOf: null,
    reversedById: null,
    reversalPending: false,
    bankStatementLineId: "line-1",
    recordedBy: "member-1",
    createdAt: "2026-10-20T10:00:00Z",
    runningBalance: 1234.56,
    chargeRecoverable: null,
    chargeVatAmount: null,
    ...overrides,
  };
}

function ledgerPage(content: TrustTransaction[]): CreditorLedgerPage {
  return {
    content,
    page: {
      totalElements: content.length,
      totalPages: content.length > 0 ? 1 : 0,
      size: 25,
      number: 0,
    },
  };
}

function unidentifiedItem(overrides: Partial<UnidentifiedDeposit> = {}): UnidentifiedDeposit {
  return {
    id: "ud-1",
    trustAccountId: "acc-1",
    receivedDate: "2026-11-03",
    bankDescription: "CREDIT TRANSFER J SMITH",
    bankReference: "REF-9921",
    amount: 300,
    status: "HELD",
    ageDays: 14,
    olderThanOneYear: false,
    allocatedClientId: null,
    allocatedClientName: null,
    allocatedMatterId: null,
    allocatedMatterName: null,
    resolutionTransactionId: null,
    resolutionStatus: null,
    resolvedDate: null,
    bankStatementLineId: "line-9",
    notes: null,
    ...overrides,
  };
}

function itemsPage(content: UnidentifiedDeposit[]): UnidentifiedDepositPage {
  return {
    content,
    page: {
      totalElements: content.length,
      totalPages: content.length > 0 ? 1 : 0,
      size: 25,
      number: 0,
    },
  };
}

/** The exit-month worked set, newest first (as the endpoint returns it). */
const WORKED_SET: TrustTransaction[] = [
  fundRow({
    id: "tx-sweep",
    transactionType: "FUND_SWEEP",
    typeLabel: "Interest paid to the Legal Practitioners' Fidelity Fund",
    direction: "OUT",
    amount: 1119.56,
    reference: "LPFF-OCT",
    transactionDate: "2026-10-24",
    runningBalance: -57.5,
  }),
  fundRow({
    id: "tx-charge-not",
    transactionType: "BANK_CHARGE",
    typeLabel: "Bank charge",
    direction: "OUT",
    amount: 57.5,
    reference: "CHG-2",
    transactionDate: "2026-10-22",
    runningBalance: 1062.06,
    chargeRecoverable: false,
    chargeVatAmount: 7.5,
  }),
  fundRow({
    id: "tx-charge-rec",
    transactionType: "BANK_CHARGE",
    typeLabel: "Bank charge",
    direction: "OUT",
    amount: 115,
    reference: "CHG-1",
    transactionDate: "2026-10-22",
    runningBalance: 1119.56,
    chargeRecoverable: true,
    chargeVatAmount: 15,
  }),
  fundRow({ id: "tx-interest" }),
];

async function renderPage(search: { page?: string } = {}) {
  const result = await OtherTrustCreditorsPage({
    params: Promise.resolve({ slug: "test-org" }),
    searchParams: Promise.resolve(search),
  });
  render(result);
}

describe("Other trust creditors page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetOrgSettings.mockResolvedValue({
      enabledModules: ["trust_accounting"],
      defaultCurrency: "ZAR",
    });
    mockFetchMyCapabilities.mockResolvedValue({
      capabilities: ["VIEW_TRUST"],
      role: "member",
      isAdmin: false,
      isOwner: false,
    });
    mockFetchTrustAccounts.mockResolvedValue([PRIMARY_ACCOUNT]);
    mockFetchCreditorAccounts.mockResolvedValue(
      creditorAccounts({ balance: 300.25, inDebit: false })
    );
    mockFetchCreditorLedgerRows.mockResolvedValue(ledgerPage([]));
    mockFetchUnidentifiedDeposits.mockResolvedValue(itemsPage([]));
  });

  afterEach(() => cleanup());

  it("shows both creditor ledgers with their balances, never the raw account type", async () => {
    await renderPage();

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Other trust creditors");
    expect(mockFetchCreditorAccounts).toHaveBeenCalledWith("acc-1");

    const fund = screen.getByTestId("creditor-FUND_INTEREST_AND_CHARGES");
    expect(
      within(fund).getByText("Legal Practitioners' Fidelity Fund — interest and charges")
    ).toBeInTheDocument();
    // formatCurrency emits a non-breaking space; compare the raw text, not the normalised one
    expect(fund.textContent).toContain(formatCurrency(300.25, "ZAR"));

    const unidentified = screen.getByTestId("creditor-UNIDENTIFIED_DEPOSITS");
    expect(within(unidentified).getByText("Unidentified deposits")).toBeInTheDocument();
    expect(unidentified.textContent).toContain(formatCurrency(1250, "ZAR"));

    const page = screen.getByTestId("other-creditors-page");
    expect(page).not.toHaveTextContent("FUND_INTEREST_AND_CHARGES");
    expect(page).not.toHaveTextContent("UNIDENTIFIED_DEPOSITS");
  });

  it("flags a Fund ledger in debit, and shows no notice when it is not", async () => {
    mockFetchCreditorAccounts.mockResolvedValue(
      creditorAccounts({ balance: -172.5, inDebit: true })
    );
    await renderPage();

    const notice = screen.getByTestId("fund-ledger-in-debit");
    expect(notice).toHaveAttribute("role", "status");
    expect(notice.textContent).toContain(formatCurrency(172.5, "ZAR"));
    expect(notice).toHaveTextContent("in debit");
    expect(
      within(screen.getByTestId("creditor-FUND_INTEREST_AND_CHARGES")).getByTestId(
        "fund-ledger-in-debit"
      )
    ).toBeInTheDocument();

    cleanup();
    mockFetchCreditorAccounts.mockResolvedValue(creditorAccounts({ balance: 0, inDebit: false }));
    await renderPage();
    expect(screen.queryByTestId("fund-ledger-in-debit")).not.toBeInTheDocument();
  });

  it("is not found when the trust accounting module is off", async () => {
    mockGetOrgSettings.mockResolvedValue({ enabledModules: [], defaultCurrency: "ZAR" });

    await expect(renderPage()).rejects.toThrow("NEXT_NOT_FOUND");
    expect(mockFetchTrustAccounts).not.toHaveBeenCalled();
    expect(mockFetchCreditorAccounts).not.toHaveBeenCalled();
  });

  it("is not found without VIEW_TRUST for a member who is neither admin nor owner", async () => {
    mockFetchMyCapabilities.mockResolvedValue({
      capabilities: ["VIEW_PROJECTS"],
      role: "member",
      isAdmin: false,
      isOwner: false,
    });

    await expect(renderPage()).rejects.toThrow("NEXT_NOT_FOUND");
    expect(mockFetchTrustAccounts).not.toHaveBeenCalled();
    expect(mockFetchCreditorAccounts).not.toHaveBeenCalled();
  });
  // ── The Fund ledger's rows (647C) ──────────────────────────────

  it("lists the Fund ledger's rows newest first with their running balances", async () => {
    mockFetchCreditorLedgerRows.mockResolvedValue(
      ledgerPage([
        fundRow({
          id: "tx-pending",
          transactionType: "CHARGE_REFUND",
          typeLabel: "Bank charge refunded from the business account",
          status: "AWAITING_APPROVAL",
          postedAt: null,
          runningBalance: null,
          reference: "REF-PENDING",
        }),
        ...WORKED_SET,
      ])
    );
    await renderPage();

    expect(mockFetchCreditorLedgerRows).toHaveBeenCalledWith("ca-fund", { page: 0, size: 25 });
    const section = screen.getByTestId("fund-ledger-section");
    expect(
      within(section).getByText("Legal Practitioners' Fidelity Fund — interest and charges")
    ).toBeInTheDocument();

    const rows = screen.getAllByTestId("fund-ledger-row");
    expect(rows.map((r) => within(r).getAllByRole("cell")[1].textContent)).toEqual([
      "REF-PENDING",
      "LPFF-OCT",
      "CHG-2",
      "CHG-1",
      "INT-OCT",
    ]);
    const balances = screen.getAllByTestId("fund-ledger-balance").map((c) => c.textContent);
    // formatCurrency emits a non-breaking space; compare the raw text
    expect(balances).toEqual([
      "—",
      formatCurrency(-57.5, "ZAR"),
      formatCurrency(1062.06, "ZAR"),
      formatCurrency(1119.56, "ZAR"),
      formatCurrency(1234.56, "ZAR"),
    ]);
    expect(rows[1]).toHaveTextContent("Interest paid to the Legal Practitioners' Fidelity Fund");
    const table = screen.getByTestId("fund-ledger-table");
    expect(table).not.toHaveTextContent("FUND_SWEEP");
    expect(table).not.toHaveTextContent("BANK_CHARGE");
    expect(table).not.toHaveTextContent("AWAITING_APPROVAL");
  });

  it("shows a bank charge's recoverable flag and VAT, and nothing for other rows", async () => {
    mockFetchCreditorLedgerRows.mockResolvedValue(ledgerPage(WORKED_SET));
    await renderPage();

    const charges = screen.getAllByTestId("fund-ledger-charge");
    expect(charges).toHaveLength(4);
    expect(charges[0]).toHaveTextContent("—"); // sweep
    expect(charges[1].textContent).toBe(`Not recoverable · VAT ${formatCurrency(7.5, "ZAR")}`);
    expect(charges[2].textContent).toBe(`Recoverable · VAT ${formatCurrency(15, "ZAR")}`);
    expect(charges[3]).toHaveTextContent("—"); // interest
  });

  it("shows an empty Fund ledger, and keeps the balances when the rows fail to load", async () => {
    await renderPage();
    expect(screen.getByTestId("fund-ledger-empty")).toBeInTheDocument();
    expect(screen.queryByTestId("fund-ledger-error")).not.toBeInTheDocument();

    cleanup();
    mockFetchCreditorLedgerRows.mockRejectedValue(new Error("boom"));
    await renderPage();
    expect(screen.getByTestId("fund-ledger-error")).toBeInTheDocument();
    expect(screen.queryByTestId("fund-ledger-table")).not.toBeInTheDocument();
    expect(screen.getByTestId("creditor-FUND_INTEREST_AND_CHARGES")).toBeInTheDocument();
    expect(screen.queryByTestId("error-state")).not.toBeInTheDocument();
  });

  it("says a page past the last has no entries and links back to the first page", async () => {
    // The ledger has 3 rows on one page; `?page=4` is past the end, so the server returns none.
    mockFetchCreditorLedgerRows.mockResolvedValue({
      content: [],
      page: { totalElements: 3, totalPages: 1, size: 25, number: 4 },
    });
    await renderPage({ page: "4" });

    expect(mockFetchCreditorLedgerRows).toHaveBeenCalledWith("ca-fund", { page: 4, size: 25 });
    const message = screen.getByTestId("fund-ledger-page-out-of-range");
    expect(message).toHaveTextContent("No entries on this page.");
    expect(within(message).getByRole("link", { name: "Go to the first page" })).toHaveAttribute(
      "href",
      "/org/test-org/trust-accounting/other-creditors?page=0"
    );
    expect(screen.queryByTestId("fund-ledger-empty")).not.toBeInTheDocument();
  });

  it("labels an unposted row with its status, never the raw enum, and keeps Reversed", async () => {
    mockFetchCreditorLedgerRows.mockResolvedValue(
      ledgerPage([
        fundRow({
          id: "tx-awaiting",
          transactionType: "CHARGE_REFUND",
          typeLabel: "Bank charge refunded from the business account",
          status: "AWAITING_APPROVAL",
          postedAt: null,
          runningBalance: null,
          reference: "REF-PENDING-2",
        }),
        fundRow({
          id: "tx-rejected",
          transactionType: "CHARGE_REFUND",
          typeLabel: "Bank charge refunded from the business account",
          status: "REJECTED",
          postedAt: null,
          runningBalance: null,
          reference: "REF-DECLINED",
        }),
        fundRow({ id: "tx-reversed", status: "REVERSED", reference: "INT-REVERSED" }),
        fundRow({ id: "tx-recorded" }),
      ])
    );
    await renderPage();

    const rows = screen.getAllByTestId("fund-ledger-row");
    expect(within(rows[0]).getByTestId("fund-ledger-status")).toHaveTextContent(
      "Awaiting approval"
    );
    expect(within(rows[1]).getByTestId("fund-ledger-status")).toHaveTextContent("Rejected");
    // A posted row (reversed or not) carries no status badge; the reversed one keeps "Reversed".
    expect(within(rows[2]).queryByTestId("fund-ledger-status")).not.toBeInTheDocument();
    expect(within(rows[2]).getByText("Reversed")).toBeInTheDocument();
    expect(within(rows[3]).queryByTestId("fund-ledger-status")).not.toBeInTheDocument();

    const table = screen.getByTestId("fund-ledger-table");
    expect(table).not.toHaveTextContent("AWAITING_APPROVAL");
    expect(table).not.toHaveTextContent("AWAITING APPROVAL");
    expect(table).not.toHaveTextContent("REJECTED");
  });

  // ── Unidentified deposits (647B) ────────────────────────────────

  it("lists the unidentified deposits oldest first with received date, amount, age and status label", async () => {
    mockFetchUnidentifiedDeposits.mockResolvedValue(
      itemsPage([
        unidentifiedItem({
          id: "ud-old",
          receivedDate: "2026-09-01",
          bankDescription: "DEPOSIT CASH BRANCH 0412",
          bankReference: null,
          amount: 1000,
          ageDays: 77,
        }),
        unidentifiedItem(),
      ])
    );
    await renderPage();

    expect(mockFetchUnidentifiedDeposits).toHaveBeenCalledWith("acc-1", { page: 0, size: 25 });
    const section = screen.getByTestId("unidentified-deposits-section");
    expect(
      within(section).getByText("Unidentified deposits", { selector: '[data-slot="card-title"]' })
    ).toBeInTheDocument();
    const rows = screen.getAllByTestId("unidentified-deposit-row");
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent("DEPOSIT CASH BRANCH 0412");
    expect(rows[0].textContent).toContain(formatCurrency(1000, "ZAR"));
    expect(within(rows[0]).getByTestId("unidentified-deposit-age")).toHaveTextContent("77 days");
    expect(rows[1]).toHaveTextContent("CREDIT TRANSFER J SMITH");
    expect(rows[1]).toHaveTextContent("REF-9921");
    expect(rows[1].textContent).toContain(formatCurrency(300, "ZAR"));
    expect(within(rows[1]).getByTestId("unidentified-deposit-age")).toHaveTextContent("14 days");
    expect(within(rows[1]).getByTestId("unidentified-deposit-status")).toHaveTextContent("Held");
    expect(screen.queryByTestId("unidentified-over-one-year")).not.toBeInTheDocument();

    const table = screen.getByTestId("unidentified-deposits-table");
    expect(table).not.toHaveTextContent("HELD");
    expect(table).not.toHaveTextContent("UNIDENTIFIED_DEPOSIT");
  });

  it("flags an item held over one year, and shows a reversed item's label with no age", async () => {
    mockFetchUnidentifiedDeposits.mockResolvedValue(
      itemsPage([
        unidentifiedItem({
          id: "ud-year",
          receivedDate: "2025-11-15",
          ageDays: 367,
          olderThanOneYear: true,
        }),
        unidentifiedItem({
          id: "ud-reversed",
          status: "REVERSED",
          ageDays: 14,
          resolvedDate: "2026-11-03",
        }),
      ])
    );
    await renderPage();

    const rows = screen.getAllByTestId("unidentified-deposit-row");
    expect(within(rows[0]).getByTestId("unidentified-deposit-age")).toHaveTextContent("367 days");
    expect(within(rows[0]).getByTestId("unidentified-over-one-year")).toHaveTextContent(
      "Over one year"
    );
    expect(within(rows[1]).getByTestId("unidentified-deposit-status")).toHaveTextContent(
      "Reversed"
    );
    expect(within(rows[1]).queryByTestId("unidentified-deposit-age")).not.toBeInTheDocument();
    expect(within(rows[1]).queryByTestId("unidentified-over-one-year")).not.toBeInTheDocument();
    expect(screen.getByTestId("unidentified-deposits-table")).not.toHaveTextContent("REVERSED");
  });

  it("shows no items as empty, and keeps the balances and the Fund ledger when the items fail", async () => {
    await renderPage();
    expect(screen.getByTestId("unidentified-deposits-empty")).toHaveTextContent(
      "No unidentified deposits."
    );
    expect(screen.queryByTestId("unidentified-deposits-error")).not.toBeInTheDocument();

    cleanup();
    mockFetchUnidentifiedDeposits.mockRejectedValue(new Error("boom"));
    await renderPage();
    expect(screen.getByTestId("unidentified-deposits-error")).toBeInTheDocument();
    expect(screen.queryByTestId("unidentified-deposits-table")).not.toBeInTheDocument();
    expect(screen.getByTestId("creditor-UNIDENTIFIED_DEPOSITS")).toBeInTheDocument();
    expect(screen.getByTestId("creditor-FUND_INTEREST_AND_CHARGES")).toBeInTheDocument();
    expect(screen.getByTestId("fund-ledger-section")).toBeInTheDocument();
    expect(screen.getByTestId("fund-ledger-empty")).toBeInTheDocument();
    expect(screen.queryByTestId("error-state")).not.toBeInTheDocument();
  });
});
