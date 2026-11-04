import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";

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

// ── Imports after mocks ──────────────────────────────────────────
import ClientDetailPage from "@/app/(app)/org/[slug]/trust-accounting/client-ledgers/[clientId]/page";
import { formatCurrency } from "@/lib/format";

// G88-F23 (Phase 89 630C, ADR-361): the client-ledger card renders the server-computed
// `runningBalance` on every page — no client-side backward walk — with "—" for null
// (non-posted / REVERSAL rows), and REFUND is an outflow.

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
  mockFetchClientLedger.mockResolvedValue({
    id: "ledger-1",
    trustAccountId: "acc-1",
    clientId: "cust-1",
    clientName: "Moroka Holdings",
    balance: 7850,
    totalDeposits: 10000,
    totalPayments: 1000,
    totalFeeTransfers: 1150,
    totalInterestCredited: 0,
    lastTransactionDate: "2026-11-02",
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-11-02T00:00:00Z",
  });
}

function makeTx(overrides: Record<string, unknown> = {}) {
  return {
    id: "tx-1",
    trustAccountId: "acc-1",
    transactionType: "DEPOSIT",
    amount: 10000,
    clientId: "cust-1",
    matterId: null,
    counterpartyClientId: null,
    invoiceId: null,
    reference: "DEP-001",
    description: null,
    transactionDate: "2026-10-01",
    status: "RECORDED",
    approvedBy: null,
    approvedAt: null,
    secondApprovedBy: null,
    secondApprovedAt: null,
    rejectedBy: null,
    rejectedAt: null,
    rejectionReason: null,
    reversalOf: null,
    reversedById: null,
    bankStatementLineId: null,
    recordedBy: "member-1",
    createdAt: "2026-10-01T09:00:00Z",
    runningBalance: 10000,
    ...overrides,
  };
}

async function renderDetailPage(searchParams: Record<string, string> = {}) {
  const result = await ClientDetailPage({
    params: Promise.resolve({ slug: "acme", clientId: "cust-1" }),
    searchParams: Promise.resolve(searchParams),
  });
  render(result);
}

function balanceCell(txId: string): HTMLElement {
  const cells = within(screen.getByTestId(`history-row-${txId}`)).getAllByRole("cell");
  return cells[cells.length - 1];
}

function amountSpan(txId: string): HTMLElement {
  const cells = within(screen.getByTestId(`history-row-${txId}`)).getAllByRole("cell");
  return cells[3].querySelector("span") as HTMLElement;
}

describe("Client ledger card — server running balance (630C)", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders server balances on page 2 (not '—')", async () => {
    setupDefaultMocks();
    mockFetchClientHistory.mockResolvedValue({
      content: [
        makeTx({ id: "tx-ref", transactionType: "REFUND", amount: 1000, runningBalance: 9000 }),
        makeTx({ id: "tx-dep", transactionType: "DEPOSIT", amount: 10000, runningBalance: 10000 }),
      ],
      totalElements: 22,
      totalPages: 2,
      pageSize: 20,
      pageNumber: 1,
    });

    await renderDetailPage({ page: "1" });

    expect(balanceCell("tx-ref").textContent).toBe(formatCurrency(9000, "ZAR"));
    expect(balanceCell("tx-dep").textContent).toBe(formatCurrency(10000, "ZAR"));
  });

  it("renders the server value as-is on page 0 and '—' for a null (non-posted) row", async () => {
    setupDefaultMocks();
    mockFetchClientHistory.mockResolvedValue({
      content: [
        makeTx({
          id: "tx-rej",
          transactionType: "PAYMENT",
          amount: 500,
          status: "REJECTED",
          reference: "PAY-REJ",
          runningBalance: null,
        }),
        makeTx({
          id: "tx-ft",
          transactionType: "FEE_TRANSFER",
          amount: 1150,
          status: "APPROVED",
          reference: "FT-001",
          runningBalance: 7850,
        }),
        makeTx({
          id: "tx-ref",
          transactionType: "REFUND",
          amount: 1000,
          status: "APPROVED",
          reference: "REF-001",
          runningBalance: 9000,
        }),
        makeTx({ id: "tx-dep", reference: "DEP-001", runningBalance: 10000 }),
      ],
      totalElements: 4,
      totalPages: 1,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderDetailPage();

    expect(balanceCell("tx-rej").textContent).toBe("—");
    expect(balanceCell("tx-ft").textContent).toBe(formatCurrency(7850, "ZAR"));
    expect(balanceCell("tx-ref").textContent).toBe(formatCurrency(9000, "ZAR"));
    expect(balanceCell("tx-dep").textContent).toBe(formatCurrency(10000, "ZAR"));
  });

  it("renders REFUND as an outflow (no green, up-right arrow)", async () => {
    setupDefaultMocks();
    mockFetchClientHistory.mockResolvedValue({
      content: [
        makeTx({
          id: "tx-ref",
          transactionType: "REFUND",
          amount: 1000,
          status: "APPROVED",
          runningBalance: 9000,
        }),
        makeTx({ id: "tx-dep", runningBalance: 10000 }),
      ],
      totalElements: 2,
      totalPages: 1,
      pageSize: 20,
      pageNumber: 0,
    });

    await renderDetailPage();

    const refund = amountSpan("tx-ref");
    expect(refund.className).not.toContain("text-green-600");
    expect(refund.querySelector(".lucide-arrow-up-right")).not.toBeNull();
    expect(refund.querySelector(".lucide-arrow-down-left")).toBeNull();

    const deposit = amountSpan("tx-dep");
    expect(deposit.className).toContain("text-green-600");
    expect(deposit.querySelector(".lucide-arrow-down-left")).not.toBeNull();
  });
});
