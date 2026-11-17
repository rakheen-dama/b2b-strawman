import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { todayInSA } from "@b2mash/shared/date";
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
const mockAllocateUnidentifiedDeposit = vi.fn();
const mockPayUnidentifiedDepositToFund = vi.fn();
vi.mock("@/app/(app)/org/[slug]/trust-accounting/other-creditors/actions", () => ({
  fetchCreditorAccounts: (...args: unknown[]) => mockFetchCreditorAccounts(...args),
  fetchCreditorLedgerRows: (...args: unknown[]) => mockFetchCreditorLedgerRows(...args),
  fetchUnidentifiedDeposits: (...args: unknown[]) => mockFetchUnidentifiedDeposits(...args),
  allocateUnidentifiedDeposit: (...args: unknown[]) => mockAllocateUnidentifiedDeposit(...args),
  payUnidentifiedDepositToFund: (...args: unknown[]) => mockPayUnidentifiedDepositToFund(...args),
}));

// ── Mock the client roster and the matter picker's fetch (647D) ───
const mockListClients = vi.fn();
vi.mock("@/lib/api/clients", () => ({
  listClients: (...args: unknown[]) => mockListClients(...args),
}));

const mockFetchCustomerProjects = vi.fn();
vi.mock("@/app/(app)/org/[slug]/clients/[id]/actions", () => ({
  fetchCustomerProjects: (...args: unknown[]) => mockFetchCustomerProjects(...args),
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

/** The client and matter of an allocation (UUIDs: the schema checks them). */
const CLIENT = {
  id: "550e8400-e29b-41d4-a716-446655440000",
  name: "Sipho Dlamini",
  email: "sipho@example.com",
};
const MATTER = {
  id: "770e8400-e29b-41d4-a716-446655440002",
  name: "Estate late M Dlamini",
  referenceNumber: null,
};

/** A member who may manage trust (647D actions). */
const MANAGER_CAPABILITIES = {
  capabilities: ["VIEW_TRUST", "MANAGE_TRUST"],
  role: "member",
  isAdmin: false,
  isOwner: false,
};

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

async function renderPage(search: { page?: string; itemsPage?: string } = {}) {
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
    mockListClients.mockResolvedValue([CLIENT]);
    mockFetchCustomerProjects.mockResolvedValue([MATTER]);
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

  // ── Paging: `?page=` the Fund ledger, `?itemsPage=` the items ───

  const BASE = "/org/test-org/trust-accounting/other-creditors";

  /** A page of `totalPages` with `number` current — one row is enough to render the pager. */
  function pagedFund(number: number, totalPages: number): CreditorLedgerPage {
    return {
      content: [fundRow()],
      page: { totalElements: totalPages * 25, totalPages, size: 25, number },
    };
  }

  function pagedItems(number: number, totalPages: number): UnidentifiedDepositPage {
    return {
      content: [unidentifiedItem()],
      page: { totalElements: totalPages * 25, totalPages, size: 25, number },
    };
  }

  function pagerLink(sectionTestId: string, name: "Previous" | "Next") {
    return within(screen.getByTestId(sectionTestId)).getByRole("link", { name });
  }

  it("fetches the items page named by ?itemsPage=, and the Fund ledger's own page", async () => {
    await renderPage({ itemsPage: "2" });

    expect(mockFetchUnidentifiedDeposits).toHaveBeenCalledWith("acc-1", { page: 2, size: 25 });
    expect(mockFetchCreditorLedgerRows).toHaveBeenCalledWith("ca-fund", { page: 0, size: 25 });
  });

  it("says an items page past the last has no items and links back to the first items page", async () => {
    // Two items on one page; `?itemsPage=3` is past the end, so the server returns none.
    mockFetchUnidentifiedDeposits.mockResolvedValue({
      content: [],
      page: { totalElements: 2, totalPages: 1, size: 25, number: 3 },
    });
    await renderPage({ page: "0", itemsPage: "3" });

    expect(mockFetchUnidentifiedDeposits).toHaveBeenCalledWith("acc-1", { page: 3, size: 25 });
    const message = screen.getByTestId("unidentified-deposits-page-out-of-range");
    expect(message).toHaveTextContent("No unidentified deposits on this page.");
    expect(within(message).getByRole("link", { name: "Go to the first page" })).toHaveAttribute(
      "href",
      `${BASE}?page=0`
    );
    expect(screen.queryByTestId("unidentified-deposits-empty")).not.toBeInTheDocument();
    // The Fund ledger's first page is not out of range.
    expect(screen.queryByTestId("fund-ledger-page-out-of-range")).not.toBeInTheDocument();
  });

  it("keeps ?itemsPage= on the Fund ledger's links and ?page= on the items' links", async () => {
    mockFetchCreditorLedgerRows.mockResolvedValue(pagedFund(1, 3));
    mockFetchUnidentifiedDeposits.mockResolvedValue(pagedItems(2, 4));
    await renderPage({ page: "1", itemsPage: "2" });

    expect(pagerLink("fund-ledger-section", "Previous")).toHaveAttribute(
      "href",
      `${BASE}?page=0&itemsPage=2`
    );
    expect(pagerLink("fund-ledger-section", "Next")).toHaveAttribute(
      "href",
      `${BASE}?page=2&itemsPage=2`
    );
    expect(pagerLink("unidentified-deposits-section", "Previous")).toHaveAttribute(
      "href",
      `${BASE}?page=1&itemsPage=1`
    );
    expect(pagerLink("unidentified-deposits-section", "Next")).toHaveAttribute(
      "href",
      `${BASE}?page=1&itemsPage=3`
    );
  });

  it("omits ?itemsPage= from the Fund ledger's links when the items are on their first page", async () => {
    mockFetchCreditorLedgerRows.mockResolvedValue(pagedFund(1, 3));
    mockFetchUnidentifiedDeposits.mockResolvedValue(pagedItems(0, 2));
    await renderPage({ page: "1" });

    expect(pagerLink("fund-ledger-section", "Previous")).toHaveAttribute("href", `${BASE}?page=0`);
    expect(pagerLink("fund-ledger-section", "Next")).toHaveAttribute("href", `${BASE}?page=2`);
    // The items are on their first page: Previous is a disabled button, Next keeps ?page=1.
    const items = screen.getByTestId("unidentified-deposits-section");
    expect(within(items).queryByRole("link", { name: "Previous" })).not.toBeInTheDocument();
    expect(within(items).getByRole("button", { name: "Previous" })).toBeDisabled();
    expect(pagerLink("unidentified-deposits-section", "Next")).toHaveAttribute(
      "href",
      `${BASE}?page=1&itemsPage=1`
    );
  });

  // ── Unidentified deposits: resolve (647D) ───────────────────────

  it("offers Allocate and Pay to the Fund on a held item with MANAGE_TRUST, and nothing with VIEW_TRUST only", async () => {
    mockFetchUnidentifiedDeposits.mockResolvedValue(itemsPage([unidentifiedItem()]));
    await renderPage();

    // VIEW_TRUST only (the default member): no actions, no client roster fetched.
    expect(screen.queryByTestId("allocate-btn-ud-1")).not.toBeInTheDocument();
    expect(screen.queryByTestId("pay-to-fund-btn-ud-1")).not.toBeInTheDocument();
    expect(mockListClients).not.toHaveBeenCalled();

    cleanup();
    mockFetchMyCapabilities.mockResolvedValue(MANAGER_CAPABILITIES);
    await renderPage();

    const row = screen.getByTestId("unidentified-deposit-row");
    expect(within(row).getByTestId("allocate-btn-ud-1")).toHaveTextContent("Allocate");
    expect(within(row).getByTestId("pay-to-fund-btn-ud-1")).toHaveTextContent("Pay to the Fund");
    expect(mockListClients).toHaveBeenCalledWith({ status: "ACTIVE" });
  });

  it("requests an allocation from the dialog and shows the server's refusal", async () => {
    const user = userEvent.setup();
    mockFetchMyCapabilities.mockResolvedValue(MANAGER_CAPABILITIES);
    mockFetchUnidentifiedDeposits.mockResolvedValue(itemsPage([unidentifiedItem()]));
    mockAllocateUnidentifiedDeposit.mockResolvedValue({
      success: false,
      error: "This deposit already has a pending allocation or payment",
    });
    await renderPage();

    await user.click(screen.getByTestId("allocate-btn-ud-1"));
    const dialog = await screen.findByTestId("allocate-unidentified-dialog");
    expect(dialog).toHaveTextContent("CREDIT TRANSFER J SMITH");
    expect(dialog).toHaveTextContent("Another member must approve the allocation");

    await user.click(within(dialog).getByTestId("allocate-customer-trigger"));
    await user.click(await screen.findByText("Sipho Dlamini"));
    const matterTrigger = within(dialog).getByTestId("allocate-matter-trigger");
    await waitFor(() => expect(matterTrigger).toHaveTextContent("Select a matter..."));
    await user.click(matterTrigger);
    await user.click(await screen.findByText("Estate late M Dlamini"));
    await waitFor(() => expect(matterTrigger).toHaveTextContent("Estate late M Dlamini"));
    expect(within(dialog).getByTestId("allocate-date")).toHaveValue(todayInSA());

    await user.click(within(dialog).getByTestId("allocate-submit"));

    await waitFor(() =>
      expect(mockAllocateUnidentifiedDeposit).toHaveBeenCalledWith("ud-1", {
        customerId: CLIENT.id,
        projectId: MATTER.id,
        allocationDate: todayInSA(),
        notes: "",
      })
    );
    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      "This deposit already has a pending allocation or payment"
    );
    // A refusal keeps the dialog open.
    expect(screen.getByTestId("allocate-unidentified-dialog")).toBeInTheDocument();
  });

  it("shows the item's age and the under-one-year warning in the Fund dialog, and no warning over a year", async () => {
    const user = userEvent.setup();
    mockFetchMyCapabilities.mockResolvedValue(MANAGER_CAPABILITIES);
    mockFetchUnidentifiedDeposits.mockResolvedValue(
      itemsPage([
        unidentifiedItem({ id: "ud-young", ageDays: 14 }),
        unidentifiedItem({ id: "ud-old", ageDays: 400, olderThanOneYear: true }),
      ])
    );
    mockPayUnidentifiedDepositToFund.mockResolvedValue({ success: true });
    await renderPage();

    await user.click(screen.getByTestId("pay-to-fund-btn-ud-young"));
    let dialog = await screen.findByTestId("pay-to-fund-dialog");
    expect(within(dialog).getByTestId("pay-to-fund-age")).toHaveTextContent("Held for 14 days");
    const warning = within(dialog).getByTestId("pay-to-fund-under-one-year-warning");
    expect(warning).toHaveAttribute("role", "status");
    expect(warning).toHaveTextContent("held for less than a year");
    expect(warning).toHaveTextContent("Legal Practitioners' Fidelity Fund");

    // A warning, never a block: the request goes through.
    await user.type(within(dialog).getByTestId("pay-to-fund-reference"), "LPFF-UID-1");
    await user.click(within(dialog).getByTestId("pay-to-fund-submit"));
    await waitFor(() =>
      expect(mockPayUnidentifiedDepositToFund).toHaveBeenCalledWith("ud-young", {
        reference: "LPFF-UID-1",
        transactionDate: todayInSA(),
        notes: "",
      })
    );
    await waitFor(() => expect(screen.queryByTestId("pay-to-fund-dialog")).not.toBeInTheDocument());

    await user.click(screen.getByTestId("pay-to-fund-btn-ud-old"));
    dialog = await screen.findByTestId("pay-to-fund-dialog");
    expect(within(dialog).getByTestId("pay-to-fund-age")).toHaveTextContent("Held for 400 days");
    expect(
      within(dialog).queryByTestId("pay-to-fund-under-one-year-warning")
    ).not.toBeInTheDocument();
  });

  it("says a pending resolution awaits approval with no actions, and names an allocated item's client and matter", async () => {
    mockFetchMyCapabilities.mockResolvedValue(MANAGER_CAPABILITIES);
    mockFetchUnidentifiedDeposits.mockResolvedValue(
      itemsPage([
        unidentifiedItem({
          id: "ud-pending-alloc",
          allocatedClientId: CLIENT.id,
          allocatedClientName: CLIENT.name,
          allocatedMatterId: MATTER.id,
          allocatedMatterName: MATTER.name,
          resolutionTransactionId: "tx-alloc",
          resolutionStatus: "AWAITING_APPROVAL",
        }),
        unidentifiedItem({
          id: "ud-pending-fund",
          resolutionTransactionId: "tx-fund",
          resolutionStatus: "AWAITING_APPROVAL",
        }),
        unidentifiedItem({
          id: "ud-allocated",
          status: "ALLOCATED",
          allocatedClientId: CLIENT.id,
          allocatedClientName: CLIENT.name,
          allocatedMatterId: MATTER.id,
          allocatedMatterName: MATTER.name,
          resolutionTransactionId: "tx-alloc-2",
          resolutionStatus: "APPROVED",
          resolvedDate: "2026-11-10",
        }),
      ])
    );
    await renderPage();

    const rows = screen.getAllByTestId("unidentified-deposit-row");
    expect(within(rows[0]).getByTestId("unidentified-deposit-pending")).toHaveTextContent(
      "Allocation to Sipho Dlamini awaiting approval"
    );
    expect(within(rows[1]).getByTestId("unidentified-deposit-pending")).toHaveTextContent(
      "Payment to the Fund awaiting approval"
    );
    for (const row of rows.slice(0, 2)) {
      expect(within(row).getByTestId("unidentified-deposit-status")).toHaveTextContent("Held");
    }
    expect(within(rows[2]).getByTestId("unidentified-deposit-status")).toHaveTextContent(
      "Allocated to a client"
    );
    expect(within(rows[2]).getByTestId("unidentified-deposit-allocated-to")).toHaveTextContent(
      "Sipho Dlamini — Estate late M Dlamini"
    );
    expect(screen.queryByRole("button", { name: /Allocate/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Pay to the Fund/ })).not.toBeInTheDocument();
    // Never a raw code on the screen.
    const table = screen.getByTestId("unidentified-deposits-table");
    expect(table).not.toHaveTextContent("AWAITING_APPROVAL");
    expect(table).not.toHaveTextContent("ALLOCATED");
  });
});
