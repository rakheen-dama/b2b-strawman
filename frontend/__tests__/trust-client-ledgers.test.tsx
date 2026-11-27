import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, fireEvent, within } from "@testing-library/react";
import { formatCurrency } from "@/lib/format";
import { unserializableProps } from "./helpers/rsc-props";

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
vi.mock("@/app/(app)/org/[slug]/trust-accounting/actions", () => ({
  // 646E: the detail page's type pills come from the registry endpoint
  fetchTrustTransactionTypes: vi.fn().mockResolvedValue([]),
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

// ── Mock client ledger actions ───────────────────────────────────
const mockFetchClientLedgers = vi.fn();
const mockFetchClientLedger = vi.fn();
const mockFetchClientHistory = vi.fn();
const mockGenerateStatementPdf = vi.fn();

vi.mock("@/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions", () => ({
  fetchClientLedgers: (...args: unknown[]) => mockFetchClientLedgers(...args),
  fetchClientLedger: (...args: unknown[]) => mockFetchClientLedger(...args),
  fetchClientHistory: (...args: unknown[]) => mockFetchClientHistory(...args),
  fetchClientStatement: vi.fn(),
  generateStatementPdf: (...args: unknown[]) => mockGenerateStatementPdf(...args),
}));

// ── Mock next/navigation ─────────────────────────────────────────
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

// ── Mock next/link ───────────────────────────────────────────────
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

// OQ #19 (667A): capture the props the server pages hand their client wrappers while still
// rendering the real tables, so the RSC-boundary guard inspects what the pages pass.
const wrapperProps = vi.hoisted(() => ({
  ledgers: [] as Record<string, unknown>[],
  history: [] as Record<string, unknown>[],
}));
vi.mock("@/components/trust/client-ledgers-data-table", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@/components/trust/client-ledgers-data-table")>();
  return {
    ...actual,
    ClientLedgersDataTable: (props: React.ComponentProps<typeof actual.ClientLedgersDataTable>) => {
      wrapperProps.ledgers.push(props as unknown as Record<string, unknown>);
      return <actual.ClientLedgersDataTable {...props} />;
    },
  };
});
vi.mock("@/components/trust/client-history-data-table", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@/components/trust/client-history-data-table")>();
  return {
    ...actual,
    ClientHistoryDataTable: (props: React.ComponentProps<typeof actual.ClientHistoryDataTable>) => {
      wrapperProps.history.push(props as unknown as Record<string, unknown>);
      return <actual.ClientHistoryDataTable {...props} />;
    },
  };
});

// ── Imports after mocks ──────────────────────────────────────────
import ClientLedgersPage from "@/app/(app)/org/[slug]/trust-accounting/client-ledgers/page";
import ClientDetailPage from "@/app/(app)/org/[slug]/trust-accounting/client-ledgers/[clientId]/page";

// ── Helpers ──────────────────────────────────────────────────────

function setupDefaultMocks() {
  mockGetOrgSettings.mockResolvedValue({
    enabledModules: ["trust_accounting"],
    defaultCurrency: "ZAR",
  });
  mockFetchMyCapabilities.mockResolvedValue({
    capabilities: ["VIEW_TRUST", "MANAGE_TRUST"],
    role: "OWNER",
    isAdmin: false,
    isOwner: true,
  });
}

function makeLedgerCard(overrides: Record<string, unknown> = {}) {
  return {
    id: "ledger-1",
    trustAccountId: "acc-1",
    clientId: "cust-1",
    clientName: "Acme Corp",
    balance: 50000,
    totalDeposits: 100000,
    totalPayments: 40000,
    totalFeeTransfers: 10000,
    totalRefunds: 0,
    totalTransfersIn: 0,
    totalTransfersOut: 0,
    totalInvestmentPlacements: 0,
    totalInvestmentReturns: 0,
    investedBalance: 0,
    lastTransactionDate: "2026-04-01",
    createdAt: "2025-01-01T00:00:00Z",
    updatedAt: "2026-04-01T00:00:00Z",
    ...overrides,
  };
}

function makeTx(overrides: Record<string, unknown> = {}) {
  return {
    id: "tx-1",
    trustAccountId: "acc-1",
    transactionType: "DEPOSIT",
    typeLabel: "Deposit",
    direction: "IN",
    ledger: "CLIENT",
    amount: 50000,
    clientId: "cust-1",
    matterId: null,
    counterpartyClientId: null,
    invoiceId: null,
    reference: "DEP-001",
    description: "Initial deposit",
    transactionDate: "2026-04-01",
    status: "APPROVED",
    approvedBy: "member-1",
    approvedAt: "2026-04-01T10:00:00Z",
    secondApprovedBy: null,
    secondApprovedAt: null,
    rejectedBy: null,
    rejectedAt: null,
    rejectionReason: null,
    reversalOf: null,
    reversedById: null,
    reversalPending: false,
    bankStatementLineId: null,
    recordedBy: "member-2",
    createdAt: "2026-04-01T09:00:00Z",
    creditorAccountId: null,
    investmentId: null,
    postedAt: "2026-04-01T10:00:00Z",
    ...overrides,
  };
}

async function renderListPage(searchParams = {}) {
  const result = await ClientLedgersPage({
    params: Promise.resolve({ slug: "acme" }),
    searchParams: Promise.resolve(searchParams),
  });
  render(result);
}

async function renderDetailPage(searchParams = {}) {
  const result = await ClientDetailPage({
    params: Promise.resolve({ slug: "acme", clientId: "cust-1" }),
    searchParams: Promise.resolve(searchParams),
  });
  render(result);
}

// ── Tests ────────────────────────────────────────────────────────

describe("Client Ledgers", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  // Test 1: Ledger list renders client cards
  it("renders client ledger list with client cards", async () => {
    setupDefaultMocks();
    mockFetchClientLedgers.mockResolvedValue({
      content: [
        makeLedgerCard({
          clientId: "cust-1",
          clientName: "Acme Corp",
          balance: 50000,
        }),
        makeLedgerCard({
          id: "ledger-2",
          clientId: "cust-2",
          clientName: "Beta Inc",
          balance: 25000,
          totalDeposits: 50000,
          totalPayments: 20000,
          totalFeeTransfers: 5000,
          totalRefunds: 0,
          totalTransfersIn: 0,
          totalTransfersOut: 0,
        }),
      ],
      totalElements: 2,
      totalPages: 1,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderListPage();

    expect(screen.getByTestId("client-ledgers-table")).toBeInTheDocument();
    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    expect(screen.getByText("Beta Inc")).toBeInTheDocument();
    expect(screen.getByText("2 clients found")).toBeInTheDocument();
  });

  // Test 2: Non-zero balance filter
  // G89-F13: the list shows the derived Refunds total; Phase 92 (646A) removed the Interest column
  it("shows Total Refunds and no Total Interest column", async () => {
    setupDefaultMocks();
    mockFetchClientLedgers.mockResolvedValue({
      content: [
        makeLedgerCard({
          clientId: "cust-636b",
          clientName: "Refunded Client",
          balance: 0,
          totalDeposits: 5000,
          totalPayments: 1250,
          totalFeeTransfers: 0,
          totalRefunds: 3750,
        }),
      ],
      totalElements: 1,
      totalPages: 1,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderListPage();

    const table = screen.getByTestId("client-ledgers-table");
    expect(within(table).queryByText("Total Interest")).toBeNull();
    expect(within(table).getByText("Total Refunds")).toBeInTheDocument();
    // Existing headers stay
    expect(within(table).getByText("Total Deposits")).toBeInTheDocument();
    expect(within(table).getByText("Total Fee Transfers")).toBeInTheDocument();
    const row = screen.getByTestId("ledger-row-cust-636b");
    expect(row.textContent).toContain(formatCurrency(3750, "ZAR"));
  });

  it("passes nonZeroOnly filter to fetchClientLedgers", async () => {
    setupDefaultMocks();
    mockFetchClientLedgers.mockResolvedValue({
      content: [
        makeLedgerCard({
          clientId: "cust-1",
          clientName: "Acme Corp",
          balance: 50000,
        }),
      ],
      totalElements: 1,
      totalPages: 1,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderListPage({ nonZeroOnly: "true" });

    expect(mockFetchClientLedgers).toHaveBeenCalledWith("acc-1", {
      page: 0,
      size: 20,
      nonZeroOnly: true,
      search: undefined,
    });
    expect(screen.getByTestId("client-ledgers-table")).toBeInTheDocument();
  });

  // Test 3: Detail page shows transaction history
  it("renders detail page with transaction history", async () => {
    setupDefaultMocks();
    mockFetchClientLedger.mockResolvedValue(
      makeLedgerCard({
        clientId: "cust-1",
        clientName: "Acme Corp",
        balance: 50000,
      })
    );
    mockFetchClientHistory.mockResolvedValue({
      content: [
        makeTx({
          id: "tx-1",
          reference: "DEP-001",
          transactionType: "DEPOSIT",
          amount: 50000,
          status: "APPROVED",
        }),
        makeTx({
          id: "tx-2",
          reference: "PAY-001",
          transactionType: "PAYMENT",
          typeLabel: "Payment",
          direction: "OUT",
          amount: 10000,
          status: "APPROVED",
        }),
      ],
      totalElements: 2,
      totalPages: 1,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderDetailPage();

    expect(screen.getByTestId("client-detail-page")).toBeInTheDocument();
    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    expect(screen.getByTestId("history-table")).toBeInTheDocument();
    expect(screen.getByText("DEP-001")).toBeInTheDocument();
    expect(screen.getByText("PAY-001")).toBeInTheDocument();
    // Running Balance column header
    expect(screen.getByText("Running Balance")).toBeInTheDocument();
    // Print statement section
    expect(screen.getByTestId("print-statement")).toBeInTheDocument();
  });

  // 648E (ADR-379 L7): general, invested (section 86(4)) and the unchanged total
  it("detail page shows the general balance, the section 86(4) invested figure and the unchanged total", async () => {
    setupDefaultMocks();
    mockFetchClientLedger.mockResolvedValue(
      makeLedgerCard({
        balance: 20000,
        totalDeposits: 50000,
        totalPayments: 0,
        totalFeeTransfers: 0,
        totalInvestmentPlacements: 30000,
        investedBalance: 30000,
      })
    );
    mockFetchClientHistory.mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderDetailPage();

    const summary = within(screen.getByTestId("client-summary"));
    expect(summary.getByText("Trust Balance")).toBeInTheDocument();
    // dd text compared raw: toHaveTextContent collapses the formatter's non-breaking spaces
    const figure = (testId: string) => summary.getByTestId(testId).querySelector("dd")?.textContent;
    expect(figure("ledger-total-invested")).toBe(formatCurrency(30000, "ZAR"));
    expect(figure("ledger-total-held")).toBe(formatCurrency(50000, "ZAR"));
    expect(figure("ledger-total-balance")).toBe(formatCurrency(20000, "ZAR"));
    expect(summary.getByTestId("client-summary-invested")).toBeInTheDocument();
    expect(summary.queryByTestId("ledger-total-transfers-out")).toBeNull();
  });

  it("detail page without investments shows no invested figure", async () => {
    setupDefaultMocks();
    mockFetchClientLedger.mockResolvedValue(makeLedgerCard());
    mockFetchClientHistory.mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderDetailPage();

    expect(screen.queryByText("Invested (section 86(4))")).toBeNull();
    expect(screen.queryByTestId("client-summary-invested")).toBeNull();
  });

  // Test 4: Print statement triggers report generation
  it("print statement button triggers PDF generation", async () => {
    setupDefaultMocks();
    mockFetchClientLedger.mockResolvedValue(
      makeLedgerCard({
        clientId: "cust-1",
        clientName: "Acme Corp",
        balance: 50000,
      })
    );
    mockFetchClientHistory.mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      pageSize: 20,
      pageNumber: 0,
    });
    // Return a minimal base64 PDF
    mockGenerateStatementPdf.mockResolvedValue("JVBER");

    await renderDetailPage();

    const dateFrom = screen.getByTestId("statement-date-from");
    const dateTo = screen.getByTestId("statement-date-to");
    const printBtn = screen.getByTestId("print-statement-btn");

    // Set dates
    fireEvent.change(dateFrom, { target: { value: "2026-01-01" } });
    fireEvent.change(dateTo, { target: { value: "2026-03-31" } });

    // Click print
    fireEvent.click(printBtn);

    // Verify generateStatementPdf was called with correct params
    expect(mockGenerateStatementPdf).toHaveBeenCalledWith(
      "acc-1",
      "cust-1",
      "2026-01-01",
      "2026-03-31"
    );
  });
});

// Phase 91 667A: both pages on PageHeader + server-paged DataTable wrappers.
describe("Client Ledgers — PageHeader + server-paged DataTable (667A)", () => {
  const LIST = "/org/acme/trust-accounting/client-ledgers";

  beforeEach(() => {
    wrapperProps.ledgers.length = 0;
    wrapperProps.history.length = 0;
    setupDefaultMocks();
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  function ledgerPage(content: unknown[], pageNumber = 0, totalPages = 1) {
    return { content, totalElements: content.length, totalPages, pageSize: 20, pageNumber };
  }

  it("list: one h1, the search form in the toolbar and only serializable wrapper props", async () => {
    mockFetchClientLedgers.mockResolvedValue(ledgerPage([makeLedgerCard()]));

    await renderListPage({ search: "acme" });

    const headings = screen.getAllByRole("heading", { level: 1 });
    expect(headings).toHaveLength(1);
    expect(headings[0]).toHaveTextContent("Client Ledgers");
    expect(screen.getByTestId("client-ledgers-page")).toBeInTheDocument();
    const search = screen.getByTestId("client-search");
    expect(search).toHaveAttribute("name", "search");
    expect(search).toHaveAttribute("placeholder", "Search clients...");
    expect(search).toHaveValue("acme");
    expect(search.closest("form")).toHaveAttribute("action", LIST);
    // The page's own submit (a string name matches exactly in Testing Library)
    expect(screen.getByRole("button", { name: "Search" })).toHaveAttribute("type", "submit");
    expect(screen.getByTestId("non-zero-filter")).toHaveAttribute(
      "href",
      `${LIST}?search=acme&nonZeroOnly=true`
    );

    expect(wrapperProps.ledgers).toHaveLength(1);
    expect(wrapperProps.ledgers[0]).toEqual(
      expect.objectContaining({
        rows: expect.any(Array),
        basePath: LIST,
        query: { search: "acme" },
        pageIndex: 0,
        pageCount: 1,
      })
    );
    expect(unserializableProps(wrapperProps.ledgers[0], new Set(["toolbar", "empty"]))).toEqual([]);
    // The row link is built from basePath (no slug prop)
    expect(wrapperProps.ledgers[0]).not.toHaveProperty("slug");
  });

  it("list: page 2 deep link keeps every param in the pager", async () => {
    mockFetchClientLedgers.mockResolvedValue(ledgerPage([makeLedgerCard()], 1, 2));

    await renderListPage({ nonZeroOnly: "true", page: "1" });

    expect(mockFetchClientLedgers).toHaveBeenCalledWith("acc-1", {
      page: 1,
      size: 20,
      nonZeroOnly: true,
      search: undefined,
    });
    expect(screen.getByText("Page 2 of 2")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Previous" })).toHaveAttribute(
      "href",
      `${LIST}?nonZeroOnly=true&page=0`
    );
    // The toggle drops page
    expect(screen.getByTestId("non-zero-filter")).toHaveAttribute("href", LIST);
  });

  it("list: keeps the empty-state testid on the shared empty state", async () => {
    mockFetchClientLedgers.mockResolvedValue(ledgerPage([]));

    await renderListPage({ search: "nobody" });

    expect(screen.queryByTestId("client-ledgers-table")).not.toBeInTheDocument();
    expect(screen.getByTestId("empty-state")).toHaveTextContent(
      "No client ledgers match the current filters"
    );
    expect(screen.getByTestId("client-search")).toBeInTheDocument();
  });

  it("list: keeps the error-state testid and the search form when the read fails", async () => {
    mockFetchClientLedgers.mockRejectedValue(new Error("boom"));

    await renderListPage();

    expect(screen.getByTestId("error-state")).toHaveTextContent(
      "Unable to load client ledgers. Please try again later."
    );
    expect(screen.getByTestId("client-search")).toBeInTheDocument();
  });

  it("list: adds the s86(4) columns while a client has an investment (K-89)", async () => {
    mockFetchClientLedgers.mockResolvedValue(
      ledgerPage([makeLedgerCard({ totalInvestmentPlacements: 30000, investedBalance: 30095 })])
    );

    await renderListPage();

    const table = within(screen.getByTestId("client-ledgers-table"));
    expect(table.getByRole("columnheader", { name: "General balance" })).toBeInTheDocument();
    expect(table.getByRole("columnheader", { name: "Placed in s86(4)" })).toBeInTheDocument();
    expect(table.getByRole("columnheader", { name: "Invested (s86(4))" })).toBeInTheDocument();
    expect(table.queryByRole("columnheader", { name: "Trust Balance" })).toBeNull();
  });

  it("detail: h1 is the client name, Generate Statement sits in the header actions", async () => {
    mockFetchClientLedger.mockResolvedValue(makeLedgerCard({ clientName: "Acme Corp" }));
    mockFetchClientHistory.mockResolvedValue(ledgerPage([makeTx()]));

    await renderDetailPage();

    const headings = screen.getAllByRole("heading", { level: 1 });
    expect(headings).toHaveLength(1);
    expect(headings[0]).toHaveTextContent("Acme Corp");
    const header = screen.getByTestId("page-header");
    expect(within(header).getByTestId("print-statement")).toBeInTheDocument();
    expect(screen.getByTestId("back-link")).toHaveAttribute("href", LIST);
    expect(screen.getByTestId("history-row-tx-1")).toBeInTheDocument();
  });

  it("detail: the summary tiles keep every ledger-total testid once, each with a dd", async () => {
    mockFetchClientLedger.mockResolvedValue(
      makeLedgerCard({
        balance: 20000,
        totalDeposits: 50000,
        totalPayments: 0,
        totalFeeTransfers: 0,
        totalInvestmentPlacements: 30000,
        investedBalance: 30000,
      })
    );
    mockFetchClientHistory.mockResolvedValue(ledgerPage([]));

    await renderDetailPage();

    const summary = within(screen.getByTestId("client-summary"));
    expect(summary.getByTestId("ledger-totals-breakdown")).toBeInTheDocument();
    for (const key of ["deposits", "investment-placements", "balance", "invested", "held"]) {
      expect(screen.getAllByTestId(`ledger-total-${key}`)).toHaveLength(1);
      expect(summary.getByTestId(`ledger-total-${key}`).querySelector("dd")).not.toBeNull();
    }
    expect(summary.getByTestId("client-summary-invested")).toHaveTextContent(
      "held in trust in total"
    );
  });

  it("detail: history pager keeps every param; wrapper props are serializable", async () => {
    mockFetchClientLedger.mockResolvedValue(makeLedgerCard());
    mockFetchClientHistory.mockResolvedValue({
      content: [makeTx()],
      totalElements: 22,
      totalPages: 2,
      pageSize: 20,
      pageNumber: 1,
    });

    await renderDetailPage({ status: "APPROVED", page: "1" });

    const base = `${LIST}/cust-1`;
    expect(screen.getByText("Page 2 of 2")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Previous" })).toHaveAttribute(
      "href",
      `${base}?status=APPROVED&page=0`
    );
    for (const pill of within(screen.getByTestId("status-filters")).getAllByRole("link")) {
      expect(pill.getAttribute("href")).not.toContain("page=");
    }
    expect(wrapperProps.history).toHaveLength(1);
    expect(wrapperProps.history[0]).toEqual(
      expect.objectContaining({ basePath: base, query: { status: "APPROVED", page: "1" } })
    );
    expect(unserializableProps(wrapperProps.history[0], new Set(["toolbar", "empty"]))).toEqual([]);
  });

  it("detail: keeps the filters usable when the history read fails", async () => {
    mockFetchClientLedger.mockResolvedValue(makeLedgerCard());
    mockFetchClientHistory.mockRejectedValue(new Error("boom"));

    await renderDetailPage();

    expect(
      screen.getByRole("heading", {
        name: "Unable to load transaction history. Please try again later.",
      })
    ).toBeInTheDocument();
    expect(screen.getByTestId("date-filters")).toBeInTheDocument();
    expect(screen.queryByTestId("history-table")).not.toBeInTheDocument();
  });
});
