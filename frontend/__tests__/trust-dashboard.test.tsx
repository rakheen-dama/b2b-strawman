import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import { INTEGRITY_FLAG_LABELS } from "@/lib/trust-integrity";
import type { FirmIntegrityReport, IntegrityFlag, IntegrityFlagCode } from "@/lib/types";
import { unserializableProps } from "./helpers/rsc-props";

// Must mock server-only before importing components that use it
vi.mock("server-only", () => ({}));

// Mock org settings API
const mockGetOrgSettings = vi.fn();
vi.mock("@/lib/api/settings", () => ({
  getOrgSettings: (...args: unknown[]) => mockGetOrgSettings(...args),
}));

// Mock the trust actions
const mockFetchTrustAccounts = vi.fn();
const mockFetchDashboardData = vi.fn();
const mockFetchTrustPosition = vi.fn();
const mockFetchTrustBalanceTrend = vi.fn();
const mockFetchTrustIntegrity = vi.fn();

vi.mock("@/app/(app)/org/[slug]/trust-accounting/actions", () => ({
  fetchTrustAccounts: (...args: unknown[]) => mockFetchTrustAccounts(...args),
  fetchDashboardData: (...args: unknown[]) => mockFetchDashboardData(...args),
  fetchTrustPosition: (...args: unknown[]) => mockFetchTrustPosition(...args),
  fetchTrustBalanceTrend: (...args: unknown[]) => mockFetchTrustBalanceTrend(...args),
  fetchTrustIntegrity: (...args: unknown[]) => mockFetchTrustIntegrity(...args),
}));

// Mock next/navigation (DataTable calls useRouter)
const mockNotFound = vi.fn();
vi.mock("next/navigation", () => ({
  notFound: () => {
    mockNotFound();
    throw new Error("NEXT_NOT_FOUND");
  },
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
}));

// The chart has its own test (trust/trust-balance-trend-chart.test.tsx): a prop-capturing stub.
const chartProps = vi.hoisted(() => [] as Record<string, unknown>[]);
vi.mock("@/components/trust/trust-balance-trend-chart", () => ({
  TrustBalanceTrendChart: (props: Record<string, unknown>) => {
    chartProps.push(props);
    return <div data-testid="trust-balance-trend-stub" />;
  },
}));

// OQ #19: capture the props the server page hands TrustTransactionsTable (a client component)
// while still rendering the real table, so the RSC-boundary guard inspects what the page passes.
const tableProps = vi.hoisted(() => [] as Record<string, unknown>[]);
vi.mock("@/components/trust/trust-transactions-table", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@/components/trust/trust-transactions-table")>();
  return {
    ...actual,
    TrustTransactionsTable: (props: React.ComponentProps<typeof actual.TrustTransactionsTable>) => {
      tableProps.push(props as unknown as Record<string, unknown>);
      return <actual.TrustTransactionsTable {...props} />;
    },
  };
});

// Import page after mocks
import TrustAccountingPage from "@/app/(app)/org/[slug]/trust-accounting/page";

// ── Helpers ────────────────────────────────────────────────────────

async function renderPage() {
  const result = await TrustAccountingPage({
    params: Promise.resolve({ slug: "acme" }),
  });
  render(result);
}

function defaultDashboardData() {
  return {
    totalBalance: 125000.5,
    activeClients: 8,
    pendingApprovals: 3,
    recentTransactions: [
      {
        id: "tx-1",
        trustAccountId: "acc-1",
        transactionType: "DEPOSIT" as const,
        typeLabel: "Deposit",
        direction: "IN" as const,
        ledger: "CLIENT" as const,
        amount: 50000,
        clientId: "cust-1",
        matterId: null,
        creditorAccountId: null,
        investmentId: null,
        counterpartyClientId: null,
        invoiceId: null,
        reference: "DEP-001",
        description: "Initial deposit",
        transactionDate: "2026-04-01",
        status: "APPROVED" as const,
        postedAt: "2026-04-01T10:00:00Z",
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
      },
      {
        id: "tx-2",
        trustAccountId: "acc-1",
        transactionType: "PAYMENT" as const,
        typeLabel: "Payment",
        direction: "OUT" as const,
        ledger: "CLIENT" as const,
        amount: 15000,
        clientId: "cust-1",
        matterId: "proj-1",
        creditorAccountId: null,
        investmentId: null,
        counterpartyClientId: null,
        invoiceId: "inv-1",
        reference: "PAY-001",
        description: "Counsel fee",
        transactionDate: "2026-04-02",
        status: "AWAITING_APPROVAL" as const,
        postedAt: null,
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
        bankStatementLineId: null,
        recordedBy: "member-2",
        createdAt: "2026-04-02T09:00:00Z",
      },
    ],
    alerts: [
      {
        type: "AGING_APPROVAL" as const,
        message: "3 transactions awaiting approval",
        severity: "warning" as const,
      },
    ],
  };
}

function position(overrides: Record<string, unknown> = {}) {
  return {
    trustAccountId: "acc-1",
    accountName: "Main Trust Account",
    bankName: "FNB",
    cashbookBalance: 125000.5,
    clientLedgerTotal: 125000.5,
    creditorBreakdown: {
      clientLedgers: 125000.5,
      investmentCreditors: 0,
      fundInterestAndCharges: 0,
      unidentifiedDeposits: 0,
      otherNonClient: 0,
      interAccount: 0,
      total: 125000.5,
    },
    difference: 0,
    lastReconciliationPeriodEnd: "2026-10-31",
    lastReconciliationStatus: "COMPLETED",
    lastReconciliationBalanced: true,
    daysSinceReconciliation: 13,
    pendingApprovalCount: 3,
    accounts: [{ id: "acc-1", accountName: "Main Trust Account", isPrimary: true }],
    integrity: { balanced: true, flags: [] },
    ...overrides,
  };
}

function trend() {
  return {
    trustAccountId: "acc-1",
    accountName: "Main Trust Account",
    currency: "ZAR",
    asOf: "2026-11-13",
    openingCashbookBalance: 0,
    openingClientLedgerTotal: 0,
    months: [
      {
        month: "2026-11",
        periodEnd: "2026-11-13",
        cashbookBalance: 125000.5,
        clientLedgerTotal: 125000.5,
        partial: true,
      },
    ],
    accounts: [{ id: "acc-1", accountName: "Main Trust Account", isPrimary: true }],
  };
}

function accountReport(
  overrides: Partial<FirmIntegrityReport["accounts"][number]> = {}
): FirmIntegrityReport["accounts"][number] {
  return {
    trustAccountId: "acc-1",
    asAt: "2026-11-13",
    position: {
      trustAccountId: "acc-1",
      asAt: "2026-11-13",
      cashbook: 125000.5,
      clientLedgers: 125000.5,
      investmentCreditors: 0,
      fundLedger: 0,
      unidentifiedDeposits: 0,
      otherNonClient: 0,
      interAccount: 0,
      creditorTotal: 125000.5,
      difference: 0,
    },
    bank: null,
    balanced: true,
    flags: [],
    lastCompletedReconciliationId: null,
    lastReconciledThrough: null,
    ...overrides,
  };
}

function firmReport(overrides: Partial<FirmIntegrityReport> = {}): FirmIntegrityReport {
  return {
    asAt: "2026-11-13",
    balanced: true,
    cashbook: 125000.5,
    clientLedgers: 125000.5,
    investmentCreditors: 0,
    fundLedger: 0,
    unidentifiedDeposits: 0,
    otherNonClient: 0,
    interAccount: 0,
    creditorTotal: 125000.5,
    difference: 0,
    flags: [],
    accounts: [accountReport()],
    ...overrides,
  };
}

function setupDefaultMocks() {
  mockGetOrgSettings.mockResolvedValue({
    enabledModules: ["trust_accounting"],
    defaultCurrency: "ZAR",
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
  mockFetchDashboardData.mockResolvedValue(defaultDashboardData());
  mockFetchTrustPosition.mockResolvedValue({ state: "ok", data: position() });
  mockFetchTrustBalanceTrend.mockResolvedValue({ state: "ok", data: trend() });
  mockFetchTrustIntegrity.mockResolvedValue({ state: "ok", data: firmReport() });
}

function summaryCards() {
  return within(screen.getByTestId("summary-cards"));
}

function tile(label: string) {
  return within(screen.getByRole("group", { name: label }));
}

function tileValue(label: string) {
  return screen
    .getByRole("group", { name: label })
    .querySelector('[data-slot="stat-card-value"]') as HTMLElement;
}

// ── Tests ──────────────────────────────────────────────────────────

describe("TrustAccountingPage", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    chartProps.length = 0;
    tableProps.length = 0;
  });

  it("renders summary cards with correct data", async () => {
    setupDefaultMocks();
    await renderPage();

    // One h1 (PageHeader), title unchanged.
    expect(screen.getByRole("heading", { level: 1, name: "Trust Accounting" })).toBeInTheDocument();

    // The four frozen labels sit inside summary-cards (e2e day-07-14-work-trust).
    for (const label of [
      "Trust Balance",
      "Active Clients",
      "Pending Approvals",
      "Reconciliation",
    ]) {
      expect(summaryCards().getByText(label)).toBeInTheDocument();
    }
    expect(screen.getByTestId("summary-cards")).toHaveTextContent(
      "Main Trust Account cashbook balance"
    );

    // Trust Balance = trust-position cashbook. locale-agnostic: en-ZA "R 125 000,50" or en-US
    expect(tileValue("Trust Balance")).toHaveTextContent(/R[\s\u00A0]125[\s\u00A0,]000[,.]50/);
    expect(mockFetchTrustPosition).toHaveBeenCalledWith("acc-1");
    expect(mockFetchTrustBalanceTrend).toHaveBeenCalledWith("acc-1", 12);

    // Active Clients card (still from the dashboard aggregation)
    expect(tileValue("Active Clients")).toHaveTextContent("8");

    // Pending Approvals card (trust-position)
    expect(tileValue("Pending Approvals")).toHaveTextContent("3");

    // Reconciliation card
    expect(screen.getByText("Reconciliation")).toBeInTheDocument();
  });

  // 643.1 (declared side effect): the Reconciliation tile reads the trust position, so a
  // reconciled account shows "Balanced" and its period end instead of "Not yet reconciled".
  it("a reconciled trust position renders Balanced and the period-end date", async () => {
    setupDefaultMocks();
    mockFetchTrustPosition.mockResolvedValue({ state: "ok", data: position() });
    mockFetchTrustBalanceTrend.mockResolvedValue({ state: "absent" });
    await renderPage();

    const cards = within(screen.getByTestId("summary-cards"));
    expect(cards.getByText("Balanced")).toBeInTheDocument();
    expect(cards.getByText(/Last reconciled/)).toHaveTextContent(
      `Last reconciled ${formatLocalDate("2026-10-31")} · 13 days ago`
    );
    expect(cards.queryByText("Not yet reconciled")).not.toBeInTheDocument();
  });

  it("a balanced position shows the trust-creditor total with no difference line", async () => {
    setupDefaultMocks();
    await renderPage();

    const balance = tile("Trust Balance");
    expect(balance.getByText(/^Trust creditors R/)).toHaveTextContent(
      /Trust creditors R[\s\u00A0]125[\s\u00A0,]000[,.]50/
    );
    expect(balance.queryByText(/^Difference/)).not.toBeInTheDocument();
    expect(tile("Reconciliation").queryByText(/in draft/)).not.toBeInTheDocument();
  });

  it("an unbalanced draft reconciliation renders Unbalanced, the draft period hint, and the difference line", async () => {
    setupDefaultMocks();
    mockFetchTrustPosition.mockResolvedValue({
      state: "ok",
      data: position({
        clientLedgerTotal: 124999.16,
        creditorBreakdown: {
          clientLedgers: 124999.16,
          investmentCreditors: 0,
          fundInterestAndCharges: 0,
          unidentifiedDeposits: 0,
          otherNonClient: 0,
          interAccount: 0,
          total: 124999.16,
        },
        difference: 1.34,
        lastReconciliationPeriodEnd: "2026-11-13",
        lastReconciliationStatus: "DRAFT",
        lastReconciliationBalanced: false,
        daysSinceReconciliation: 0,
      }),
    });
    await renderPage();

    const recon = tile("Reconciliation");
    expect(recon.getByText("Unbalanced")).toBeInTheDocument();
    // A draft is not a reconciliation yet: the Home widget's copy, no "Last reconciled", no days-ago.
    expect(recon.getByText(/in draft/)).toHaveTextContent(
      new RegExp(`^Period to ${formatLocalDate("2026-11-13")}, in draft$`)
    );
    expect(recon.queryByText(/Last reconciled/)).not.toBeInTheDocument();
    expect(recon.queryByText(/days ago/)).not.toBeInTheDocument();
    expect(tile("Trust Balance").getByText(/^Difference/)).toHaveTextContent(
      /Difference R[\s\u00A0]1[,.]34/
    );
  });

  it("a never-reconciled account renders Not yet reconciled", async () => {
    setupDefaultMocks();
    mockFetchTrustPosition.mockResolvedValue({
      state: "ok",
      data: position({
        lastReconciliationPeriodEnd: null,
        lastReconciliationStatus: null,
        lastReconciliationBalanced: null,
        daysSinceReconciliation: null,
      }),
    });
    await renderPage();

    const recon = tile("Reconciliation");
    expect(recon.getByText("Not yet reconciled")).toBeInTheDocument();
    expect(recon.getByText("No reconciliation records found")).toBeInTheDocument();
    expect(recon.queryByText("Balanced")).not.toBeInTheDocument();
  });

  it.each([["error"], ["absent"]])(
    "a trust-position %s read fails the three position tiles but keeps Active Clients",
    async (state) => {
      setupDefaultMocks();
      mockFetchTrustPosition.mockResolvedValue({ state });
      await renderPage();

      for (const label of ["Trust Balance", "Pending Approvals", "Reconciliation"]) {
        expect(tile(label).getByText("Couldn't load")).toBeInTheDocument();
      }
      expect(tile("Active Clients").queryByText("Couldn't load")).not.toBeInTheDocument();
      expect(tileValue("Active Clients")).toHaveTextContent("8");
      // The labels stay (e2e asserts them).
      expect(summaryCards().getByText("Trust Balance")).toBeInTheDocument();
    }
  );

  it("hands the trend chart the trend read as plain data", async () => {
    setupDefaultMocks();
    await renderPage();

    expect(screen.getByTestId("trust-balance-trend-stub")).toBeInTheDocument();
    expect(chartProps).toHaveLength(1);
    expect(chartProps[0]).toEqual({ trend: trend(), failed: false });
  });

  it.each([
    ["error", { trend: null, failed: true }],
    ["absent", { trend: null, failed: false }],
  ])("a trend %s read reaches the chart as its state", async (state, expected) => {
    setupDefaultMocks();
    mockFetchTrustBalanceTrend.mockResolvedValue({ state });
    await renderPage();

    expect(chartProps[0]).toEqual(expected);
    // The rest of the page still renders.
    expect(screen.getByTestId("transactions-table")).toBeInTheDocument();
  });

  it("passes the client components only serializable props (RSC boundary, OQ #19)", async () => {
    setupDefaultMocks();
    await renderPage();

    expect(tableProps).toHaveLength(1);
    // Non-vacuous: the page really passed the data props and the empty slot.
    expect(tableProps[0]).toEqual(
      expect.objectContaining({ rows: expect.any(Array), currency: "ZAR" })
    );
    expect(tableProps[0].empty).toBeTruthy();
    expect(unserializableProps(tableProps[0], new Set(["empty"]))).toEqual([]);

    expect(chartProps).toHaveLength(1);
    expect(chartProps[0].trend).toBeTruthy();
    expect(unserializableProps(chartProps[0], new Set())).toEqual([]);
  });

  it("renders recent transactions table with data", async () => {
    setupDefaultMocks();
    await renderPage();

    // Table headers
    expect(screen.getByText("Recent Transactions")).toBeInTheDocument();
    // Frozen testid on the <table> element itself
    expect(screen.getByTestId("transactions-table").tagName).toBe("TABLE");

    // Transaction references
    expect(screen.getByText("DEP-001")).toBeInTheDocument();
    expect(screen.getByText("PAY-001")).toBeInTheDocument();

    // Transaction type badges
    expect(screen.getByText("Deposit")).toBeInTheDocument();
    expect(screen.getByText("Payment")).toBeInTheDocument();

    // Status badges
    expect(screen.getByText("APPROVED")).toBeInTheDocument();
    expect(screen.getByText("AWAITING APPROVAL")).toBeInTheDocument();
  });

  // 643A OBS D1: the kit cell is nowrap, so a long system reference (INT-CREDIT-<uuid>-<uuid>)
  // pushed Amount and Status out of the card. The reference cell wraps; the amount does not.
  it("wraps long references so the amount and status stay in the card", async () => {
    setupDefaultMocks();
    const data = defaultDashboardData();
    const reference = `INT-CREDIT-${"a1b2c3d4-e5f6-7890-abcd-ef1234567890"}-${"0f9e8d7c-6b5a-4321-9876-543210fedcba"}`;
    mockFetchDashboardData.mockResolvedValue({
      ...data,
      recentTransactions: [{ ...data.recentTransactions[0], reference }],
    });
    await renderPage();

    const cells = within(screen.getByText(reference).closest("tr") as HTMLElement).getAllByRole(
      "cell"
    );
    expect(cells[1]).toHaveClass("whitespace-normal", "[overflow-wrap:anywhere]");
    expect(cells[1]).not.toHaveClass("whitespace-nowrap");
    expect(cells[3]).toHaveClass("whitespace-nowrap", "text-right");
  });

  it("renders the empty recent-transactions state without a table", async () => {
    setupDefaultMocks();
    mockFetchDashboardData.mockResolvedValue({ ...defaultDashboardData(), recentTransactions: [] });
    await renderPage();

    expect(screen.getByText("No transactions recorded yet")).toBeInTheDocument();
    expect(screen.queryByTestId("transactions-table")).not.toBeInTheDocument();
  });

  // G88-F23 (Phase 89 630C): REFUND moves money out of trust — outflow colour + arrow on the
  // dashboard's recent transactions, same as the transactions and client-ledger pages. 643A
  // restyled the colour onto a token, so the case asserts the stated direction instead.
  it("renders REFUND as an outflow in recent transactions (no green, up-right arrow)", async () => {
    setupDefaultMocks();
    const data = defaultDashboardData();
    const refund = {
      ...data.recentTransactions[0],
      id: "tx-3",
      transactionType: "REFUND" as const,
      typeLabel: "Refund",
      direction: "OUT" as const,
      amount: 1000,
      reference: "REF-001",
      description: "Refund to client",
    };
    // 646E: a row whose direction cannot be resolved (an orphan reversal) draws no arrow
    const neutral = {
      ...data.recentTransactions[0],
      id: "tx-4",
      transactionType: "REVERSAL",
      typeLabel: "Reversal",
      direction: "NEUTRAL" as const,
      amount: 200,
      reference: "REV-ORPHAN",
    };
    mockFetchDashboardData.mockResolvedValue({
      ...data,
      recentTransactions: [...data.recentTransactions, refund, neutral],
    });
    await renderPage();

    const amountSpan = (reference: string) =>
      within(screen.getByText(reference).closest("tr") as HTMLElement)
        .getAllByRole("cell")[3]
        .querySelector("span") as HTMLElement;

    const refundAmount = amountSpan("REF-001");
    expect(refundAmount).toHaveAttribute("data-direction", "out");
    expect(refundAmount.className).not.toContain("text-success-foreground");
    expect(refundAmount.querySelector(".lucide-arrow-up-right")).not.toBeNull();
    expect(refundAmount.querySelector(".lucide-arrow-down-left")).toBeNull();

    const deposit = amountSpan("DEP-001");
    expect(deposit).toHaveAttribute("data-direction", "in");
    expect(deposit.className).toContain("text-success-foreground");
    expect(deposit.querySelector(".lucide-arrow-down-left")).not.toBeNull();

    const orphan = amountSpan("REV-ORPHAN");
    expect(orphan).toHaveAttribute("data-direction", "neutral");
    expect(orphan.className).not.toContain("text-success-foreground");
    expect(orphan.querySelector("svg")).toBeNull();
    expect(screen.getByText("Refund")).toBeInTheDocument();
  });

  it("renders Module Not Available when trust_accounting module is disabled", async () => {
    mockGetOrgSettings.mockResolvedValue({
      enabledModules: [],
    });

    await renderPage();

    expect(screen.getByText("Module Not Available")).toBeInTheDocument();
    expect(
      screen.getByText("The Trust Accounting module is not enabled for your organization.")
    ).toBeInTheDocument();
    expect(mockNotFound).not.toHaveBeenCalled();
  });

  it("renders No Trust Accounts without reading the trust position when no account exists", async () => {
    setupDefaultMocks();
    mockFetchTrustAccounts.mockResolvedValue([]);
    await renderPage();

    expect(screen.getByText("No Trust Accounts")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add Account" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Record Transaction" })).not.toBeInTheDocument();
    expect(mockFetchTrustPosition).not.toHaveBeenCalled();
    expect(mockFetchTrustBalanceTrend).not.toHaveBeenCalled();
    expect(mockFetchTrustIntegrity).not.toHaveBeenCalled();
    expect(screen.queryByTestId("trust-integrity-card")).not.toBeInTheDocument();
  });

  it("keeps the header links and shows the error card when the dashboard read fails", async () => {
    setupDefaultMocks();
    mockFetchDashboardData.mockRejectedValue(new Error("boom"));
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
    await renderPage();

    expect(
      screen.getByText("Unable to load trust account data. Please try again later.")
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Client Ledgers" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Record Transaction" })).toBeInTheDocument();
    expect(screen.queryByTestId("summary-cards")).not.toBeInTheDocument();
    consoleError.mockRestore();
  });

  it("renders pending approvals warning badge when count > 0", async () => {
    setupDefaultMocks();
    await renderPage();

    expect(tile("Pending Approvals").getByText("Needs attention")).toBeInTheDocument();
  });

  it("renders no warning badge when nothing awaits approval", async () => {
    setupDefaultMocks();
    mockFetchTrustPosition.mockResolvedValue({
      state: "ok",
      data: position({ pendingApprovalCount: 0 }),
    });
    await renderPage();

    expect(tileValue("Pending Approvals")).toHaveTextContent("0");
    expect(screen.queryByText("Needs attention")).not.toBeInTheDocument();
  });

  it("renders trust sub-items in nav with VIEW_TRUST capability", async () => {
    // Import nav items to verify structure
    const { NAV_GROUPS } = await import("@/lib/nav-items");
    const trustZone = NAV_GROUPS.find((g) => g.id === "trust");

    expect(trustZone).toBeDefined();

    const trustItems = trustZone!.items.filter(
      (item) => item.requiredModule === "trust_accounting"
    );

    // Trust zone — 8 items (646B added "Other trust creditors"; 651C removes "Interest")
    expect(trustItems.length).toBe(8);

    // All trust items should require VIEW_TRUST capability
    for (const item of trustItems) {
      expect(item.requiredCapability).toBe("VIEW_TRUST");
    }

    // Verify sub-item labels
    const labels = trustItems.map((i) => i.label);
    expect(labels).toContain("Overview");
    expect(labels).toContain("Transactions");
    expect(labels).toContain("Client ledgers");
    expect(labels).toContain("Other trust creditors");
    expect(labels).toContain("Reconciliation");
    expect(labels).toContain("Interest");
    expect(labels).toContain("Investments");
    expect(labels).toContain("Trust reports");
  });
});

// ── Trust integrity (Phase 92 649C) ────────────────────────────────

/** formatCurrency as toHaveTextContent sees it (it collapses the NBSP grouping to spaces). */
function money(amount: number): string {
  return formatCurrency(amount, "ZAR").replace(/\s/g, " ");
}

/** One flag per code, as the backend raises it (§16.3.9); `href`/`linkName` = where it is acted on. */
const FLAG_CASES: {
  code: IntegrityFlagCode;
  flag: IntegrityFlag;
  linkName: string | null;
  href: string | null;
}[] = [
  {
    code: "BANK_DIFFERENCE",
    flag: {
      code: "BANK_DIFFERENCE",
      severity: "BLOCKING",
      message:
        "Adjusted bank balance R2 250,00 differs from the cashbook R1 000,00 by R1 250,00 as at 31 October 2026.",
      amount: 1250,
      subjects: [],
    },
    linkName: "Reconciliation",
    href: "/org/acme/trust-accounting/reconciliation",
  },
  {
    code: "BOOKS_OUT_OF_BALANCE",
    flag: {
      code: "BOOKS_OUT_OF_BALANCE",
      severity: "BLOCKING",
      message:
        "As at 13 November 2026 the cashbook is R125 050,50 and trust creditors total R125 000,50 — a difference of R50,00.",
      amount: 50,
      subjects: [],
    },
    linkName: null,
    href: null,
  },
  {
    code: "INTER_ACCOUNT_NOT_ZERO",
    flag: {
      code: "INTER_ACCOUNT_NOT_ZERO",
      severity: "BLOCKING",
      message: "Section 86(3) placements across the firm's trust accounts total R300,00, not zero.",
      amount: 300,
      subjects: [{ kind: "TRUST_ACCOUNT", id: "acc-1", name: "Main Trust Account", amount: 300 }],
    },
    linkName: null,
    href: null,
  },
  {
    code: "UNIDENTIFIED_ITEMS_MISMATCH",
    flag: {
      code: "UNIDENTIFIED_ITEMS_MISMATCH",
      severity: "BLOCKING",
      message:
        'Unidentified deposit "ACB CREDIT UNKNOWN" of R1 250,00: its ledger rows total R0,00 as at 13 November 2026.',
      amount: null,
      subjects: [
        { kind: "UNIDENTIFIED_DEPOSIT", id: "ud-1", name: "ACB CREDIT UNKNOWN", amount: 1250 },
      ],
    },
    linkName: "ACB CREDIT UNKNOWN",
    href: "/org/acme/trust-accounting/other-creditors",
  },
  {
    code: "INVESTMENT_BALANCE_MISMATCH",
    flag: {
      code: "INVESTMENT_BALANCE_MISMATCH",
      severity: "BLOCKING",
      message:
        "Investment Investec 1002003: its ledger rows total R20 095,00 against R20 000,00 placed, credited and withdrawn as at 13 November 2026.",
      amount: 95,
      subjects: [{ kind: "INVESTMENT", id: "inv-1", name: "Investec 1002003", amount: 95 }],
    },
    linkName: "Investec 1002003",
    href: "/org/acme/trust-accounting/investments",
  },
  {
    code: "UNRESOLVED_STATEMENT_LINES",
    flag: {
      code: "UNRESOLVED_STATEMENT_LINES",
      severity: "WARNING",
      message: "1 statement line is neither matched, recorded nor excluded",
      amount: null,
      subjects: [{ kind: "BANK_STATEMENT", id: "stmt-1", name: "october-2026.csv", amount: null }],
    },
    linkName: "october-2026.csv",
    href: "/org/acme/trust-accounting/reconciliation",
  },
  {
    code: "FUND_LEDGER_IN_DEBIT",
    flag: {
      code: "FUND_LEDGER_IN_DEBIT",
      severity: "WARNING",
      message: "The Fund interest and charges ledger is R172,50 in debit.",
      amount: 172.5,
      subjects: [
        { kind: "CREDITOR_ACCOUNT", id: "ca-1", name: "Fund interest and charges", amount: 172.5 },
      ],
    },
    linkName: "Fund interest and charges",
    href: "/org/acme/trust-accounting/other-creditors",
  },
  {
    code: "CLIENT_LEDGER_IN_DEBIT",
    flag: {
      code: "CLIENT_LEDGER_IN_DEBIT",
      severity: "WARNING",
      message: "1 client ledger is in debit.",
      amount: 500,
      subjects: [{ kind: "CLIENT", id: "cust-1", name: "Sipho Dlamini", amount: 500 }],
    },
    linkName: "Sipho Dlamini",
    href: "/org/acme/trust-accounting/client-ledgers/cust-1",
  },
  {
    code: "UNIDENTIFIED_OVER_ONE_YEAR",
    flag: {
      code: "UNIDENTIFIED_OVER_ONE_YEAR",
      severity: "WARNING",
      message: "1 unidentified deposit has been held for more than a year.",
      amount: 300,
      subjects: [{ kind: "UNIDENTIFIED_DEPOSIT", id: "ud-2", name: "EFT UNKNOWN", amount: 300 }],
    },
    linkName: "EFT UNKNOWN",
    href: "/org/acme/trust-accounting/other-creditors",
  },
  {
    code: "CHARGES_TO_REFUND",
    flag: {
      code: "CHARGES_TO_REFUND",
      severity: "WARNING",
      message:
        "R57,50 of non-recoverable bank charges has been debited to trust and not yet refunded from the business account.",
      amount: 57.5,
      subjects: [],
    },
    linkName: "Other trust creditors",
    href: "/org/acme/trust-accounting/other-creditors",
  },
];

/** A report raising `flag` where the backend raises it: firm-level for EQ-3, else on the account. */
function reportWith(flag: IntegrityFlag): FirmIntegrityReport {
  const blocking = flag.severity === "BLOCKING";
  if (flag.code === "INTER_ACCOUNT_NOT_ZERO") {
    return firmReport({ balanced: false, flags: [flag] });
  }
  return firmReport({
    balanced: !blocking,
    accounts: [accountReport({ balanced: !blocking, flags: [flag] })],
  });
}

describe("TrustAccountingPage — trust integrity (649C)", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    chartProps.length = 0;
    tableProps.length = 0;
  });

  it('shows "Books balance" when the firm report is balanced', async () => {
    setupDefaultMocks();
    await renderPage();

    expect(mockFetchTrustIntegrity).toHaveBeenCalledTimes(1);
    const card = within(screen.getByTestId("trust-integrity-card"));
    expect(card.getByText("Trust integrity")).toBeInTheDocument();
    expect(
      card.getByText(`As at ${formatLocalDate("2026-11-13")}, all trust accounts`)
    ).toBeVisible();
    expect(card.getByTestId("integrity-books-balance")).toHaveTextContent("Books balance");
    expect(card.queryByTestId("integrity-out-of-balance")).not.toBeInTheDocument();
    expect(card.getByTestId("integrity-no-flags")).toHaveTextContent("No flags");

    // D-1: the frozen four-tile row is untouched.
    for (const label of [
      "Trust Balance",
      "Active Clients",
      "Pending Approvals",
      "Reconciliation",
    ]) {
      expect(summaryCards().getByText(label)).toBeInTheDocument();
    }
  });

  it("shows the cashbook, trust creditors and the difference when the books do not balance", async () => {
    setupDefaultMocks();
    mockFetchTrustIntegrity.mockResolvedValue({
      state: "ok",
      data: {
        ...reportWith(FLAG_CASES[1].flag),
        cashbook: 125050.5,
        creditorTotal: 125000.5,
        difference: 50,
      },
    });
    await renderPage();

    const card = within(screen.getByTestId("trust-integrity-card"));
    expect(card.queryByTestId("integrity-books-balance")).not.toBeInTheDocument();
    const out = within(card.getByTestId("integrity-out-of-balance"));
    expect(out.getByText("The books do not balance.")).toBeInTheDocument();
    expect(out.getByText("Cashbook").nextSibling).toHaveTextContent(money(125050.5));
    expect(out.getByText("Trust creditors").nextSibling).toHaveTextContent(money(125000.5));
    expect(out.getByTestId("integrity-difference")).toHaveTextContent(money(50));
  });

  it("points at the flags when a blocking flag is raised with no firm difference", async () => {
    setupDefaultMocks();
    mockFetchTrustIntegrity.mockResolvedValue({
      state: "ok",
      data: reportWith(FLAG_CASES[0].flag),
    });
    await renderPage();

    expect(screen.getByTestId("integrity-out-of-balance")).toHaveTextContent(
      "The books do not balance — see the flags below."
    );
    expect(screen.getByTestId("integrity-difference")).toHaveTextContent(money(0));
  });

  it.each(FLAG_CASES)(
    "shows the $code flag by its label, with its severity, sentence, amount, subjects and link",
    async ({ code, flag, linkName, href }) => {
      setupDefaultMocks();
      mockFetchTrustIntegrity.mockResolvedValue({ state: "ok", data: reportWith(flag) });
      await renderPage();

      const item = screen.getByTestId(`integrity-flag-${code}`);
      const inItem = within(item);
      // The label from the map — never the raw code.
      expect(inItem.getByText(INTEGRITY_FLAG_LABELS[code])).toBeInTheDocument();
      expect(item).not.toHaveTextContent(code);
      expect(screen.queryByText(new RegExp(`\\b${code}\\b`))).not.toBeInTheDocument();
      expect(item).not.toHaveTextContent(/BLOCKING|WARNING/);
      expect(inItem.getByText(flag.severity === "BLOCKING" ? "Blocking" : "Warning")).toBeVisible();
      expect(inItem.getByText(flag.message)).toBeInTheDocument();
      if (flag.amount !== null) {
        expect(item).toHaveTextContent(money(flag.amount));
      }
      for (const subject of flag.subjects) {
        expect(item).toHaveTextContent(subject.name ?? "");
        if (subject.amount !== null) {
          expect(item).toHaveTextContent(money(subject.amount));
        }
      }
      if (linkName && href) {
        expect(inItem.getByRole("link", { name: linkName })).toHaveAttribute("href", href);
      } else {
        expect(inItem.queryByRole("link")).not.toBeInTheDocument();
      }
      // A warning alone never makes the books unbalanced (ADR-377).
      if (flag.severity === "WARNING") {
        expect(screen.getByTestId("integrity-books-balance")).toHaveTextContent("Books balance");
      } else {
        expect(screen.getByTestId("integrity-out-of-balance")).toBeInTheDocument();
      }
    }
  );

  it("names the account of each flag when the firm has more than one trust account", async () => {
    setupDefaultMocks();
    mockFetchTrustAccounts.mockResolvedValue([
      { id: "acc-1", accountName: "Main Trust Account", isPrimary: true },
      { id: "acc-2", accountName: "Investment Account", isPrimary: false },
    ]);
    mockFetchTrustIntegrity.mockResolvedValue({
      state: "ok",
      data: firmReport({
        accounts: [
          accountReport(),
          accountReport({ trustAccountId: "acc-2", flags: [FLAG_CASES[6].flag] }),
        ],
      }),
    });
    await renderPage();

    expect(screen.getByTestId("integrity-flag-FUND_LEDGER_IN_DEBIT")).toHaveTextContent(
      `Investment Account: ${INTEGRITY_FLAG_LABELS.FUND_LEDGER_IN_DEBIT}`
    );
  });

  it("shows the firm-wide Cashbook / Client ledgers / Other trust creditors / Invested tiles from the integrity report", async () => {
    setupDefaultMocks();
    // The exit month's firm column (§16.9.6): 121 725 = 100 380 + 1 250 + 20 095.
    mockFetchTrustIntegrity.mockResolvedValue({
      state: "ok",
      data: firmReport({
        cashbook: 121725,
        clientLedgers: 100380,
        investmentCreditors: 20095,
        fundLedger: -100,
        unidentifiedDeposits: 1250,
        otherNonClient: 100,
        creditorTotal: 121725,
      }),
    });
    await renderPage();

    const tiles = within(screen.getByTestId("integrity-tiles"));
    for (const label of ["Cashbook", "Client ledgers", "Other trust creditors", "Invested"]) {
      expect(tiles.getByRole("group", { name: label })).toBeInTheDocument();
    }
    expect(tileValue("Cashbook")).toHaveTextContent(money(121725));
    expect(tileValue("Client ledgers")).toHaveTextContent(money(100380));
    // Other trust creditors = Fund ledger + unidentified deposits + other non-client.
    expect(tileValue("Other trust creditors")).toHaveTextContent(money(1250));
    expect(tileValue("Invested")).toHaveTextContent(money(20095));
    // The frozen row keeps its own source (trust-position), not the firm report.
    expect(tileValue("Trust Balance")).toHaveTextContent(money(125000.5));
  });

  it("renders a quiet error when the integrity report fails", async () => {
    setupDefaultMocks();
    mockFetchTrustIntegrity.mockResolvedValue({ state: "error" });
    await renderPage();

    expect(screen.getByTestId("integrity-error")).toHaveTextContent(
      "Trust integrity could not be loaded."
    );
    expect(screen.queryByTestId("integrity-books-balance")).not.toBeInTheDocument();
    expect(tileValue("Cashbook")).toHaveTextContent("—");
    // The rest of the overview still renders.
    expect(screen.getByTestId("summary-cards")).toBeInTheDocument();
  });

  it("renders the card and the firm-wide tiles when the dashboard read fails but integrity loads", async () => {
    setupDefaultMocks();
    mockFetchDashboardData.mockRejectedValue(new Error("boom"));
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
    await renderPage();

    expect(
      screen.getByText("Unable to load trust account data. Please try again later.")
    ).toBeInTheDocument();
    expect(screen.queryByTestId("summary-cards")).not.toBeInTheDocument();
    const card = within(screen.getByTestId("trust-integrity-card"));
    expect(card.getByTestId("integrity-books-balance")).toHaveTextContent("Books balance");
    expect(screen.getByTestId("integrity-tiles")).toBeInTheDocument();
    consoleError.mockRestore();
  });

  it("renders neither the card nor the tiles when the integrity report is absent", async () => {
    setupDefaultMocks();
    mockFetchTrustIntegrity.mockResolvedValue({ state: "absent" });
    await renderPage();

    expect(screen.queryByTestId("trust-integrity-card")).not.toBeInTheDocument();
    expect(screen.queryByTestId("integrity-tiles")).not.toBeInTheDocument();
    expect(screen.getByTestId("summary-cards")).toBeInTheDocument();
  });

  it("INTEGRITY_FLAG_LABELS has a label for every code (no raw enum on screen)", () => {
    const codes = FLAG_CASES.map((c) => c.code).sort();
    expect(Object.keys(INTEGRITY_FLAG_LABELS).sort()).toEqual(codes);
    for (const code of codes) {
      expect(INTEGRITY_FLAG_LABELS[code]).toMatch(/^[A-Z][a-z]/);
      expect(INTEGRITY_FLAG_LABELS[code]).not.toContain("_");
    }
  });
});
